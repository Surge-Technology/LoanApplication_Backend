package com.surge.loanManagement.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
 
import com.surge.loanManagement.model.LoantransactionDetails;
 
@Repository
public interface LoantransactionDetailsRep  extends JpaRepository<LoantransactionDetails, Long>{
 
}