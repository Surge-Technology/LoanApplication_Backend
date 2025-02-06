package com.surge.loanManagement.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.surge.loanManagement.model.LoanApplicantDetails;
import com.surge.loanManagement.repository.LoanApplicantDetailsRepository;

@Service
public class LoanApplicantService {

	@Autowired
	LoanApplicantDetailsRepository loanApplicantDetailsRepository;
	
	public List<LoanApplicantDetails> getAllLoanDetailsByEmail(String emailId) {
	    return loanApplicantDetailsRepository.findAllByEmailIdSortedByDate(emailId);
	}

}
