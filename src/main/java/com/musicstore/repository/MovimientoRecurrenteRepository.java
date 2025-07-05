package com.musicstore.repository;

import com.musicstore.model.MovimientoRecurrente;
import com.musicstore.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MovimientoRecurrenteRepository extends JpaRepository<MovimientoRecurrente, Long> {
    List<MovimientoRecurrente> findByUser(User user);
    List<MovimientoRecurrente> findByActivoTrue();
} 