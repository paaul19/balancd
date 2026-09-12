package com.balancdapp.admin.repository;

import com.balancdapp.admin.model.AdminUserView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface AdminUserRepository extends JpaRepository<AdminUserView, Long> {

    List<AdminUserView> findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(String username, String email);

    @Modifying
    @Transactional
    @Query("UPDATE AdminUserView u SET u.verified = :verified WHERE u.id = :id")
    void setVerified(@Param("id") Long id, @Param("verified") boolean verified);

    @Modifying
    @Transactional
    @Query("UPDATE AdminUserView u SET u.baneado = :baneado WHERE u.id = :id")
    void setBaneado(@Param("id") Long id, @Param("baneado") boolean baneado);
}
