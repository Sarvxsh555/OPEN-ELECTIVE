package com.svce.oejava;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = { "http://127.0.0.1:5500", "http://localhost:5500" })
public class AdminController {

    @Autowired
    private AdminService adminService;
    // --- 1. Admin Login ---

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody AdminLoginRequest request) {
        Map<String, Object> response = new HashMap<>();

        Optional<Admin> adminOpt = adminService.validateAdmin(request.getEmail(), request.getPassword());

        if (adminOpt.isPresent()) {
            Admin admin = adminOpt.get();
            response.put("success", true);
            response.put("message", "Admin login successful");
            Map<String, Object> adminDetails = new HashMap<>();
            adminDetails.put("email", admin.getEmail());
            response.put("adminDetails", adminDetails);
            return ResponseEntity.ok(response);
        } else {
            response.put("success", false);
            response.put("message", "Invalid admin email or password");
            return ResponseEntity.status(401).body(response);
        }
    }

    @GetMapping("/courses")
    public ResponseEntity<List<courseSelection>> getAllCourses(
            @RequestParam(required = false) String session) {
        List<courseSelection> courses = adminService.getCoursesForAdminBySession(session);
        return ResponseEntity.ok(courses);
    }

    // GET already returns courseSelection with id = running index, code = real key
    @GetMapping("/courses/{courseCode}/excel")
    public ResponseEntity<byte[]> downloadSingleCourseExcel(
            @PathVariable String courseCode,
            @RequestParam(required = false) String session) {

        byte[] excelBytes = adminService.generateSingleCourseExcel(courseCode, session);
        String effectiveSession = (session == null || session.isBlank()) ? "OS" : session;
        String fileName = "course_" + courseCode + "_" + effectiveSession + ".xlsx";

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + fileName + "\"")
                .header("Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .body(excelBytes);
    }

    // ADD
    @PostMapping("/courses")
    public ResponseEntity<Map<String, String>> addCourse(
            @RequestBody courseSelection newCourse,
            @RequestParam(required = false) String session) {

        String message = adminService.addCourseForSession(newCourse, session);
        Map<String, String> resp = new HashMap<>();
        resp.put("message", message);

        if (message.startsWith("Course added")) {
            return ResponseEntity.ok(resp);
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(resp);
        }
    }

    // UPDATE by course code
    @PutMapping("/courses/{code}")
    public ResponseEntity<Map<String, String>> updateCourse(
            @PathVariable String code,
            @RequestBody courseSelection updatedCourse,
            @RequestParam(required = false) String session) {

        String message = adminService.updateCourseForSession(code, updatedCourse, session);
        Map<String, String> resp = new HashMap<>();
        resp.put("message", message);

        if (message.endsWith("successfully.")) {
            return ResponseEntity.ok(resp);
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(resp);
        }
    }

    // DELETE by course code
    @DeleteMapping("/courses/{code}")
    public ResponseEntity<Map<String, String>> deleteCourse(
            @PathVariable String code,
            @RequestParam(required = false) String session) {

        String message = adminService.deleteCourseForSession(code, session);
        Map<String, String> resp = new HashMap<>();
        resp.put("message", message);

        if (message.startsWith("Course deleted")) {
            return ResponseEntity.ok(resp);
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(resp);
        }
    }

    @GetMapping("/enrollments")
    public ResponseEntity<List<Map<String, String>>> getAllEnrollments(
            @RequestParam(required = false) String session) {

        List<Map<String, String>> enrollments = adminService.getEnrollmentsForSession(session);
        return ResponseEntity.ok(enrollments);
    }

    @DeleteMapping("/enrollments")
    public ResponseEntity<Map<String, String>> deleteEnrollment(
            @RequestParam String email,
            @RequestParam String courseCode,
            @RequestParam(required = false) String session) {

        String message = adminService.deleteEnrollmentForSession(email, courseCode, session);
        Map<String, String> resp = new HashMap<>();
        resp.put("message", message);

        if (message.startsWith("Enrollment deleted")) {
            return ResponseEntity.ok(resp);
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(resp);
        }
    }

    @PostMapping("/enroll")
    public ResponseEntity<Map<String, Object>> manuallyEnrollStudent(
            @RequestBody Map<String, String> payload,
            @RequestParam(required = false) String session) {

        String email = payload.get("email");
        String courseCode = payload.get("courseCode");

        String message = adminService.adminManualEnrollment(email, courseCode, session);

        Map<String, Object> resp = new HashMap<>();
        resp.put("message", message);

        if (message.startsWith("Enrollment added")) {
            resp.put("success", true);
            return ResponseEntity.ok(resp);
        } else {
            resp.put("success", false);
            return ResponseEntity.badRequest().body(resp);
        }
    }

    @GetMapping("/downloads/all/pdf")
    public ResponseEntity<byte[]> downloadAllEnrollmentsPdf(
            @RequestParam(required = false) String session,
            @RequestParam(required = false, defaultValue = "dept") String groupBy) {

        byte[] pdfBytes = adminService.generateAllEnrollmentsPdf(session, groupBy);

        String effectiveSession = (session == null || session.isBlank()) ? "OS" : session;
        String filename = "all_enrollments_" + groupBy + "_" + effectiveSession + ".pdf";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    @GetMapping("/downloads/all/excel")
    public ResponseEntity<byte[]> downloadAllEnrollmentsExcel(
            @RequestParam(required = false) String session,
            @RequestParam(required = false, defaultValue = "dept") String groupBy) {

        byte[] bytes = adminService.generateAllEnrollmentsExcel(session, groupBy);

        String effectiveSession = (session == null || session.isBlank()) ? "OS" : session;
        String filename = "all_enrollments_" + groupBy + "_" + effectiveSession + ".xlsx";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(
                        MediaType.parseMediaType(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    @GetMapping("/downloads/enrolled/pdf")
    public ResponseEntity<byte[]> downloadEnrolledByDeptPdf(
            @RequestParam(required = false) String session,
            @RequestParam(required = false) String dept) {

        byte[] pdfBytes = adminService.generateEnrolledByDeptPdf(session, dept);

        String effectiveSession = (session == null || session.isBlank()) ? "OS" : session;
        String deptPart = (dept == null || dept.isBlank()) ? "all" : dept.trim();
        String filename = "enrolled_" + deptPart + "_" + effectiveSession + ".pdf";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    @GetMapping("/downloads/enrolled/excel")
    public ResponseEntity<byte[]> downloadEnrolledByDeptExcel(
            @RequestParam(required = false) String session,
            @RequestParam(required = false) String dept) {

        byte[] bytes = adminService.generateEnrolledByDeptExcel(session, dept);

        String effectiveSession = (session == null || session.isBlank()) ? "OS" : session;
        String deptPart = (dept == null || dept.isBlank()) ? "all" : dept.trim();
        String filename = "enrolled_" + deptPart + "_" + effectiveSession + ".xlsx";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(
                        MediaType.parseMediaType(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    @GetMapping("/downloads/not-enrolled/pdf")
    public ResponseEntity<byte[]> downloadNotEnrolledByDeptPdf(
            @RequestParam(required = false) String session,
            @RequestParam(required = false) String dept) {

        byte[] pdfBytes = adminService.generateNotEnrolledByDeptPdf(session, dept);

        String effectiveSession = (session == null || session.isBlank()) ? "OS" : session;
        String deptPart = (dept == null || dept.isBlank()) ? "all" : dept.trim();
        String filename = "not_enrolled_" + deptPart + "_" + effectiveSession + ".pdf";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    @GetMapping("/downloads/not-enrolled/excel")
    public ResponseEntity<byte[]> downloadNotEnrolledByDeptExcel(
            @RequestParam(required = false) String session,
            @RequestParam(required = false) String dept) {

        byte[] bytes = adminService.generateNotEnrolledByDeptExcel(session, dept);

        String effectiveSession = (session == null || session.isBlank()) ? "OS" : session;
        String deptPart = (dept == null || dept.isBlank()) ? "all" : dept.trim();
        String filename = "not_enrolled_" + deptPart + "_" + effectiveSession + ".xlsx";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(
                        MediaType.parseMediaType(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    @GetMapping("/courses/{courseCode}/pdf")
    public ResponseEntity<byte[]> downloadCourseEnrollmentPdf(
            @PathVariable String courseCode,
            @RequestParam(required = false) String session) {

        byte[] pdfBytes = adminService.generateSingleCoursePdf(courseCode, session);
        String effectiveSession = (session == null || session.isBlank()) ? "OS" : session;
        String filename = courseCode + "_" + effectiveSession + ".pdf";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    @PostMapping("/upload/students")
    public ResponseEntity<Map<String, String>> uploadStudents(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String session) {

        String msg = adminService.uploadStudentsExcel(file, session);
        Map<String, String> resp = new HashMap<>();
        resp.put("message", msg);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/upload/courses")
    public ResponseEntity<Map<String, String>> uploadCourses(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String session) {

        String msg = adminService.uploadCoursesExcel(file, session);
        Map<String, String> resp = new HashMap<>();
        resp.put("message", msg);
        return ResponseEntity.ok(resp);
    }

@PostMapping("/verify-and-truncate")
public ResponseEntity<Map<String, String>> verifyAndTruncate(
        @RequestBody Map<String, String> body,
        @RequestParam(required = false) String session) {

    String email = body.get("email");
    String password = body.get("password");
    String target = body.get("target"); 

    String message = adminService.verifyAndTruncate(email, password, target, session);

    Map<String, String> resp = new HashMap<>();
    resp.put("message", message);

    if (message.startsWith("Truncate success")) {
        return ResponseEntity.ok(resp);
    } else {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(resp);
    }
}

}