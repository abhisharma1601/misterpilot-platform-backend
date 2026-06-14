package online.misterpilot.platform.repository;

import online.misterpilot.platform.entity.ApiKey;
import online.misterpilot.platform.entity.TokenUsage;
import online.misterpilot.platform.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TokenUsageRepository extends JpaRepository<TokenUsage, Long> {

    List<TokenUsage> findByUserOrderByCreatedAtDesc(User user);

    List<TokenUsage> findByApiKeyOrderByCreatedAtDesc(ApiKey apiKey);

    List<TokenUsage> findByUserAndModel(User user, String model);

    List<TokenUsage> findByUserAndCreatedAtBetween(User user, LocalDateTime start, LocalDateTime end);

    List<TokenUsage> findByModelAndCreatedAtBetween(String model, LocalDateTime start, LocalDateTime end);

    // --- Dashboard stats ---

    long countByUser(User user);

    @Query("SELECT COALESCE(SUM(t.outputTokens + t.cacheHitTokens + t.cacheMissTokens), 0) " +
           "FROM TokenUsage t WHERE t.user = :user")
    long sumTokensByUser(@Param("user") User user);

    @Query("SELECT COALESCE(SUM(t.costUsd), 0) FROM TokenUsage t WHERE t.user = :user")
    BigDecimal sumCostUsdByUser(@Param("user") User user);

    @Query("SELECT COALESCE(SUM(t.costInr), 0) FROM TokenUsage t WHERE t.user = :user")
    BigDecimal sumCostInrByUser(@Param("user") User user);

    @Query("SELECT FUNCTION('DATE', t.createdAt), COUNT(t) " +
           "FROM TokenUsage t WHERE t.user = :user " +
           "GROUP BY FUNCTION('DATE', t.createdAt) ORDER BY FUNCTION('DATE', t.createdAt) DESC")
    List<Object[]> countByUserGroupByDate(@Param("user") User user);

    @Query("SELECT FUNCTION('DATE', t.createdAt), SUM(t.costInr) " +
           "FROM TokenUsage t WHERE t.user = :user " +
           "GROUP BY FUNCTION('DATE', t.createdAt) ORDER BY FUNCTION('DATE', t.createdAt) DESC")
    List<Object[]> sumCostInrByUserGroupByDate(@Param("user") User user);

    List<TokenUsage> findTop10ByUserOrderByCreatedAtDesc(User user);

    // --- Date‑specific stats ---

    @Query("SELECT COALESCE(SUM(t.outputTokens + t.cacheHitTokens + t.cacheMissTokens), 0), " +
           "COALESCE(SUM(t.costUsd), 0), COALESCE(SUM(t.costInr), 0), COUNT(t) " +
           "FROM TokenUsage t WHERE t.user = :user " +
           "AND FUNCTION('DATE', t.createdAt) = :date")
    Object[] aggregateByUserAndDate(@Param("user") User user, @Param("date") Date date);

    List<TokenUsage> findByUserAndCreatedAtBetweenOrderByCreatedAtDesc(
            User user, LocalDateTime start, LocalDateTime end);

    @Modifying
    @Query("DELETE FROM TokenUsage t WHERE t.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
