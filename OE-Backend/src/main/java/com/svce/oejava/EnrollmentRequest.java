package com.svce.oejava;

public class EnrollmentRequest {
    private String email;
    private String name;
    private String dept;
    private courseSelection selectedCourse;

    // Getters
    public String getEmail() {
        return email;
    }

    public String getName() {
        return name;
    }

    public String getDept() {
        return dept;
    }

    public courseSelection getSelectedCourse() {
        return selectedCourse;
    }

    // Setters (optional, but good practice)
    public void setEmail(String email) {
        this.email = email;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDept(String dept) {
        this.dept = dept;
    }

    public void setSelectedCourse(courseSelection selectedCourse) {
        this.selectedCourse = selectedCourse;
    }
}