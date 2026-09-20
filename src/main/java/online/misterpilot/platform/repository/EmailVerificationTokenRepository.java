package online.misterpilot.platform.repository;

import online.misterpilot.platform.entity.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    Optional<EmailVerificationToken> findByToken(String token);

    /**
     * Finds an outstanding (unused and unexpired) token for an email —
     * i.e. a link that has been sent but not yet acted on.
     * Newest first, in case several somehow exist.
     */
    @Query("""
            SELECT t FROM EmailVerificationToken t
            WHERE t.email = :email
              AND t.used = false
              AND t.expiresAt > :now
            ORDER BY t.createdAt DESC
            """)
    Optional<EmailVerificationToken> findActiveByEmail(
            @Param("email") String email,
            @Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM EmailVerificationToken t WHERE t.email = :email")
    void deleteAllByEmail(@Param("email") String email);
}
