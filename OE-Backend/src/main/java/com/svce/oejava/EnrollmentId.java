package com.svce.oejava;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class EnrollmentId implements Serializable {

    @Column(name = "registration_no")
    private Long registrationNo;

    @Column(name = "session")
    private String session;   // OS or ES

    public EnrollmentId() {}

    public EnrollmentId(Long registrationNo, String session) {
        this.registrationNo = registrationNo;
        this.session = session;
    }

    public Long getRegistrationNo() {
        return registrationNo;
    }

    public void setRegistrationNo(Long registrationNo) {
        this.registrationNo = registrationNo;
    }

    public String getSession() {
        return session;
    }

    public void setSession(String session) {
        this.session = session;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EnrollmentId)) return false;
        EnrollmentId that = (EnrollmentId) o;
        return Objects.equals(registrationNo, that.registrationNo) &&
               Objects.equals(session, that.session);
    }

    @Override
    public int hashCode() {
        return Objects.hash(registrationNo, session);
    }
}
