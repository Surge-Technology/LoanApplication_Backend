package com.surge.loanManagement.repository;

import java.sql.Timestamp;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.surge.loanManagement.model.LoanApplicantDetails;

import jakarta.transaction.Transactional;

@Repository
public interface LoanApplicantDetailsRepository extends JpaRepository<LoanApplicantDetails, Long> {
	 // LoanApplicantDetails findByEmailId(String emailId);
	  
	@Query("SELECT l FROM LoanApplicantDetails l WHERE l.emailId = :emailId ORDER BY l.createdDate DESC")
	List<LoanApplicantDetails> findAllByEmailIdSortedByDate(@Param("emailId") String emailId);


    @Modifying
    @Transactional
    @Query(value = "INSERT INTO applicant_data (data, email_id, loan_account_number, created_date, loan_status, loan_type, loan_amount, applicant_name) "
                   + "VALUES (CAST(:data AS JSONB), :emailId, :loanAccountNumber, :createdDate, :loanStatus, :loanType, :loanAmount, :applicantName)", 
           nativeQuery = true)
    void saveJson(String data, String emailId, String loanAccountNumber, Timestamp createdDate, String loanStatus, String loanType, Long loanAmount, String applicantName);
}

  
