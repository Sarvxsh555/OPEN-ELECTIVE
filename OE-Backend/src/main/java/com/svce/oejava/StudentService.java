package com.svce.oejava;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class StudentService {
    
    @Autowired
    private StudentRepository studentRepository;
    public Optional<Map<String, String>> validateStudentByRegnoAndEmail(Long regno, String email) {

    // 1. Find the student using register number
    Student student = studentRepository.findByRegistrationNo(regno);

    if (student == null) {
        return Optional.empty(); // regno not found
    }

    // 2. Check if Google email matches DB email
    if (!student.getEmail().equalsIgnoreCase(email)) {
        return Optional.empty(); // email mismatch
    }

    // 3. Build successful response
    Map<String, String> result = new HashMap<>();
    result.put("email", student.getEmail());
    result.put("name", student.getName());
    result.put("branch", student.getBranch());
    result.put("registrationNo", String.valueOf(student.getRegistrationNo()));

    return Optional.of(result);
}

    // /**
    //  * Email-only login validation
    //  * Returns student details if found
    //  */
    // public Optional<Map<String, String>> validateStudent(String email) {
    //     // Query database for student by email
    //     Student student = studentRepository.findByEmail(email);
        
    //     if (student != null) {
    //         // Found! Return student details as Map
    //         Map<String, String> result = new HashMap<>();
    //         result.put("email", student.getEmail());
    //         result.put("name", student.getName());
    //         result.put("branch", student.getBranch());
    //         result.put("registrationNo", String.valueOf(student.getRegistrationNo()));
            
    //         return Optional.of(result);
    //     }
        
    //     // Not found
    //     return Optional.empty();
    // }
}
