package com.svce.oejava;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Arrays;
import java.util.Set;

@Service
public class EnrollmentService {

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private courseRepository courseRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    // =================================================================================
    //  HELPER METHODS (Strictly Preserved from your original code)
    // =================================================================================

    private boolean isCourseRestrictedForStudent(Course course, String studentBranch) {
        String restricted = course.getRestricted();
        if (restricted == null || restricted.isBlank() || studentBranch == null) {
            return false;
        }
        String sb = studentBranch.trim().toUpperCase();
        // Handles "CS/IT" and "CS,IT" correctly
        String[] restrictedBranches = restricted.split("[,/]");
        for (String rb : restrictedBranches) {
            if (sb.equals(rb.trim().toUpperCase())) {
                return true;
            }
        }
        return false;
    }

    private Set<String> parseDeptList(String value) {
        if (value == null || value.trim().isEmpty()) return new HashSet<>();
        return Arrays.stream(value.split("[,/]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private Set<String> computeBlockedDepartmentsForStudent(String studentBranch, List<Course> sessionCourses) {
        Set<String> blocked = new HashSet<>();
        if (studentBranch == null || studentBranch.trim().isEmpty()) return blocked;
        String sb = studentBranch.trim().toUpperCase();

        for (Course c : sessionCourses) {
            Set<String> restrictedList = parseDeptList(c.getRestricted());
            Set<String> upper = restrictedList.stream()
                    .map(String::toUpperCase)
                    .collect(Collectors.toSet());
            
            if (upper.contains(sb)) {
                if (c.getBranch() != null) blocked.add(c.getBranch().trim().toUpperCase());
                blocked.addAll(upper);
            }
        }
        return blocked;
    }

    // =================================================================================
    //  CORE ENROLLMENT LOGIC (Thread-Safe & Optimized)
    // =================================================================================

    @Transactional // 🛡️ CRITICAL: Keeps the whole process safe
    public String enrollStudent(String email, String courseCode, String session) {
        String effectiveSession = (session == null || session.isBlank()) ? "OS" : session;

        Student student = studentRepository.findByEmail(email.toLowerCase());
        if (student == null) return "❌ Student not found";

        // 🔒 LOCKING: Prevents over-booking (Race Condition Fix)
        Course course = courseRepository.findByCourseCodeAndSessionWithLock(courseCode, effectiveSession);
        
        if (course == null) return "❌ Course not found for this session";

        // Check 1: Already Enrolled?
        EnrollmentId id = new EnrollmentId(student.getRegistrationNo(), effectiveSession);
        if (enrollmentRepository.existsById(id)) {
            return "❌ You have already enrolled in a course for this session";
        }

        // Check 2: Capacity (Thread-Safe now due to Lock)
        if (course.getEnrolled() >= course.getCapacity()) {
            return "❌ Course is full";
        }

        // Check 3: Department Restrictions (Preserved)
        List<Course> sessionCourses = courseRepository.findBySession(effectiveSession);
        Set<String> blockedDepartments = computeBlockedDepartmentsForStudent(student.getBranch(), sessionCourses);
        String courseDept = course.getBranch() == null ? "" : course.getBranch().trim().toUpperCase();
        
        if (blockedDepartments.contains(courseDept)) {
            return "❌ You are not allowed to enroll in courses offered by "
                    + course.getBranch() + " department for this session";
        }

        if (isCourseRestrictedForStudent(course, student.getBranch())) {
            return "❌ You are not eligible for this course";
        }

        // --- Save Enrollment ---
        Enrollment enrollment = new Enrollment();
        enrollment.setId(id);
        enrollment.setName(student.getName());
        enrollment.setBranch(student.getBranch());
        enrollment.setEmail(student.getEmail());
        enrollment.setCourseCode(course.getCourseCode());
        enrollment.setCourseTitle(course.getCourseTitle());
        enrollment.setTs(LocalDateTime.now());
        enrollment.setChangedCourse("no");

        enrollmentRepository.save(enrollment);

        // --- Increment Count ---
        course.setEnrolled(course.getEnrolled() + 1);
        courseRepository.save(course);

        return "✅ Enrolled successfully";
    }

    @Transactional
    public String changeEnrollment(String email, String oldCourseCode, String newCourseCode, String session) {
        String effectiveSession = (session == null || session.isBlank()) ? "OS" : session;

        Student student = studentRepository.findByEmail(email.toLowerCase());
        if (student == null) return "❌ Student not found";

        Enrollment existing = enrollmentRepository.findByEmailAndCourseCodeAndIdSession(
                email.toLowerCase(), oldCourseCode, effectiveSession);
        if (existing == null) return "❌ Existing enrollment not found";

        if ("yes".equalsIgnoreCase(existing.getChangedCourse())) {
            return "❌ You have already changed your course once for this session";
        }

        // 🔒 LOCK NEW COURSE ONLY
        Course newCourse = courseRepository.findByCourseCodeAndSessionWithLock(newCourseCode, effectiveSession);
        if (newCourse == null) return "❌ New course not found";

        // Capacity Check
        if (newCourse.getEnrolled() >= newCourse.getCapacity()) {
            return "❌ New course is full";
        }

        // Restriction Checks (Preserved)
        List<Course> sessionCourses = courseRepository.findBySession(effectiveSession);
        Set<String> blockedDepartments = computeBlockedDepartmentsForStudent(student.getBranch(), sessionCourses);
        String newDept = newCourse.getBranch() == null ? "" : newCourse.getBranch().trim().toUpperCase();
        
        if (blockedDepartments.contains(newDept)) {
            return "❌ Not allowed: " + newCourse.getBranch() + " department";
        }
        if (isCourseRestrictedForStudent(newCourse, student.getBranch())) {
            return "❌ Not eligible (Restricted)";
        }

        // --- Execution ---
        
        // 1. Update Enrollment Record
        existing.setCourseCode(newCourse.getCourseCode());
        existing.setCourseTitle(newCourse.getCourseTitle());
        existing.setTs(LocalDateTime.now());
        existing.setChangedCourse("yes");
        enrollmentRepository.save(existing);

        // 2. 🚀 ATOMIC DECREMENT (Safe Old Course Update)
        // We use a custom query to subtract 1 safely without locking the old row.
        courseRepository.decrementEnrolled(oldCourseCode, effectiveSession);

        // 3. Increment New Course
        newCourse.setEnrolled(newCourse.getEnrolled() + 1);
        courseRepository.save(newCourse);

        return "✅ Course changed successfully";
    }

    // Helper: Gets list of enrolled courses (No changes made here)
    public List<courseSelection> getEnrolledCoursesByEmail(String email, String session) {
        String effectiveSession = (session == null || session.isBlank()) ? "OS" : session;
        List<Enrollment> enrollments = enrollmentRepository.findByEmailAndIdSession(email.toLowerCase(), effectiveSession);
        List<courseSelection> result = new ArrayList<>();
        int idx = 1;

        for (Enrollment e : enrollments) {
            Course c = courseRepository.findById(e.getCourseCode()).orElse(null);
            if (c == null) continue;
            boolean full = c.getEnrolled() >= c.getCapacity();
            courseSelection dto = new courseSelection(
                    idx++, c.getCourseTitle(), c.getCourseCode(), c.getFaculty(),
                    c.getBranch(), c.getCapacity(), c.getEnrolled(),
                    c.getRestricted(), c.getSession(), full, false);
            dto.setSession(c.getSession());
            result.add(dto);
        }
        return result;
    }
}