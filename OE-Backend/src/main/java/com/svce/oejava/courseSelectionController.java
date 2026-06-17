package com.svce.oejava;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = { "http://127.0.0.1:5500", "http://localhost:5500" })
public class courseSelectionController {

    @Autowired
    private StudentService studentService; // DB login

    @Autowired
    private courseService courseService; // DB courses

    @Autowired
    private EnrollmentService enrollmentService; // DB enrollments


    /*
     * @Autowired
     * private ExcelService excelService; // still used for enrollments for now
     */

    // --------------------- LOGIN (EMAIL ONLY, DB) ---------------------
    // @PostMapping("/login")
    // public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest request) {
    //     Map<String, Object> response = new HashMap<>();

    //     Optional<Map<String, String>> studentOpt = studentService.validateStudent(request.getEmail());

    //     if (studentOpt.isPresent()) {
    //         response.put("success", true);
    //         response.put("message", "Login successful!");
    //         response.put("studentDetails", studentOpt.get());
    //         return ResponseEntity.ok(response);
    //     } else {
    //         response.put("success", false);
    //         response.put("message", "Student email not found");
    //         return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    //     }
    // }
@PostMapping("/login/google")
public ResponseEntity<Map<String, Object>> googleLogin(@RequestBody GoogleLoginRequest request) {

    Map<String, Object> response = new HashMap<>();

    try {
        // 1. Verify Google ID Token → get email
        if (request == null || request.getRegno() == null || request.getRegno().trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "Register number is required");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "Google email is required");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        String googleEmail = request.getEmail().trim().toLowerCase();


        // 2. Convert regno
        Long reg;
        try {
            reg = Long.parseLong(request.getRegno().trim());
        } catch (NumberFormatException ex) {
            response.put("success", false);
            response.put("message", "Register number must contain numbers only");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        // 3. Validate with DB
        Optional<Map<String, String>> studentOpt =
                studentService.validateStudentByRegnoAndEmail(reg, googleEmail);

        if (studentOpt.isEmpty()) {
            response.put("success", false);
            response.put("message", "Register number and email do not match");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        // SUCCESS
        response.put("success", true);
        response.put("studentDetails", studentOpt.get());
        return ResponseEntity.ok(response);

    } catch (Exception e) {
        response.put("success", false);
        response.put("message", "Server error during login. Check backend logs and database connection.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}

    // --------------------- GET COURSES (DB + SESSION) ---------------------
    /**
     * If 'email' is provided => return courses for that student (own dept
     * disabled).
     * Otherwise => return ALL courses for the given session.
     * session = "OS" or "ES" (from frontend dropdown).
     */
    @GetMapping("/courses")
    public ResponseEntity<List<courseSelection>> getCourses(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String session) {

        List<courseSelection> courses;

        if (email != null && !email.isBlank()) {
            courses = courseService.getAvailableCoursesForEmail(email, session);
        } else {
            courses = courseService.getAllCoursesForSession(session);
        }
        return ResponseEntity.ok(courses);
    }

    // --------------------- ENROLL (DB) ---------------------
    @PostMapping("/enroll")
    public ResponseEntity<Map<String, String>> enroll(@RequestBody EnrollmentRequest request,
            @RequestParam(required = false) String session) {
        String message = enrollmentService.enrollStudent(
                request.getEmail(),
                request.getSelectedCourse().getCode(),
                session);

        Map<String, String> response = new HashMap<>();
        response.put("message", message);

        if (message.startsWith("✅")) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    // --------------------- GET ENROLLMENTS FOR STUDENT (DB) ---------------------
    @GetMapping("/enrollments")
    public ResponseEntity<List<courseSelection>> getEnrolledCourses(@RequestParam String email,
            @RequestParam(required = false) String session) {
        List<courseSelection> enrolledCourses = enrollmentService.getEnrolledCoursesByEmail(email, session);
        return ResponseEntity.ok(enrolledCourses);
    }

    // --------------------- CHANGE ENROLLMENT (DB) ---------------------
    @PostMapping("/enroll/change")
    public ResponseEntity<Map<String, String>> changeEnrollment(
            @RequestBody ChangeEnrollmentRequest request,
            @RequestParam(required = false) String session) {

        String message = enrollmentService.changeEnrollment(
                request.getEmail(),
                request.getOldCourseCode(),
                request.getNewCourseCode(),
                session);

        Map<String, String> response = new HashMap<>();
        response.put("message", message);

        if (message.startsWith("✅")) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    // --------------------- GET ALL SESSIONS (NEW) ---------------------
    @GetMapping("/sessions")
    public ResponseEntity<List<String>> getSessions() {
        List<String> sessions = courseService.getAllSessions();
        return ResponseEntity.ok(sessions);
    }

}
