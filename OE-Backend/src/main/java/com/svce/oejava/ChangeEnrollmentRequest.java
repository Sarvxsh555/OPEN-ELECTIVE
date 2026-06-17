package com.svce.oejava;

public class ChangeEnrollmentRequest {
    private String email;
    private String oldCourseCode;
    private String newCourseCode;
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getOldCourseCode() { return oldCourseCode; }
    public void setOldCourseCode(String oldCourseCode) { this.oldCourseCode = oldCourseCode; }
    public String getNewCourseCode() { return newCourseCode; }
    public void setNewCourseCode(String newCourseCode) { this.newCourseCode = newCourseCode; }
}

