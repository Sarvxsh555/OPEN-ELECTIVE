package com.svce.oejava;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "enrollments")
public class Enrollment {

    @EmbeddedId
    private EnrollmentId id;  // composite key: registration_no + session

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "branch", nullable = false)
    private String branch;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "course_code", nullable = false)
    private String courseCode;

    @Column(name = "course_title", nullable = false)
    private String courseTitle;

    @Column(name = "ts", nullable = false)
    private LocalDateTime ts;

    @Column(name = "changed_course", nullable = false)
    private String changedCourse; // "yes" or "no"

    public EnrollmentId getId() {
        return id;
    }

    public void setId(EnrollmentId id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCourseCode() {
        return courseCode;
    }

    public void setCourseCode(String courseCode) {
        this.courseCode = courseCode;
    }

    public String getCourseTitle() {
        return courseTitle;
    }

    public void setCourseTitle(String courseTitle) {
        this.courseTitle = courseTitle;
    }

    public LocalDateTime getTs() {
        return ts;
    }

    public void setTs(LocalDateTime ts) {
        this.ts = ts;
    }

    public String getChangedCourse() {
        return changedCourse;
    }

    public void setChangedCourse(String changedCourse) {
        this.changedCourse = changedCourse;
    }
}
