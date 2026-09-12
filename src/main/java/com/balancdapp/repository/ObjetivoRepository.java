package com.balancdapp.repository;

import com.balancdapp.model.Objetivo;
import com.balancdapp.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ObjetivoRepository extends JpaRepository<Objetivo, Long> {

    List<Objetivo> findByUser(User user);

    List<Objetivo> findByUserAndActivoTrue(User user);
}
