package com.svce.oejava;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, EnrollmentId> {

    // 1. Find all enrollments of a student (Works fine, email is a direct column)
    List<Enrollment> findByEmail(String email);

    // 2. Find all enrollments of a student in a session
    // Used in Student Portal. We map 'session' explicitly to 'id.session' to be safe.
    @Query("SELECT e FROM Enrollment e WHERE e.email = :email AND e.id.session = :session")
    List<Enrollment> findByEmailAndIdSession(@Param("email") String email, @Param("session") String session);

    // 3. Find specific enrollment row
    // Used for Checking/Changing courses. Explicitly matches composite key fields.
    @Query("SELECT e FROM Enrollment e WHERE e.email = :email AND e.courseCode = :courseCode AND e.id.session = :session")
    Enrollment findByEmailAndCourseCodeAndIdSession(@Param("email") String email, 
                                                    @Param("courseCode") String courseCode, 
                                                    @Param("session") String session);

    // 4. [CRITICAL FIX] Find all enrollments for a given session (for admin)
    // The previous automatic query failed here. This manual query guarantees it works.
    @Query("SELECT e FROM Enrollment e WHERE e.id.session = :session")
    List<Enrollment> findByIdSession(@Param("session") String session);

    // 5. Find all enrollments for a given course in a session (for admin)
    // Ensures we look inside the composite ID for the session.
    @Query("SELECT e FROM Enrollment e WHERE e.courseCode = :courseCode AND e.id.session = :session")
    List<Enrollment> findByCourseCodeAndIdSession(@Param("courseCode") String courseCode, 
                                                  @Param("session") String session);
}