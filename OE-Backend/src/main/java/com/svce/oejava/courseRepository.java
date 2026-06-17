package com.svce.oejava;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface courseRepository extends JpaRepository<Course, String> {

    List<Course> findBySession(String session);

    boolean existsByCourseCodeAndSession(String courseCode, String session);

    Course findByCourseCodeAndSession(String courseCode, String session);

    void deleteByCourseCodeAndSession(String courseCode, String session);

    @Query("SELECT DISTINCT c.session FROM Course c")
    List<String> findDistinctSessions();

    // 🔒 PESSIMISTIC LOCK (For Enrollment / New Course)
    // Prevents Race Conditions (Over-booking)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Course c WHERE c.courseCode = :courseCode AND c.session = :session")
    Course findByCourseCodeAndSessionWithLock(@Param("courseCode") String courseCode, @Param("session") String session);

    // 🚀 ATOMIC UPDATE (For Withdrawal / Old Course)
    // Prevents Deadlocks by updating without a read-lock.
    // AND c.enrolled > 0 ensures we never go into negative numbers.
    @Modifying
    @Query("UPDATE Course c SET c.enrolled = c.enrolled - 1 WHERE c.courseCode = :courseCode AND c.session = :session AND c.enrolled > 0")
    void decrementEnrolled(@Param("courseCode") String courseCode, @Param("session") String session);
}