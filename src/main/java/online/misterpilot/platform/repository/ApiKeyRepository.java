package online.misterpilot.platform.repository;

import online.misterpilot.platform.entity.ApiKey;
import online.misterpilot.platform.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    Optional<ApiKey> findByHashValue(String hashValue);

    Optional<ApiKey> findById(Integer id);

    List<ApiKey> findByUser(User user);

    List<ApiKey> findByUserAndActive(User user, Boolean active);

    long countByUserAndActive(User user, Boolean active);

    @Modifying
    @Query("DELETE FROM ApiKey k WHERE k.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
