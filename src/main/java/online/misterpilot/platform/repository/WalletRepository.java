package online.misterpilot.platform.repository;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import online.misterpilot.platform.entity.User;
import online.misterpilot.platform.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByUser(User user);

    Optional<Wallet> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.user = :user")
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    Optional<Wallet> findByUserForUpdate(@Param("user") User user);

    @Modifying
    @Query("DELETE FROM Wallet w WHERE w.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
