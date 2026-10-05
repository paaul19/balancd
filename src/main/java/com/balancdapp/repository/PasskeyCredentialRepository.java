package com.balancdapp.repository;

import com.balancdapp.model.PasskeyCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PasskeyCredentialRepository extends JpaRepository<PasskeyCredential, Long> {
    List<PasskeyCredential> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<PasskeyCredential> findByCredentialId(String credentialId);
    Optional<PasskeyCredential> findByIdAndUserId(Long id, Long userId);
}
