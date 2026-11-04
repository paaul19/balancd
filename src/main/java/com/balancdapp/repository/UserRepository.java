package com.balancdapp.repository;

import com.balancdapp.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<User> findByAppleSub(String appleSub);

    @Query("SELECT u.apiTokenVersion FROM User u WHERE u.id = :id")
    Optional<Integer> findApiTokenVersionById(@Param("id") Long id);

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.apiTokenVersion = u.apiTokenVersion + 1 WHERE u.id = :id")
    int incrementApiTokenVersion(@Param("id") Long id);
} 