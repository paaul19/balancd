package com.musicstore.repository;

import com.musicstore.model.MesManual;
import com.musicstore.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MesManualRepository extends JpaRepository<MesManual, Long> {
    
    List<MesManual> findByUser(User user);
    
    boolean existsByUserAndAnioAndMes(User user, int anio, int mes);
    
    void deleteByUserAndAnioAndMes(User user, int anio, int mes);
    
    @Query("SELECT m.anio, m.mes FROM MesManual m WHERE m.user = :user")
    List<Object[]> findAnioMesByUser(@Param("user") User user);
} 