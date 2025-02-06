package com.surge.loanManagement.controller;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngines;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.surge.loanManagement.DTO.TaskDTO;
import com.surge.loanManagement.model.LoanApplicantDetails;
import com.surge.loanManagement.repository.LoanApplicantDetailsRepository;
import com.surge.loanManagement.service.DocumentService;
import com.surge.loanManagement.service.EmailService;
import com.surge.loanManagement.service.LoanApplicantService;

@RestController
public class LoanApplicantDetailsController {
	
	@Autowired
	ProcessEngine processEngine;
	
	@Autowired
	TaskService taskService;

	@Autowired
	LoanApplicantDetailsRepository loanApplicantDetailsRepository;

	@Autowired
	LoanApplicantService loanApplicantService;

	@Autowired
	DocumentService documentService;

	@Autowired
	RuntimeService runtimeService;
	
	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	EmailService emailService;

	Map<String, Object> responseMap = new HashMap<>();
	private String processInstanceId;
	private String userId;
	private String emailId;
	private String taskId;

	@CrossOrigin
	@PostMapping("/saveApplicantDetails")
	public ResponseEntity<Map<String, Object>> saveJson(@RequestBody String data) throws IOException {
		ProcessInstance processInstance = processEngine.getRuntimeService()
				.createProcessInstanceByKey("Loan_Application").execute();
		ProcessEngine processEngine = ProcessEngines.getDefaultProcessEngine();
		TaskService taskService = processEngine.getTaskService();
		RuntimeService runtimeService = processEngine.getRuntimeService();
		processInstanceId = processInstance.getId();
		JsonNode rootNode = objectMapper.readTree(data);
		String dobString = rootNode.path("personalData").path("personalInfo").path("dob").asText();
		int age = calculateAgeFromDOB(dobString);

		double annualIncome = 0;
		if (rootNode.has("houseHold")) {
			annualIncome = rootNode.path("houseHold").path("annualIncome").asDouble();
		} else if (rootNode.has("employmentData")) {
			annualIncome = rootNode.path("employmentData").path("annualIncome").asDouble();
		}

		responseMap.put("age", age);
		responseMap.put("annualIncome", annualIncome);
		 emailId = rootNode.path("personalData").path("contactInfo").path("email").asText();
		runtimeService.setVariable(processInstanceId, "emailId", emailId);
		String applicantName = rootNode.path("personalData").path("personalInfo").path("legalFullName").asText();
		Long loanAmount = rootNode.path("bankDetails").path("loanAmount").asLong();
		String loanType = rootNode.path("bankDetails").path("loanType").asText();
		String loanAccountNumber = generateLoanAccountNumber();
		String loanStatus = "Pending";
		Timestamp createdDate = new Timestamp(System.currentTimeMillis());

		if (rootNode.has("Files")) {
			JsonNode filesNode = rootNode.path("Files").path("otherFiles");
			for (JsonNode fileNode : filesNode) {
				String fileName = fileNode.path("name").asText();
				String fileContent = fileNode.path("content").asText();
				documentService.storeFile(fileName, fileContent);
			}
		}
		loanApplicantDetailsRepository.saveJson(data, emailId, loanAccountNumber, createdDate, loanStatus, loanType,
				loanAmount, applicantName);

		String subject = "Loan Application Submission Confirmation";
		String body = "Dear Applicant,\n\nYour loan application has been successfully submitted. We will process your request and update you shortly.\n\nThank you.";
		// emailService.sendSimpleEmail(emailId, subject, body);

		return ResponseEntity.ok(responseMap);
	}

	private String generateLoanAccountNumber() {
		return String.format("%04d", new Random().nextInt(10000));
	}

	@CrossOrigin
	@GetMapping("/ApplicantDashboard")
	public Map<String, Object> getApplicantDashboard(@RequestParam String emailId) {
		Map<String, Object> responseMap = new HashMap<>();

		List<LoanApplicantDetails> loanDetailsList = loanApplicantService.getAllLoanDetailsByEmail(emailId);

		if (!loanDetailsList.isEmpty()) {
			List<Map<String, Object>> loanDetailsResponseList = new ArrayList<>();

			for (LoanApplicantDetails loanDetails : loanDetailsList) {
				Map<String, Object> loanDetailsMap = new HashMap<>();
				loanDetailsMap.put("applicantName", loanDetails.getApplicantName());
				loanDetailsMap.put("createdDate", loanDetails.getCreatedDate());
				loanDetailsMap.put("accountNumber", loanDetails.getLoanAccountNumber());
				loanDetailsMap.put("loanStatus", loanDetails.getLoanStatus());

				loanDetailsResponseList.add(loanDetailsMap);
			}

			responseMap.put("loanDetails", loanDetailsResponseList);
		} else {
			responseMap.put("error", "No loan details found for the given email.");
		}

		return responseMap;
	}

	@CrossOrigin
	@GetMapping("/getApplicantDetails")
	public List<JsonNode> getAllApplicantData() {
		List<JsonNode> response = new ArrayList<>();
		ObjectMapper objectMapper = new ObjectMapper();
		List<LoanApplicantDetails> jsonDataList = loanApplicantDetailsRepository.findAll();
		for (LoanApplicantDetails jsonData : jsonDataList) {
			try {
				JsonNode jsonNode = objectMapper.readTree(jsonData.getData());
				response.add(jsonNode);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return response;
	}

	@GetMapping("/calculateCibilScore")
	public int calculateCibil() {
		int age = (int) responseMap.get("age");
		double annualIncome = (double) responseMap.get("annualIncome");
		int cibilScore = calculateCibilScore(age, annualIncome);

		return cibilScore;
	}

	public static int calculateCibilScore(int age, double annualIncome) {
		int ageScore = getAgeScore(age);
		int incomeScore = getIncomeScore(annualIncome);
		return (ageScore + incomeScore) / 2;
	}

	private static int getAgeScore(int age) {
		if (age >= 18 && age <= 25) {
			return 300;
		} else if (age >= 26 && age <= 35) {
			return 600;
		} else if (age >= 36 && age <= 50) {
			return 750;
		} else if (age >= 51 && age <= 65) {
			return 800;
		} else if (age > 65) {
			return 850;
		}
		return 300;
	}

	private static int getIncomeScore(double annualIncome) {
		if (annualIncome < 500000) {
			return 300;
		} else if (annualIncome >= 500000 && annualIncome < 1000000) {
			return 600;
		} else if (annualIncome >= 1000000 && annualIncome < 2000000) {
			return 750;
		} else if (annualIncome >= 2000000) {
			return 800;
		}
		return 300;
	}

	public int calculateAgeFromDOB(String dobString) {
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
		LocalDate dob = LocalDate.parse(dobString, formatter);
		LocalDate currentDate = LocalDate.now();
		Period period = Period.between(dob, currentDate);
		return period.getYears();
	}
	
	
	@CrossOrigin
	@GetMapping("/getTaskBasedOnUser")
	public ResponseEntity<List<TaskDTO>> getActiveTasks(@RequestParam String user) {
		
		
		ProcessEngine processEngine = ProcessEngines.getDefaultProcessEngine();
		TaskService taskService = processEngine.getTaskService();
		userId = user;
		taskService = processEngine.getTaskService();
		List<Task> tasks = taskService.createTaskQuery().processInstanceId(processInstanceId).active().list();
		List<TaskDTO> taskDTOs = tasks.stream().map(task -> {
			Timestamp creationTimestamp = task.getCreateTime() != null ? new Timestamp(task.getCreateTime().getTime())
					: null;
			return new TaskDTO(task.getId(), task.getName(), task.getAssignee(), task.getProcessInstanceId(),
					creationTimestamp);
		}).collect(Collectors.toList());
		return ResponseEntity.ok(taskDTOs);
	}

	@CrossOrigin
	@PostMapping("/InitialApprover")
	public ResponseEntity<String> loanApproval(@RequestBody String approval) throws JsonProcessingException {
	    System.out.println("Received Approval Data: " + approval);
	    ProcessEngine processEngine = ProcessEngines.getDefaultProcessEngine();
	    TaskService taskService = processEngine.getTaskService();
	    RuntimeService runtimeService = processEngine.getRuntimeService();
	    ObjectMapper objectMapper = new ObjectMapper();
	    
	    JsonNode rootNode = objectMapper.readTree(approval);
	    String finalDecision = rootNode.path("InitialApprover").asText();  
	    System.out.println("Setting InitialApprover to: " + finalDecision);
	    runtimeService.setVariable(processInstanceId, "InitialApprover", finalDecision.trim());
	    String approvalValue = (String) runtimeService.getVariable(processInstanceId, "InitialApprover");
	    System.out.println("Current value of InitialApprover: " + approvalValue);
	    
	    List<Task> tasks = taskService.createTaskQuery().processInstanceId(processInstanceId).active().list();
	    Task task = tasks.get(0);  
	    String taskId = task.getId();
	    String userId = task.getAssignee();
	    
	    System.out.println("Task ID: " + taskId);
	    System.out.println("Assigned User: " + userId);
	    taskService.claim(taskId, userId);
	    System.out.println("Task claimed successfully by user: " + userId);
	    
	    taskService.complete(taskId);
	    System.out.println("Task completed successfully.");
	    
	    return ResponseEntity.ok("Loan approval process completed successfully.");
	}

}
