package com.svce.oejava;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

import com.itextpdf.text.*;
import java.io.ByteArrayOutputStream;
import java.util.*;

import com.itextpdf.text.Font;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

@Service
public class AdminService {

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private courseRepository courseRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    // Admin login validation
    public Optional<Admin> validateAdmin(String email, String password) {
        Admin admin = adminRepository.findByEmail(email);
        if (admin != null && admin.getPassword().equals(password)) {
            return Optional.of(admin);
        }
        return Optional.empty();
    }

    // -------- Helpers for restriction logic --------
    private Set<String> parseDeptList(String value) {
        if (value == null || value.trim().isEmpty()) {
            return new HashSet<>();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private Set<String> computeBlockedDepartmentsForStudent(String studentBranch, List<Course> sessionCourses) {
        Set<String> blocked = new HashSet<>();
        if (studentBranch == null || studentBranch.trim().isEmpty()) {
            return blocked;
        }
        String sb = studentBranch.trim().toUpperCase();

        for (Course c : sessionCourses) {
            Set<String> restrictedList = parseDeptList(c.getRestricted());
            Set<String> upper = restrictedList.stream()
                    .map(String::toUpperCase)
                    .collect(Collectors.toSet());
            if (upper.contains(sb)) {
                if (c.getBranch() != null) {
                    blocked.add(c.getBranch().trim().toUpperCase());
                }
                blocked.addAll(upper);
            }
        }
        return blocked;
    }

    private String getCellValue(Cell cell) {
        if (cell == null)
            return "";
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                // assuming no decimals in your Excel
                return String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return "";
        }
    }

    public List<courseSelection> getCoursesForAdminBySession(String session) {
        String effectiveSession = session;   // will be "25-26OS" or "25-26ES"

        List<Course> dbCourses = courseRepository.findBySession(effectiveSession);
        List<courseSelection> result = new ArrayList<>();
        int idx = 1;

        for (Course c : dbCourses) {
            boolean full = c.getEnrolled() >= c.getCapacity();

            courseSelection dto = new courseSelection(
                    idx++,
                    c.getCourseTitle(),
                    c.getCourseCode(),
                    c.getFaculty(),
                    c.getBranch(),
                    c.getCapacity(),
                    c.getEnrolled(),
                    c.getRestricted(),
                    c.getSession(),
                    full,
                    false);
            dto.setSession(c.getSession());
            result.add(dto);
        }

        return result;
    }

    // ADD new course for session
    public String addCourseForSession(courseSelection dto, String session) {
        String effectiveSession = session;   // will be "25-26OS" or "25-26ES"

        if (courseRepository.existsByCourseCodeAndSession(dto.getCode(), effectiveSession)) {
            return "A course with this code already exists for this session.";
        }

        Course c = new Course();
        c.setCourseTitle(dto.getTitle());
        c.setCourseCode(dto.getCode());
        c.setFaculty(dto.getInstructor());
        c.setBranch(dto.getDepartment());
        c.setCapacity(dto.getCapacity());
        c.setEnrolled(0);
        c.setRestricted(dto.getRestricted());
        c.setSession(effectiveSession);

        courseRepository.save(c);
        return "Course added successfully.";
    }

    // UPDATE course (by courseCode + session)
    public String updateCourseForSession(String courseCode, courseSelection dto, String session) {
        String effectiveSession = session;   // will be "25-26OS" or "25-26ES"

        Course c = courseRepository.findByCourseCodeAndSession(courseCode, effectiveSession);
        if (c == null) {
            return "Course not found for this session.";
        }

        c.setCourseTitle(dto.getTitle());
        c.setFaculty(dto.getInstructor());
        c.setCapacity(dto.getCapacity());

        courseRepository.save(c);
        return "Course updated successfully.";
    }

    // DELETE course (by courseCode + session)
    public String deleteCourseForSession(String courseCode, String session) {
        String effectiveSession = session;   // will be "25-26OS" or "25-26ES"

        Course c = courseRepository.findByCourseCodeAndSession(courseCode, effectiveSession);
        if (c == null) {
            return "Course not found for this session.";
        }

        courseRepository.delete(c);
        return "Course deleted successfully.";
    }

    public List<Map<String, String>> getEnrollmentsForSession(String session) {
        String effectiveSession = session;   // will be "25-26OS" or "25-26ES"

        List<Enrollment> enrollments = enrollmentRepository.findByIdSession(effectiveSession);
        List<Map<String, String>> result = new ArrayList<>();

        for (Enrollment e : enrollments) {
            Map<String, String> map = new LinkedHashMap<>();
            // Fetch student to get register number
            Student student = studentRepository.findByEmail(e.getEmail());
            map.put("Register No",
                    student != null ? student.getRegistrationNo() != null ? student.getRegistrationNo().toString() : ""
                            : "");
            map.put("Name", e.getName());
            map.put("Email", e.getEmail());
            map.put("Department", e.getBranch());
            map.put("Course Code", e.getCourseCode());
            map.put("Course Title", e.getCourseTitle());
            result.add(map);
        }

        return result;
    }

    public String deleteEnrollmentForSession(String email, String courseCode, String session) {
        String effectiveSession = session;   // will be "25-26OS" or "25-26ES"

        Enrollment existing = enrollmentRepository
                .findByEmailAndCourseCodeAndIdSession(email, courseCode, effectiveSession);

        if (existing == null) {
            return "Enrollment not found for this email, course, and session.";
        }

        // Decrement enrolled count for the course
        Course course = courseRepository.findById(existing.getCourseCode()).orElse(null);
        if (course != null && effectiveSession.equalsIgnoreCase(course.getSession())) {
            int current = course.getEnrolled();
            course.setEnrolled(Math.max(0, current - 1));
            courseRepository.save(course);
        }

        // Delete the enrollment row
        enrollmentRepository.delete(existing);

        return "Enrollment deleted successfully.";
    }

    public String adminManualEnrollment(String email, String courseCode, String session) {
        String effectiveSession = session;   // will be "25-26OS" or "25-26ES"

        // 1) Check student exists (by email)
        Student student = studentRepository.findByEmail(email);
        if (student == null) {
            return "Student not found.";
        }

        Long registrationNo = student.getRegistrationNo();

        // 2) Check course exists for this session
        Course course = courseRepository.findByCourseCodeAndSession(courseCode, effectiveSession);
        if (course == null) {
            return "Course code invalid for this session.";
        }

        // NEW: blocked departments rule (same as student side)
        List<Course> sessionCourses = courseRepository.findBySession(effectiveSession);
        Set<String> blockedDepartments = computeBlockedDepartmentsForStudent(student.getBranch(), sessionCourses);

        String courseDept = course.getBranch() == null ? "" : course.getBranch().trim().toUpperCase();
        if (blockedDepartments.contains(courseDept)) {
            return "Student is not allowed to enroll in courses offered by "
                    + course.getBranch() + " department for this session.";
        }

        // 3) Capacity check
        if (course.getEnrolled() >= course.getCapacity()) {
            return "Course is full.";
        }

        // 5) Check if already enrolled in this session
        List<Enrollment> existingForSession = enrollmentRepository.findByEmailAndIdSession(email, effectiveSession);
        if (!existingForSession.isEmpty()) {
            for (Enrollment e : existingForSession) {
                if (e.getCourseCode().equalsIgnoreCase(courseCode)) {
                    return "Student already enrolled in this course for this session.";
                }
            }
            return "Student already enrolled in another course for this session.";
        }

        // 6) Create enrollment with composite key (registration_no + session)
        EnrollmentId id = new EnrollmentId();
        id.setRegistrationNo(registrationNo);
        id.setSession(effectiveSession);

        Enrollment enrollment = new Enrollment();
        enrollment.setId(id);
        enrollment.setName(student.getName());
        enrollment.setBranch(student.getBranch());
        enrollment.setEmail(student.getEmail());
        enrollment.setCourseCode(course.getCourseCode());
        enrollment.setCourseTitle(course.getCourseTitle());
        enrollment.setTs(java.time.LocalDateTime.now());
        enrollment.setChangedCourse("no");

        enrollmentRepository.save(enrollment);

        // 7) Increment course enrolled
        course.setEnrolled(course.getEnrolled() + 1);
        courseRepository.save(course);

        return "Enrollment added successfully.";
    }

    public static class EnrollmentReportRow {
        private String registerNo;
        private String name;
        private String email;
        private String studentDept;
        private String courseCode;
        private String courseTitle;
        private String courseDept;
        private String session;
        private String changed; // "yes"/"no"

        // constructor
        public EnrollmentReportRow(String registerNo, String name, String email,
                String studentDept, String courseCode, String courseTitle,
                String courseDept, String session, String changed) {
            this.registerNo = registerNo;
            this.name = name;
            this.email = email;
            this.studentDept = studentDept;
            this.courseCode = courseCode;
            this.courseTitle = courseTitle;
            this.courseDept = courseDept;
            this.session = session;
            this.changed = changed;
        }

        // getters (add setters if you like)
        public String getRegisterNo() {
            return registerNo;
        }

        public String getName() {
            return name;
        }

        public String getEmail() {
            return email;
        }

        public String getStudentDept() {
            return studentDept;
        }

        public String getCourseCode() {
            return courseCode;
        }

        public String getCourseTitle() {
            return courseTitle;
        }

        public String getCourseDept() {
            return courseDept;
        }

        public String getSession() {
            return session;
        }

        public String getChanged() {
            return changed;
        }
    }

    public List<EnrollmentReportRow> getAllEnrollmentRowsForSession(String session) {
        String effectiveSession = session;   // will be "25-26OS" or "25-26ES"

        List<Enrollment> enrollments = enrollmentRepository.findByIdSession(effectiveSession);

        List<EnrollmentReportRow> rows = new ArrayList<>();

        for (Enrollment e : enrollments) {
            // we already have name/branch/email/registerNo in enrollment + students
            String regNoStr;
            if (e.getId() != null && e.getId().getRegistrationNo() != null) {
                regNoStr = String.valueOf(e.getId().getRegistrationNo());
            } else {
                // fallback: lookup student by email to get registrationNo
                Student s = studentRepository.findByEmail(e.getEmail());
                regNoStr = (s != null && s.getRegistrationNo() != null)
                        ? String.valueOf(s.getRegistrationNo())
                        : "";
            }

            String studentDept = e.getBranch();
            String courseDept = "";
            Course c = courseRepository.findByCourseCodeAndSession(e.getCourseCode(), effectiveSession);
            if (c != null) {
                courseDept = c.getBranch();
            }

            rows.add(new EnrollmentReportRow(
                    regNoStr,
                    e.getName(),
                    e.getEmail(),
                    studentDept,
                    e.getCourseCode(),
                    e.getCourseTitle(),
                    courseDept,
                    effectiveSession,
                    e.getChangedCourse()));
        }
        return rows;
    }

    private void addHeaderCell(PdfPTable table, String text) {
        Font headerFont = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD);
        PdfPCell cell = new PdfPCell(new Phrase(text, headerFont));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setBackgroundColor(BaseColor.LIGHT_GRAY);
        table.addCell(cell);
    }

    private void addCell(PdfPTable table, String text) {
        Font cellFont = new Font(Font.FontFamily.HELVETICA, 9);
        PdfPCell cell = new PdfPCell(new Phrase(text == null ? "" : text, cellFont));
        table.addCell(cell);
    }

    public byte[] generateAllEnrollmentsPdf(String session, String groupBy) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            List<EnrollmentReportRow> rows = getAllEnrollmentRowsForSession(session);
            String effectiveSession = session;   // will be "25-26OS" or "25-26ES"
            String gb = (groupBy == null || groupBy.isBlank()) ? "dept" : groupBy.toLowerCase();

            Document document = new Document(PageSize.A4.rotate()); // landscape
            PdfWriter.getInstance(document, baos);
            document.open();

            // Title
            String title = "All Enrollments - Grouped by "
                    + ("course".equals(gb) ? "Course" : "Department")
                    + " - Session " + effectiveSession;
            Font titleFont = new Font(Font.FontFamily.HELVETICA, 14, Font.BOLD);
            Paragraph titlePara = new Paragraph(title, titleFont);
            titlePara.setAlignment(Element.ALIGN_CENTER);
            titlePara.setSpacingAfter(10f);
            document.add(titlePara);

            if ("course".equals(gb)) {
                // group by courseCode
                Map<String, List<EnrollmentReportRow>> byCourse = rows.stream()
                        .collect(Collectors.groupingBy(EnrollmentReportRow::getCourseCode));

                List<String> sortedCodes = new ArrayList<>(byCourse.keySet());
                Collections.sort(sortedCodes);

                for (String code : sortedCodes) {
                    List<EnrollmentReportRow> courseRows = byCourse.get(code);
                    if (courseRows.isEmpty())
                        continue;

                    EnrollmentReportRow first = courseRows.get(0);
                    String heading = "Course: " + code + " - " + first.getCourseTitle()
                            + " (Dept: " + first.getCourseDept() + ")";
                    Paragraph h = new Paragraph(heading, new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD));
                    h.setSpacingBefore(8f);
                    h.setSpacingAfter(4f);
                    document.add(h);

                    PdfPTable table = new PdfPTable(6);
                    table.setWidthPercentage(100);
                    table.setWidths(new float[] { 1.0f, 2.0f, 3.0f, 4.0f, 2.0f, 2.0f });

                    addHeaderCell(table, "S.No");
                    addHeaderCell(table, "Register No");
                    addHeaderCell(table, "Name");
                    addHeaderCell(table, "Email");
                    addHeaderCell(table, "Student Dept");
                    addHeaderCell(table, "Session");

                    int i = 1;
                    for (EnrollmentReportRow r : courseRows) {
                        addCell(table, String.valueOf(i++));
                        addCell(table, r.getRegisterNo());
                        addCell(table, r.getName());
                        addCell(table, r.getEmail());
                        addCell(table, r.getStudentDept());
                        addCell(table, r.getSession());
                    }

                    document.add(table);
                }

            } else {
                // group by studentDept
                Map<String, List<EnrollmentReportRow>> byDept = rows.stream()
                        .collect(Collectors.groupingBy(EnrollmentReportRow::getStudentDept));

                List<String> sortedDepts = new ArrayList<>(byDept.keySet());
                Collections.sort(sortedDepts, Comparator.nullsLast(String::compareTo));

                for (String dept : sortedDepts) {
                    List<EnrollmentReportRow> deptRows = byDept.get(dept);
                    if (deptRows.isEmpty())
                        continue;

                    String heading = "Department: " + (dept == null ? "" : dept);
                    Paragraph h = new Paragraph(heading, new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD));
                    h.setSpacingBefore(8f);
                    h.setSpacingAfter(4f);
                    document.add(h);

                    PdfPTable table = new PdfPTable(8);
                    table.setWidthPercentage(100);
                    table.setWidths(new float[] { 1.0f, 2.0f, 3.0f, 4.0f, 2.0f, 2.0f, 3.0f, 2.0f });

                    addHeaderCell(table, "S.No");
                    addHeaderCell(table, "Register No");
                    addHeaderCell(table, "Name");
                    addHeaderCell(table, "Email");
                    addHeaderCell(table, "Student Dept");
                    addHeaderCell(table, "Course Code");
                    addHeaderCell(table, "Course Title");
                    addHeaderCell(table, "Course Dept");

                    int i = 1;
                    for (EnrollmentReportRow r : deptRows) {
                        addCell(table, String.valueOf(i++));
                        addCell(table, r.getRegisterNo());
                        addCell(table, r.getName());
                        addCell(table, r.getEmail());
                        addCell(table, r.getStudentDept());
                        addCell(table, r.getCourseCode());
                        addCell(table, r.getCourseTitle());
                        addCell(table, r.getCourseDept());
                    }

                    document.add(table);
                }
            }

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return new byte[0];
        }
    }

    public byte[] generateAllEnrollmentsExcel(String session, String groupBy) {
        String effectiveSession = session;   // will be "25-26OS" or "25-26ES"
        List<EnrollmentReportRow> rows = getAllEnrollmentRowsForSession(effectiveSession);

        rows.sort(Comparator
                .comparing(EnrollmentReportRow::getStudentDept, Comparator.nullsLast(String::compareTo))
                .thenComparing(EnrollmentReportRow::getRegisterNo, Comparator.nullsLast(String::compareTo)));

        try (Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream bos = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("AllEnrollments");

            // Header style
            CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            int rowIdx = 0;
            Row header = sheet.createRow(rowIdx++);

            String[] cols = {
                    "Register No", "Name", "Email", "Student Dept",
                    "Course Code", "Course Title", "Course Dept",
                    "Session", "Changed?"
            };

            for (int i = 0; i < cols.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(cols[i]);
                cell.setCellStyle(headerStyle);
            }

            // Data rows
            for (EnrollmentReportRow r : rows) {
                Row row = sheet.createRow(rowIdx++);
                int c = 0;
                row.createCell(c++).setCellValue(nullOrEmpty(r.getRegisterNo()));
                row.createCell(c++).setCellValue(nullOrEmpty(r.getName()));
                row.createCell(c++).setCellValue(nullOrEmpty(r.getEmail()));
                row.createCell(c++).setCellValue(nullOrEmpty(r.getStudentDept()));
                row.createCell(c++).setCellValue(nullOrEmpty(r.getCourseCode()));
                row.createCell(c++).setCellValue(nullOrEmpty(r.getCourseTitle()));
                row.createCell(c++).setCellValue(nullOrEmpty(r.getCourseDept()));
                row.createCell(c++).setCellValue(nullOrEmpty(r.getSession()));
                row.createCell(c++).setCellValue(nullOrEmpty(r.getChanged()));
            }

            for (int i = 0; i < cols.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(bos);
            return bos.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return new byte[0];
        }
    }

    private String nullOrEmpty(String s) {
        return s == null ? "" : s;
    }

    public List<EnrollmentReportRow> getEnrolledByDeptRows(String session, String deptFilter) {
        String effectiveSession = session;   // will be "25-26OS" or "25-26ES"

        List<Enrollment> enrollments = enrollmentRepository.findByIdSession(effectiveSession);
        List<EnrollmentReportRow> rows = new ArrayList<>();

        for (Enrollment e : enrollments) {
            String studentDept = e.getBranch();
            if (deptFilter != null && !deptFilter.isBlank()) {
                if (studentDept == null ||
                        !studentDept.equalsIgnoreCase(deptFilter.trim())) {
                    continue;
                }
            }

            String regNoStr = "";
            if (e.getId() != null && e.getId().getRegistrationNo() != null) {
                regNoStr = String.valueOf(e.getId().getRegistrationNo());
            } else {
                Student s = studentRepository.findByEmail(e.getEmail());
                if (s != null && s.getRegistrationNo() != null) {
                    regNoStr = String.valueOf(s.getRegistrationNo());
                }
            }

            String courseDept = "";
            Course c = courseRepository.findByCourseCodeAndSession(e.getCourseCode(), effectiveSession);
            if (c != null) {
                courseDept = c.getBranch();
            }

            rows.add(new EnrollmentReportRow(
                    regNoStr,
                    e.getName(),
                    e.getEmail(),
                    studentDept,
                    e.getCourseCode(),
                    e.getCourseTitle(),
                    courseDept,
                    effectiveSession,
                    e.getChangedCourse()));
        }

        // sort nicely
        rows.sort(Comparator
                .comparing(EnrollmentReportRow::getStudentDept, Comparator.nullsLast(String::compareTo))
                .thenComparing(EnrollmentReportRow::getRegisterNo, Comparator.nullsLast(String::compareTo)));

        return rows;
    }

    public byte[] generateEnrolledByDeptPdf(String session, String deptFilter) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            String effectiveSession = session;   // will be "25-26OS" or "25-26ES"
            List<EnrollmentReportRow> rows = getEnrolledByDeptRows(effectiveSession, deptFilter);

            Document document = new Document(PageSize.A4.rotate());
            PdfWriter.getInstance(document, baos);
            document.open();

            String title = "Enrolled Students - "
                    + ((deptFilter == null || deptFilter.isBlank())
                            ? "All Departments"
                            : "Department " + deptFilter.trim())
                    + " - Session " + effectiveSession;

            Font titleFont = new Font(Font.FontFamily.HELVETICA, 14, Font.BOLD);
            Paragraph titlePara = new Paragraph(title, titleFont);
            titlePara.setAlignment(Element.ALIGN_CENTER);
            titlePara.setSpacingAfter(10f);
            document.add(titlePara);

            if (deptFilter != null && !deptFilter.isBlank()) {
                // single dept table
                addEnrolledDeptSection(document, deptFilter.trim(), rows);
            } else {
                // all depts, group by studentDept
                Map<String, List<EnrollmentReportRow>> byDept = rows.stream()
                        .collect(Collectors.groupingBy(EnrollmentReportRow::getStudentDept));

                List<String> sortedDepts = new ArrayList<>(byDept.keySet());
                sortedDepts.sort(Comparator.nullsLast(String::compareTo));

                for (String dept : sortedDepts) {
                    addEnrolledDeptSection(document, dept, byDept.get(dept));
                }
            }

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return new byte[0];
        }
    }

    private void addEnrolledDeptSection(Document document, String dept, List<EnrollmentReportRow> deptRows)
            throws DocumentException {
        if (deptRows == null || deptRows.isEmpty()) {
            return;
        }

        Paragraph h = new Paragraph(
                "Department: " + (dept == null ? "" : dept),
                new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD));
        h.setSpacingBefore(8f);
        h.setSpacingAfter(4f);
        document.add(h);

        PdfPTable table = new PdfPTable(8);
        table.setWidthPercentage(100);
        table.setWidths(new float[] { 1f, 2f, 3f, 4f, 2f, 2f, 3f, 2f });

        addHeaderCell(table, "S.No");
        addHeaderCell(table, "Register No");
        addHeaderCell(table, "Name");
        addHeaderCell(table, "Email");
        addHeaderCell(table, "Student Dept");
        addHeaderCell(table, "Course Code");
        addHeaderCell(table, "Course Title");
        addHeaderCell(table, "Course Dept");

        int i = 1;
        for (EnrollmentReportRow r : deptRows) {
            addCell(table, String.valueOf(i++));
            addCell(table, r.getRegisterNo());
            addCell(table, r.getName());
            addCell(table, r.getEmail());
            addCell(table, r.getStudentDept());
            addCell(table, r.getCourseCode());
            addCell(table, r.getCourseTitle());
            addCell(table, r.getCourseDept());
        }

        document.add(table);
    }

    public byte[] generateEnrolledByDeptExcel(String session, String deptFilter) {
        String effectiveSession = session;   // will be "25-26OS" or "25-26ES"
        List<EnrollmentReportRow> rows = getEnrolledByDeptRows(effectiveSession, deptFilter);

        try (Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream bos = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet(
                    (deptFilter == null || deptFilter.isBlank())
                            ? "Enrolled_All"
                            : "Enrolled_" + deptFilter.trim());

            // header style
            CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            int rowIdx = 0;
            Row header = sheet.createRow(rowIdx++);

            String[] cols = {
                    "Register No", "Name", "Email", "Student Dept",
                    "Course Code", "Course Title", "Course Dept",
                    "Session", "Changed?"
            };

            for (int i = 0; i < cols.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(cols[i]);
                cell.setCellStyle(headerStyle);
            }

            for (EnrollmentReportRow r : rows) {
                Row row = sheet.createRow(rowIdx++);
                int c = 0;
                row.createCell(c++).setCellValue(nullOrEmpty(r.getRegisterNo()));
                row.createCell(c++).setCellValue(nullOrEmpty(r.getName()));
                row.createCell(c++).setCellValue(nullOrEmpty(r.getEmail()));
                row.createCell(c++).setCellValue(nullOrEmpty(r.getStudentDept()));
                row.createCell(c++).setCellValue(nullOrEmpty(r.getCourseCode()));
                row.createCell(c++).setCellValue(nullOrEmpty(r.getCourseTitle()));
                row.createCell(c++).setCellValue(nullOrEmpty(r.getCourseDept()));
                row.createCell(c++).setCellValue(nullOrEmpty(r.getSession()));
                row.createCell(c++).setCellValue(nullOrEmpty(r.getChanged()));
            }

            for (int i = 0; i < cols.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(bos);
            return bos.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return new byte[0];
        }
    }

    public static class NotEnrolledRow {
        private String registerNo;
        private String name;
        private String email;
        private String studentDept;
        private String session;

        public NotEnrolledRow(String registerNo, String name, String email,
                String studentDept, String session) {
            this.registerNo = registerNo;
            this.name = name;
            this.email = email;
            this.studentDept = studentDept;
            this.session = session;
        }

        public String getRegisterNo() {
            return registerNo;
        }

        public String getName() {
            return name;
        }

        public String getEmail() {
            return email;
        }

        public String getStudentDept() {
            return studentDept;
        }

        public String getSession() {
            return session;
        }
    }

    public List<NotEnrolledRow> getNotEnrolledByDeptRows(String session, String deptFilter) {
        String effectiveSession = session;   // will be "25-26OS" or "25-26ES"

        // all enrollments for this session
        List<Enrollment> enrollments = enrollmentRepository.findByIdSession(effectiveSession);

        // collect registrationNos that are enrolled
        Set<Long> enrolledRegNos = new HashSet<>();
        for (Enrollment e : enrollments) {
            if (e.getId() != null && e.getId().getRegistrationNo() != null) {
                enrolledRegNos.add(e.getId().getRegistrationNo());
            } else {
                Student s = studentRepository.findByEmail(e.getEmail());
                if (s != null && s.getRegistrationNo() != null) {
                    enrolledRegNos.add(s.getRegistrationNo());
                }
            }
        }

        // all students
        List<Student> allStudents = studentRepository.findAll();
        List<NotEnrolledRow> rows = new ArrayList<>();

        for (Student s : allStudents) {
            Long regNo = s.getRegistrationNo();
            if (regNo == null)
                continue;

            // filter by dept if specified
            String studentDept = s.getBranch();
            if (deptFilter != null && !deptFilter.isBlank()) {
                if (studentDept == null ||
                        !studentDept.equalsIgnoreCase(deptFilter.trim())) {
                    continue;
                }
            }

            // skip if enrolled in this session
            if (enrolledRegNos.contains(regNo)) {
                continue;
            }

            rows.add(new NotEnrolledRow(
                    String.valueOf(regNo),
                    s.getName(),
                    s.getEmail(),
                    studentDept,
                    effectiveSession));
        }

        rows.sort(Comparator
                .comparing(NotEnrolledRow::getStudentDept, Comparator.nullsLast(String::compareTo))
                .thenComparing(NotEnrolledRow::getRegisterNo, Comparator.nullsLast(String::compareTo)));

        return rows;
    }

    public byte[] generateNotEnrolledByDeptPdf(String session, String deptFilter) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            String effectiveSession = session;   // will be "25-26OS" or "25-26ES"
            List<NotEnrolledRow> rows = getNotEnrolledByDeptRows(effectiveSession, deptFilter);

            Document document = new Document(PageSize.A4.rotate());
            PdfWriter.getInstance(document, baos);
            document.open();

            String title = "Not-Enrolled Students - "
                    + ((deptFilter == null || deptFilter.isBlank())
                            ? "All Departments"
                            : "Department " + deptFilter.trim())
                    + " - Session " + effectiveSession;

            Font titleFont = new Font(Font.FontFamily.HELVETICA, 14, Font.BOLD);
            Paragraph titlePara = new Paragraph(title, titleFont);
            titlePara.setAlignment(Element.ALIGN_CENTER);
            titlePara.setSpacingAfter(10f);
            document.add(titlePara);

            if (deptFilter != null && !deptFilter.isBlank()) {
                addNotEnrolledDeptSection(document, deptFilter.trim(), rows);
            } else {
                Map<String, List<NotEnrolledRow>> byDept = rows.stream()
                        .collect(Collectors.groupingBy(NotEnrolledRow::getStudentDept));

                List<String> sortedDepts = new ArrayList<>(byDept.keySet());
                sortedDepts.sort(Comparator.nullsLast(String::compareTo));

                for (String dept : sortedDepts) {
                    addNotEnrolledDeptSection(document, dept, byDept.get(dept));
                }
            }

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return new byte[0];
        }
    }

    private void addNotEnrolledDeptSection(Document document, String dept, List<NotEnrolledRow> deptRows)
            throws DocumentException {
        if (deptRows == null || deptRows.isEmpty()) {
            return;
        }

        Paragraph h = new Paragraph(
                "Department: " + (dept == null ? "" : dept),
                new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD));
        h.setSpacingBefore(8f);
        h.setSpacingAfter(4f);
        document.add(h);

        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[] { 1f, 2f, 3f, 4f, 2f });

        addHeaderCell(table, "S.No");
        addHeaderCell(table, "Register No");
        addHeaderCell(table, "Name");
        addHeaderCell(table, "Email");
        addHeaderCell(table, "Student Dept");

        int i = 1;
        for (NotEnrolledRow r : deptRows) {
            addCell(table, String.valueOf(i++));
            addCell(table, r.getRegisterNo());
            addCell(table, r.getName());
            addCell(table, r.getEmail());
            addCell(table, r.getStudentDept());
        }

        document.add(table);
    }

    public byte[] generateNotEnrolledByDeptExcel(String session, String deptFilter) {
        String effectiveSession = session;   // will be "25-26OS" or "25-26ES"
        List<NotEnrolledRow> rows = getNotEnrolledByDeptRows(effectiveSession, deptFilter);

        try (Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream bos = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet(
                    (deptFilter == null || deptFilter.isBlank())
                            ? "NotEnrolled_All"
                            : "NotEnrolled_" + deptFilter.trim());

            CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            int rowIdx = 0;
            Row header = sheet.createRow(rowIdx++);

            String[] cols = {
                    "Register No", "Name", "Email", "Student Dept", "Session"
            };

            for (int i = 0; i < cols.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(cols[i]);
                cell.setCellStyle(headerStyle);
            }

            for (NotEnrolledRow r : rows) {
                Row row = sheet.createRow(rowIdx++);
                int c = 0;
                row.createCell(c++).setCellValue(nullOrEmpty(r.getRegisterNo()));
                row.createCell(c++).setCellValue(nullOrEmpty(r.getName()));
                row.createCell(c++).setCellValue(nullOrEmpty(r.getEmail()));
                row.createCell(c++).setCellValue(nullOrEmpty(r.getStudentDept()));
                row.createCell(c++).setCellValue(nullOrEmpty(r.getSession()));
            }

            for (int i = 0; i < cols.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(bos);
            return bos.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return new byte[0];
        }
    }

    public byte[] generateSingleCoursePdf(String courseCode, String session) {
        String effectiveSession = session;   // will be "25-26OS" or "25-26ES"

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            // 1) Find course
            Course course = courseRepository.findByCourseCodeAndSession(courseCode, effectiveSession);
            if (course == null) {
                // empty PDF with message
                Document emptyDoc = new Document(PageSize.A4);
                PdfWriter.getInstance(emptyDoc, baos);
                emptyDoc.open();
                emptyDoc.add(new Paragraph("No such course for session " + effectiveSession));
                emptyDoc.close();
                return baos.toByteArray();
            }

            // 2) Find enrollments for this course+session
            List<Enrollment> enrollmentsForCourse = enrollmentRepository.findByCourseCodeAndIdSession(courseCode,
                    effectiveSession);

            Document document = new Document(PageSize.A4.rotate());
            PdfWriter.getInstance(document, baos);
            document.open();

            // Title
            String title = "Enrollments for " + course.getCourseCode() + " - "
                    + course.getCourseTitle() + " (Dept: " + course.getBranch()
                    + ") - Session " + effectiveSession;

            Font titleFont = new Font(Font.FontFamily.HELVETICA, 14, Font.BOLD);
            Paragraph titlePara = new Paragraph(title, titleFont);
            titlePara.setAlignment(Element.ALIGN_CENTER);
            titlePara.setSpacingAfter(10f);
            document.add(titlePara);

            // Table headers (no Changed? column)
            PdfPTable table = new PdfPTable(6);
            table.setWidthPercentage(100);
            table.setWidths(new float[] { 1f, 2f, 3f, 4f, 2f, 2f });

            addHeaderCell(table, "S.No");
            addHeaderCell(table, "Register No");
            addHeaderCell(table, "Name");
            addHeaderCell(table, "Email");
            addHeaderCell(table, "Student Dept");
            addHeaderCell(table, "Session");

            int i = 1;
            for (Enrollment e : enrollmentsForCourse) {
                String regNoStr = "";
                if (e.getId() != null && e.getId().getRegistrationNo() != null) {
                    regNoStr = String.valueOf(e.getId().getRegistrationNo());
                } else {
                    Student s = studentRepository.findByEmail(e.getEmail());
                    if (s != null && s.getRegistrationNo() != null) {
                        regNoStr = String.valueOf(s.getRegistrationNo());
                    }
                }

                addCell(table, String.valueOf(i++));
                addCell(table, regNoStr);
                addCell(table, e.getName());
                addCell(table, e.getEmail());
                addCell(table, e.getBranch());
                addCell(table, effectiveSession);
            }

            document.add(table);
            document.close();
            return baos.toByteArray();

        } catch (Exception ex) {
            ex.printStackTrace();
            return new byte[0];
        }
    }

    public byte[] generateSingleCourseExcel(String courseCode, String session) {
        String effectiveSession = session;   // will be "25-26OS" or "25-26ES"

        try (Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream bos = new ByteArrayOutputStream()) {

            // 1) Find course for this session
            Course course = courseRepository.findByCourseCodeAndSession(courseCode, effectiveSession);
            if (course == null) {
                Sheet sheet = workbook.createSheet("Error");
                Row row = sheet.createRow(0);
                row.createCell(0).setCellValue("No such course for session " + effectiveSession);
                workbook.write(bos);
                return bos.toByteArray();
            }

            // 2) ONLY enrollments for THIS course + session
            List<Enrollment> enrollmentsForCourse = enrollmentRepository.findByCourseCodeAndIdSession(courseCode,
                    effectiveSession);

            Sheet sheet = workbook.createSheet("Course_" + courseCode);

            // Header style
            CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            int rowIdx = 0;
            Row header = sheet.createRow(rowIdx++);

            // Your enrollment table + Session
            String[] cols = {
                    "S.No",
                    "Register No",
                    "Name",
                    "Email",
                    "Department",
                    "Course Code",
                    "Course Title",
                    "Session"
            };

            for (int i = 0; i < cols.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(cols[i]);
                cell.setCellStyle(headerStyle);
            }

            int i = 1;
            for (Enrollment e : enrollmentsForCourse) {
                Row row = sheet.createRow(rowIdx++);
                int c = 0;

                // Register No (from composite key or lookup)
                String regNoStr = "";
                if (e.getId() != null && e.getId().getRegistrationNo() != null) {
                    regNoStr = String.valueOf(e.getId().getRegistrationNo());
                } else {
                    Student s = studentRepository.findByEmail(e.getEmail());
                    if (s != null && s.getRegistrationNo() != null) {
                        regNoStr = String.valueOf(s.getRegistrationNo());
                    }
                }

                row.createCell(c++).setCellValue(i++); // S.No
                row.createCell(c++).setCellValue(regNoStr); // Register No
                row.createCell(c++).setCellValue(e.getName()); // Name
                row.createCell(c++).setCellValue(e.getEmail()); // Email
                row.createCell(c++).setCellValue(e.getBranch()); // Department
                row.createCell(c++).setCellValue(e.getCourseCode()); // Course Code
                row.createCell(c++).setCellValue(e.getCourseTitle()); // Course Title
                row.createCell(c++).setCellValue(effectiveSession); // Session
            }

            for (int col = 0; col < cols.length; col++) {
                sheet.autoSizeColumn(col);
            }

            workbook.write(bos);
            return bos.toByteArray();
        } catch (Exception ex) {
            ex.printStackTrace();
            return new byte[0];
        }
    }

    public String uploadStudentsExcel(MultipartFile file, String session) {
        String effectiveSession = session;   // will be "25-26OS" or "25-26ES"

        List<Student> studentsToSave = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                return "No sheet found in student Excel.";
            }

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                return "Student Excel is missing header row.";
            }

            // Build normalizedHeader -> columnIndex map
            Map<String, Integer> colIndex = new HashMap<>();
            for (Cell cell : headerRow) {
                String raw = getCellValue(cell); // existing helper
                String normalized = raw.trim()
                        .toLowerCase()
                        .replace(".", "")
                        .replace(" ", "");
                if (!normalized.isBlank()) {
                    colIndex.put(normalized, cell.getColumnIndex());
                }
            }

            // Expected normalized headers for students
            if (!colIndex.containsKey("registrationno")
                    || !colIndex.containsKey("name")
                    || !colIndex.containsKey("branch")
                    || !colIndex.containsKey("dob")
                    || !colIndex.containsKey("emailid")) {
                return "Student Excel missing required headers. Expected: Registration No, Name, Branch, DOB, Email ID";
            }

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null)
                    continue;

                String regNoStr = getCellValue(row.getCell(colIndex.get("registrationno")));
                if (regNoStr.isBlank())
                    continue;

                Long regNo;
                try {
                    regNo = Long.parseLong(regNoStr);
                } catch (NumberFormatException ex) {
                    continue;
                }

                String name = getCellValue(row.getCell(colIndex.get("name")));
                String branch = getCellValue(row.getCell(colIndex.get("branch")));
                String dobStr = getCellValue(row.getCell(colIndex.get("dob")));
                String email = getCellValue(row.getCell(colIndex.get("emailid")));

                java.time.LocalDate dob = null;
                Cell dobCell = row.getCell(colIndex.get("dob"));
                if (dobCell != null
                        && dobCell.getCellType() == CellType.NUMERIC
                        && DateUtil.isCellDateFormatted(dobCell)) {

                    java.util.Date date = dobCell.getDateCellValue();
                    dob = date.toInstant()
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate();
                } else if (!dobStr.isBlank()) {
                    try {
                        dob = java.time.LocalDate.parse(dobStr);
                    } catch (Exception ex) {
                        continue;
                    }
                } else {
                    continue;
                }

                Student s = new Student();
                s.setRegistrationNo(regNo);
                s.setName(name);
                s.setBranch(branch);
                s.setDob(dob);
                s.setEmail(email);

                studentsToSave.add(s);
            }
        } catch (IOException e) {
            return "Failed to read student Excel: " + e.getMessage();
        }

        if (!studentsToSave.isEmpty()) {
            studentRepository.saveAll(studentsToSave);
        }

        return "Students uploaded for session " + effectiveSession;
    }

    public String uploadCoursesExcel(MultipartFile file, String session) {
        String effectiveSession = session;   // will be "25-26OS" or "25-26ES"

        List<Course> coursesToSave = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                return "No sheet found in course Excel.";
            }

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                return "Course Excel is missing header row.";
            }

            // Build normalizedHeader -> columnIndex map
            Map<String, Integer> colIndex = new HashMap<>();
            for (Cell cell : headerRow) {
                String raw = getCellValue(cell);
                String normalized = raw.trim()
                        .toLowerCase()
                        .replace(".", "")
                        .replace(" ", "");
                if (!normalized.isBlank()) {
                    colIndex.put(normalized, cell.getColumnIndex());
                }
            }

            // Expected normalized headers for courses
            // SL No. column is read but not used, so slno is optional here
            if (!colIndex.containsKey("deptoffering")
                    || !colIndex.containsKey("coursecode")
                    || !colIndex.containsKey("coursetitle")
                    || !colIndex.containsKey("faculty")
                    || !colIndex.containsKey("capacity")
                    || !colIndex.containsKey("restrictedto")) {
                return "Course Excel missing required headers. Expected: SL No., Dept Offering, CourseCode, CourseTitle, Faculty, Capacity, Restricted to";
            }

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null)
                    continue;

                String code = getCellValue(row.getCell(colIndex.get("coursecode")));
                if (code.isBlank())
                    continue;

                String title = getCellValue(row.getCell(colIndex.get("coursetitle")));
                String faculty = getCellValue(row.getCell(colIndex.get("faculty")));
                String branch = getCellValue(row.getCell(colIndex.get("deptoffering"))); // maps to DB branch
                String capacityStr = getCellValue(row.getCell(colIndex.get("capacity")));
                String restricted = getCellValue(row.getCell(colIndex.get("restrictedto")));

                int capacity = 0;
                try {
                    if (!capacityStr.isBlank()) {
                        capacity = Integer.parseInt(capacityStr);
                    }
                } catch (NumberFormatException ex) {
                    capacity = 0;
                }

                Course c = new Course();
                c.setCourseCode(code);
                c.setCourseTitle(title);
                c.setFaculty(faculty);
                c.setBranch(branch);
                c.setCapacity(capacity);
                c.setEnrolled(0);
                c.setRestricted(restricted);
                c.setSession(effectiveSession);

                coursesToSave.add(c);
            }
        } catch (IOException e) {
            return "Failed to read course Excel: " + e.getMessage();
        }

        if (!coursesToSave.isEmpty()) {
            courseRepository.saveAll(coursesToSave);
        }

        return "Courses uploaded for session " + effectiveSession;
    }

public String verifyAndTruncate(String email, String password, String target, String session) {
    String effectiveSession = session;   // will be "25-26OS" or "25-26ES"

    // 1) Verify admin credentials
    Admin admin = adminRepository.findByEmail(email);
    if (admin == null || !admin.getPassword().equals(password)) {
        return "Invalid admin email or password.";
    }

    if (target == null || target.isBlank()) {
        return "No truncate target specified.";
    }

    String t = target.trim().toLowerCase();

    switch (t) {
        case "students": {
            long studentCount = studentRepository.count();
            if (studentCount == 0) {
                return "No students to truncate.";
            }
            // delete ONLY students
            studentRepository.deleteAll();
            break;
        }

        case "courses": {
            // delete ONLY courses for this session
            List<Course> courses = courseRepository.findBySession(effectiveSession);
            if (courses.isEmpty()) {
                return "No courses to truncate for session " + effectiveSession + ".";
            }
            courseRepository.deleteAll(courses);
            break;
        }

        case "enrollments": {
            // delete ONLY enrollments for this session
            List<Enrollment> sessionEnrollments = enrollmentRepository.findByIdSession(effectiveSession);
            if (sessionEnrollments.isEmpty()) {
                return "No enrollments to truncate for session " + effectiveSession + ".";
            }
            enrollmentRepository.deleteAll(sessionEnrollments);
            break;
        }

        default:
            return "Unknown truncate target: " + target;
    }

    return "Truncate success for " + t + " (session " + effectiveSession + ").";
}


}