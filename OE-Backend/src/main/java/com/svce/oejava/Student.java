package com.svce.oejava;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import java.time.LocalDate;

@Entity
@Table(name = "students")
public class Student {

    @Id
    @Column(name = "registration_no")
    private Long registrationNo;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "branch", nullable = false)
    private String branch;

    @Column(name = "dob", nullable = false)
    private LocalDate dob;

    @Column(name = "email", unique = true, nullable = false)
    private String email;

    // Getters and Setters
    public Long getRegistrationNo() {
        return registrationNo;
    }

    public void setRegistrationNo(Long registrationNo) {
        this.registrationNo = registrationNo;
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

    public LocalDate getDob() {
        return dob;
    }

    public void setDob(LocalDate dob) {
        this.dob = dob;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email == null ? null : email.toLowerCase();
    }

}
