package com.svce.oejava;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;
import java.util.stream.Collectors;

@Service
public class courseService {

    @Autowired
    private courseRepository courseRepository;

    @Autowired
    private StudentRepository studentRepository;

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
                blocked.add(c.getBranch().trim().toUpperCase());
                blocked.addAll(upper);
            }
        }
        return blocked;
    }

    // ----------------- FOR STUDENT -----------------
    public List<courseSelection> getAvailableCoursesForEmail(String email, String session) {

        String effectiveSession =
                (session == null || session.isBlank()) ? null : session;

        if (effectiveSession == null) {
            return new ArrayList<>(); // no session selected yet
        }

        Student student = studentRepository.findByEmail(email.toLowerCase());

        if (student == null) {
            return new ArrayList<>();
        }

        String studentBranch = student.getBranch();
        List<Course> dbCourses = courseRepository.findBySession(effectiveSession);

        Set<String> blockedDepartments =
                computeBlockedDepartmentsForStudent(studentBranch, dbCourses);

        List<courseSelection> result = new ArrayList<>();
        int idx = 1;

        for (Course c : dbCourses) {
            boolean full = c.getEnrolled() >= c.getCapacity();

            String courseDept = c.getBranch() == null ? "" : c.getBranch().trim().toUpperCase();
            boolean disabledByDept = blockedDepartments.contains(courseDept);

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
                    disabledByDept
            );
            dto.setSession(c.getSession());
            result.add(dto);
        }

        return result;
    }

    // ----------------- FOR ADMIN or ALL -----------------
    public List<courseSelection> getAllCoursesForSession(String session) {

        String effectiveSession =
                (session == null || session.isBlank()) ? null : session;

        if (effectiveSession == null) {
            return new ArrayList<>(); // admin must select session too
        }

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
                    false
            );

            dto.setSession(c.getSession());
            result.add(dto);
        }

        return result;
    }

    public List<String> getAllSessions() {
    return courseRepository.findDistinctSessions();
}

}
