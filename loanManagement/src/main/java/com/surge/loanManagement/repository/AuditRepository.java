package com.surge.loanManagement.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.surge.loanManagement.model.AuditDetails;

@Repository
public interface AuditRepository extends JpaRepository<AuditDetails,Long>{

}
