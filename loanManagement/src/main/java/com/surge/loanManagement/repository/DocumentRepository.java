package com.surge.loanManagement.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.surge.loanManagement.model.DocumentDetails;

@Repository
public interface DocumentRepository extends JpaRepository<DocumentDetails, Long>{

}
