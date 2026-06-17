package com.svce.oejava;

public class GoogleLoginRequest {
    private String regno;
    private String email;  // Firebase gives email directly

    public String getRegno() {
        return regno;
    }

    public void setRegno(String regno) {
        this.regno = regno;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
