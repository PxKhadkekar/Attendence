package com.college.placement.attendance.repository;

import com.college.placement.attendance.entity.AttendanceChallenge;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AttendanceChallengeRepository extends JpaRepository<AttendanceChallenge, Long> {
    Optional<AttendanceChallenge> findByChallengeToken(String challengeToken);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM AttendanceChallenge c WHERE c.challengeToken = :token")
    Optional<AttendanceChallenge> findByChallengeTokenForUpdate(@Param("token") String token);
}
