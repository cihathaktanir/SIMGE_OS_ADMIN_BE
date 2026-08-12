package com.simge.adminbackend.appdb.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.simge.adminbackend.appdb.model.RegistrationRequest;

@Repository
public interface RegistrationRequestRepository extends JpaRepository<RegistrationRequest, Long> {

    List<RegistrationRequest> findByStatusOrderByIdDesc(String status);

    long countByStatus(String status);
}
