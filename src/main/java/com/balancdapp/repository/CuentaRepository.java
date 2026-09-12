package com.balancdapp.repository;

import com.balancdapp.model.Cuenta;
import com.balancdapp.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CuentaRepository extends JpaRepository<Cuenta, Long> {

    List<Cuenta> findByUser(User user);

    List<Cuenta> findByUserAndActivaTrue(User user);

    boolean existsByUserAndNombreIgnoreCase(User user, String nombre);
}
