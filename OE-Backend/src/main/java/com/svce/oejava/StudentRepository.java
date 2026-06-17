package com.svce.oejava;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {
    
    // Find student by email (replaces Excel file search)
    Student findByEmail(String email);

    Student findByRegistrationNo(Long registrationNo);
}
