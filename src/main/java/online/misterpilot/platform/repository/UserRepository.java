package online.misterpilot.platform.repository;

import online.misterpilot.platform.entity.User;
import online.misterpilot.platform.enums.Role;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByGoogleId(String googleId);

    Page<User> findByRole(Role role, Pageable pageable);

    boolean existsByEmail(String email);

    @Modifying
    @Query("DELETE FROM User u WHERE u.id = :id")
    void deleteByIdBulk(@Param("id") Long id);
}
