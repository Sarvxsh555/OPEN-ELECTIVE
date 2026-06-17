package com.svce.oejava;

import jakarta.persistence.*;

@Entity
@Table(name = "courses")
public class Course {

    @Id
    @Column(name = "course_code")
    private String courseCode;          // PK

    @Column(name = "course_title", nullable = false)
    private String courseTitle;

    @Column(name = "faculty")
    private String faculty;            // maps to instructor in frontend

    @Column(name = "branch")
    private String branch;             // owning department

    @Column(name = "capacity", nullable = false)
    private int capacity;

    @Column(name = "enrolled", nullable = false)
    private int enrolled;

    @Column(name = "restricted")
    private String restricted;         // e.g. "IT,CS,AD" or NULL

    @Column(name = "session", nullable = false)
    private String session;            // e.g. "OS"

    // getters and setters

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

    public String getFaculty() {
        return faculty;
    }
    public void setFaculty(String faculty) {
        this.faculty = faculty;
    }

    public String getBranch() {
        return branch;
    }
    public void setBranch(String branch) {
        this.branch = branch;
    }

    public int getCapacity() {
        return capacity;
    }
    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public int getEnrolled() {
        return enrolled;
    }
    public void setEnrolled(int enrolled) {
        this.enrolled = enrolled;
    }

    public String getRestricted() {
        return restricted;
    }
    public void setRestricted(String restricted) {
        this.restricted = restricted;
    }

    public String getSession() {
        return session;
    }
    public void setSession(String session) {
        this.session = session;
    }
}
