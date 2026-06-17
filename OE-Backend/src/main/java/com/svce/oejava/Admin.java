package com.svce.oejava;

import jakarta.persistence.*;

@Entity
@Table(name = "admins")
public class Admin {

    @Id
    @Column(name = "email", nullable = false, length = 100)
    private String email;          // PK

    @Column(name = "password", nullable = false, length = 100)
    private String password;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
