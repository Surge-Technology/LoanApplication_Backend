package com.surge.loanManagement.controller;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngines;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.surge.loanManagement.DTO.TaskDTO;
import com.surge.loanManagement.model.JsonData;
import com.surge.loanManagement.model.Loan;
import com.surge.loanManagement.model.LoanApplicantDetails;
import com.surge.loanManagement.repository.LoanApplicantDetailsRepository;
import com.surge.loanManagement.service.DocumentService;
import com.surge.loanManagement.service.EmailService;
import com.surge.loanManagement.service.LoanApplicantService;
import com.surge.loanManagement.service.LoanDetailsService;

import jakarta.transaction.Transactional;

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

	@Autowired
	LoanDetailsService loanDetailsService;

	Map<String, Object> responseMap = new HashMap<>();
	private String processInstanceId;
	private String userId;
	private String emailId;
	private String taskId;
	private JsonNode rootNode;
	private String loanAccountNumber;
	private Map<String, Object> response;
	private static final Map<String, Map<String, Object>> loanApplications = new HashMap<>();
	private final Map<String, Object> loanResponseMap = new HashMap<>();
	private String emailIdData;
	private String loanStatus;

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
		loanAccountNumber = generateLoanAccountNumber();
		loanStatus = "Pending";
		Timestamp createdDate = new Timestamp(System.currentTimeMillis());

		if (rootNode.has("Files")) {
			JsonNode filesNode = rootNode.path("Files").path("otherFiles");
			for (JsonNode fileNode : filesNode) {
				String fileName = fileNode.path("name").asText();
				String fileContent = fileNode.path("content").asText();
				documentService.storeFile(fileName, fileContent);
			}
		}
//		loanApplicantDetailsRepository.saveJson(data, emailId, loanAccountNumber, createdDate, loanStatus, loanType,
//				loanAmount, applicantName);
		int rowsAffected = loanApplicantDetailsRepository.saveJson(data, emailId, loanAccountNumber, createdDate,
				loanStatus, loanType, loanAmount, applicantName);

		LoanApplicantDetails savedApplicant = loanApplicantDetailsRepository
				.findTopByEmailIdOrderByCreatedDateDesc(emailId);

		Long generatedId = (savedApplicant != null) ? savedApplicant.getId() : null;

		Map<String, Object> applicationData = new HashMap<>();
		applicationData.put("id", generatedId);
		applicationData.put("emailId", emailId);
		applicationData.put("loanAccountNumber", loanAccountNumber);
		applicationData.put("createdDate", createdDate);
		applicationData.put("loanStatus", loanStatus);
		applicationData.put("loanType", loanType);
		applicationData.put("loanAmount", loanAmount);
		applicationData.put("applicantName", applicantName);
		applicationData.put("jsonData", data);

		loanApplications.put(loanAccountNumber, applicationData);
		System.out.println(loanApplications);

		String subject = "Loan Application Submission Confirmation";
		String body = "Dear Applicant,\n\nYour loan application has been successfully submitted. We will process your request and update you shortly.\n\nThank you.";
		// emailService.sendSimpleEmail(emailId, subject, body);

		return ResponseEntity.ok(applicationData);
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
				loanDetailsMap.put("loanType", loanDetails.getLoanType());
				loanDetailsMap.put("loanAmount", loanDetails.getLoanAmount());

				loanDetailsResponseList.add(loanDetailsMap);
			}

			responseMap.put("loanDetails", loanDetailsResponseList);
		} else {
			responseMap.put("error", "No loan details found for the given email.");
		}

		return responseMap;
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

		System.out.println(loanApplications);

		ProcessEngine processEngine = ProcessEngines.getDefaultProcessEngine();
		TaskService taskService = processEngine.getTaskService();
		userId = user;
		taskService = processEngine.getTaskService();
		List<Task> tasks = taskService.createTaskQuery().processInstanceId(processInstanceId).active().list();
		List<TaskDTO> taskDTOs = tasks.stream().map(task -> {
			Timestamp creationTimestamp = task.getCreateTime() != null ? new Timestamp(task.getCreateTime().getTime())
					: null;
			return new TaskDTO(task.getId(), task.getName(), task.getAssignee(), task.getProcessInstanceId(),
					creationTimestamp, loanApplications);
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

	@CrossOrigin
	@PostMapping("/UnderWriterApprover")
	public ResponseEntity<String> UnderWriterApprover(@RequestBody String approval) throws JsonProcessingException {
		System.out.println("Received Approval Data: " + approval);
		ProcessEngine processEngine = ProcessEngines.getDefaultProcessEngine();
		TaskService taskService = processEngine.getTaskService();
		RuntimeService runtimeService = processEngine.getRuntimeService();
		ObjectMapper objectMapper = new ObjectMapper();
		JsonNode rootNode = objectMapper.readTree(approval);
		String finalDecision = rootNode.path("underWriterDecision").asText();
		String clarificationDetails = "";
		if ("needClarification".equals(finalDecision)) {
			clarificationDetails = rootNode.path("clarificationDetails").asText();
		}
		System.out.println("Final Decision: " + finalDecision);
		System.out.println("Clarification Details: " + clarificationDetails);
		runtimeService.setVariable(processInstanceId, "UnderWriter", finalDecision);
		if (!clarificationDetails.isEmpty()) {
			runtimeService.setVariable(processInstanceId, "clarificationDetails", clarificationDetails);
		}
		List<Task> tasks = taskService.createTaskQuery().processInstanceId(processInstanceId).active().list();
		Task task = tasks.get(0);
		taskId = task.getId();
		userId = task.getAssignee();
		System.out.println("Task ID: " + taskId);
		System.out.println("Assigned User: " + userId);
		taskService.claim(taskId, userId);
		System.out.println("Task claimed successfully by user: " + userId);
		taskService.complete(taskId);
		System.out.println("Task completed successfully.");
		return ResponseEntity.ok("Loan approval process completed successfully.");
	}

	@CrossOrigin
	@PostMapping("/calculateTenureInterest")
	public ResponseEntity<Map<String, Object>> calculateTenureAndInterest(
			@RequestBody Map<String, Object> requestData) {
		Long loanAmount = Long.parseLong(requestData.get("loanAmount").toString());

		int tenure;
		double interestRate;
		if (loanAmount <= 100000) {
			tenure = 5;
			interestRate = 5.0;
		} else if (loanAmount <= 500000) {
			tenure = 12;
			interestRate = 6.0;
		} else if (loanAmount <= 1000000) {
			tenure = 24;
			interestRate = 7.0;
		} else if (loanAmount <= 5000000) {
			tenure = 36;
			interestRate = 8.0;
		} else {
			tenure = 48;
			interestRate = 9.0;
		}

		Map<String, Object> response = new HashMap<>();
		response.put("loanAmount", loanAmount);
		response.put("tenure", tenure);
		response.put("interestRate", interestRate);

		return ResponseEntity.ok(response);
	}

	@CrossOrigin
	@PostMapping("/LegalApprover")
	public ResponseEntity<String> LegalApprover(@RequestBody String approval) throws JsonProcessingException {
		System.out.println("Received Approval Data: " + approval);
		ProcessEngine processEngine = ProcessEngines.getDefaultProcessEngine();
		TaskService taskService = processEngine.getTaskService();
		RuntimeService runtimeService = processEngine.getRuntimeService();
		ObjectMapper objectMapper = new ObjectMapper();
		JsonNode rootNode = objectMapper.readTree(approval);
		String finalDecision = rootNode.path("LegalApprover").asText();
		String clarificationDetails = "";
		if ("needClarification".equals(finalDecision)) {
			clarificationDetails = rootNode.path("clarificationDetails").asText();
		}
		System.out.println("Final Decision: " + finalDecision);
		System.out.println("Clarification Details: " + clarificationDetails);
		runtimeService.setVariable(processInstanceId, "LegalApprover", finalDecision);
		if (!clarificationDetails.isEmpty()) {
			runtimeService.setVariable(processInstanceId, "clarificationDetails", clarificationDetails);
		}
		List<Task> tasks = taskService.createTaskQuery().processInstanceId(processInstanceId).active().list();
		Task task = tasks.get(0);
		taskId = task.getId();
		userId = task.getAssignee();
		System.out.println("Task ID: " + taskId);
		System.out.println("Assigned User: " + userId);
		taskService.claim(taskId, userId);
		System.out.println("Task claimed successfully by user: " + userId);
		taskService.complete(taskId);
		System.out.println("Task completed successfully.");
		return ResponseEntity.ok("Loan approval process completed successfully.");
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

	@CrossOrigin
	@GetMapping("/getApplicantData/{id}")
	public ResponseEntity<JsonNode> getApplicantData(@PathVariable Long id) {
		Optional<LoanApplicantDetails> jsonData = loanApplicantDetailsRepository.findById(id);

		if (jsonData.isPresent()) {
			try {
				ObjectMapper objectMapper = new ObjectMapper();
				JsonNode jsonNode = objectMapper.readTree(jsonData.get().getData());
				return ResponseEntity.ok(jsonNode);
			} catch (IOException e) {
				e.printStackTrace();
				return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
			}
		} else {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
		}
	}
//
//	@CrossOrigin
//	 @GetMapping("/getApplicantDataByAccount/{loanAccountNumber}")
//	    public ResponseEntity<JsonNode> getApplicantDataByAccount(@PathVariable String loanAccountNumber) {
//	        Optional<LoanApplicantDetails> jsonData = loanApplicantDetailsRepository.findByLoanAccountNumber(loanAccountNumber);
//
//	        
//	        if (jsonData.isPresent()) {
//	            try {
//	                ObjectMapper objectMapper = new ObjectMapper();
//	                JsonNode jsonNode = objectMapper.readTree(jsonData.get().getData());
//	                return ResponseEntity.ok(jsonNode);
//	            } catch (IOException e) {
//	                e.printStackTrace();
//	                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
//	            }
//	        } else {
//	            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
//	        }
//	    }

	@CrossOrigin
	@GetMapping("/getLoanDetails")
	public ResponseEntity<Map<String, Object>> getLoanDetails() {
		if (loanResponseMap.isEmpty()) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(Collections.singletonMap("message", "No loan details found"));
		}
		return ResponseEntity.ok(loanResponseMap);
	}



    @PostMapping("/calculateTenureInterestSaveData")
    public ResponseEntity<Map<String, Object>> calculateAndSaveLoan(@RequestBody Map<String, Object> requestData) {
    	 String loanAmount = requestData.get("loanAmount").toString(); 
         Long loanAmountLong = Long.parseLong(loanAmount); 

         int tenure;
         double interestRate;
         if (loanAmountLong <= 100000) {
             tenure = 5;
             interestRate = 5.0;
         } else if (loanAmountLong <= 500000) {
             tenure = 12;
             interestRate = 6.0;
         } else if (loanAmountLong <= 1000000) {
             tenure = 24;
             interestRate = 7.0;
         } else if (loanAmountLong <= 5000000) {
             tenure = 36;
             interestRate = 8.0;
         } else {
             tenure = 48;
             interestRate = 9.0;
         }

        Loan loan = new Loan();
        loan.setLoanAmount(loanAmount);
        loan.setTenure(tenure);
        loan.setInterest(interestRate);
        String uanNumber = generateLoanAccountNumber();
        loan.setUanNumber(uanNumber);
        loan.setLoanAccountNumber(loanAccountNumber);
        loan.setLoanStatus(loanStatus);

        Loan savedLoan = loanDetailsService.saveLoan(loan);

        
        Map<String, Object> response = new HashMap<>();
        response.put("loanId", savedLoan.getLoanId());
        response.put("loanAmount", savedLoan.getLoanAmount());
        response.put("tenure", savedLoan.getTenure());
        response.put("interestRate", savedLoan.getInterest());
        response.put("uanNumber", uanNumber);
        response.put("loanStatus", loanStatus);
        response.put("loanAccountNumber", loanAccountNumber);
        
        System.out.println(response);

        return ResponseEntity.ok(response);
    }
	private static final String BASE_NUMBER = "22507000000000";
	private long lastGeneratedNumber = Long.parseLong(BASE_NUMBER);
	private String uanNumber;

//	@CrossOrigin
//	@PostMapping("/generateAccountNumber")
	public ResponseEntity<String> generateAccountNumber() {
		lastGeneratedNumber += 1;

		uanNumber = String.format("%014d", lastGeneratedNumber);

		System.out.println(uanNumber);
		return ResponseEntity.ok(uanNumber);
	}

//	@CrossOrigin
//	@PostMapping("/emailAccountGenerator")
//	public String emailAccountGenerator() {
//		System.out.println("Account Number: " + uanNumber);
//		System.out.println("Sending email to: " + emailIdData);
//		String to = emailIdData;
//		String subject = "Account Generated Successfully - Your Account Number: " + uanNumber;
//		String body = "Dear Customer,\n\n"
//				+ "We are pleased to inform you that your account has been successfully created. "
//				+ "Your new account number is: " + uanNumber + ".\n\n";
//
//		emailService.sendSimpleEmail(to, subject, body);
//		return "Email Sent Successfully";
//	}

	@CrossOrigin
	@GetMapping("/getEmailId")
	public String getEmail() {

		ProcessEngine processEngine = ProcessEngines.getDefaultProcessEngine();
		RuntimeService runtimeService = processEngine.getRuntimeService();

		emailIdData = (String) runtimeService.getVariable(processInstanceId, "emailId");

		System.out.println("Email ID: " + emailIdData);
		return emailIdData != null ? emailIdData : "Email ID not found!";
	}

	@CrossOrigin
	@GetMapping("/download-all-Files")
	public ResponseEntity<Resource> downloadAll() {
		try {
			Path zipFilePath = Paths.get("C:/Users/STS178/Desktop/FileSystem/").resolve("all-files.zip");

			try (FileOutputStream fos = new FileOutputStream(zipFilePath.toFile());
					ZipOutputStream zipOut = new ZipOutputStream(fos)) {

				File directory = new File("C:/Users/STS178/Desktop/FileSystem/");
				File[] files = directory.listFiles();
				if (files != null) {
					for (File file : files) {
						if (file.isFile()) {
							try (FileInputStream fis = new FileInputStream(file)) {
								ZipEntry zipEntry = new ZipEntry(file.getName());
								zipOut.putNextEntry(zipEntry);

								byte[] buffer = new byte[1024];
								int length;
								while ((length = fis.read(buffer)) >= 0) {
									zipOut.write(buffer, 0, length);
								}
							}
						}
					}
				}
			}
			Resource resource = new FileSystemResource(zipFilePath.toFile());
			return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"all-files.zip\"")
					.body(resource);

		} catch (IOException e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
		}
	}

	@CrossOrigin
	@GetMapping("/downloadFileByEmailId")
	public ResponseEntity<?> getJsonDataByEmailAndDownloadFile(@RequestParam String emailId)
			throws FileNotFoundException {
		List<LoanApplicantDetails> jsonDataOptional = loanApplicantDetailsRepository.findByEmailId(emailId);

		LoanApplicantDetails jsonData = jsonDataOptional.get(0);
		ObjectMapper objectMapper = new ObjectMapper();
		try {
			JsonNode jsonNode = objectMapper.readTree(jsonData.getData());
			if (jsonNode.has("Files")) {
				JsonNode filesNode = jsonNode.path("Files").path("otherFiles");
				for (JsonNode fileNode : filesNode) {
					String fileName = fileNode.path("name").asText();
					String fileContent = fileNode.path("content").asText();

					Resource file = documentService.loadFileAsResource(fileName);
					System.out.println("File exists: " + file.exists() + ", isReadable: " + file.isReadable());

					if (file.exists() && file.isReadable()) {
						return ResponseEntity.ok()
								.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
								.contentType(MediaType.APPLICATION_OCTET_STREAM).body(file);
					} else {
						return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File not found: " + fileName);
					}
				}
			}
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No files found for this emailId.");
		} catch (JsonProcessingException e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error parsing JSON data");
		}
	}
	
	@GetMapping("/updateStatusApproved/{loanAccountNumber}")
	@Transactional
	public String updateStatus(@PathVariable String loanAccountNumber) {
	    LoanApplicantDetails detail = loanApplicantDetailsRepository.findByLoanAccountNumber(loanAccountNumber);
 
	    if (detail == null) {
	        return "Loan applicant not found";
	    }
	    ObjectMapper objectMapper = new ObjectMapper();
	    String validJsonString;
	    try {
	        ObjectNode jsonNode = objectMapper.createObjectNode();
	        jsonNode.put("updated", detail.getData());
	        validJsonString = objectMapper.writeValueAsString(jsonNode);
	    } catch (Exception e) {
	        return "Failed to generate JSON: " + e.getMessage();
	    }
	    loanApplicantDetailsRepository.updateLoanStatus(loanAccountNumber, "Approved", validJsonString);
	    
	    loanStatus = "Approved";
 
	    return "Status updated successfully";
	}
	
	@GetMapping("/updateStatusDisbursed/{loanAccountNumber}")
	@Transactional
	public String updateStatusDisbursed(@PathVariable String loanAccountNumber) {
	    LoanApplicantDetails detail = loanApplicantDetailsRepository.findByLoanAccountNumber(loanAccountNumber);
 
	    if (detail == null) {
	        return "Loan applicant not found";
	    }
	    ObjectMapper objectMapper = new ObjectMapper();
	    String validJsonString;
	    try {
	        ObjectNode jsonNode = objectMapper.createObjectNode();
	        jsonNode.put("updated", detail.getData());
	        validJsonString = objectMapper.writeValueAsString(jsonNode);
	    } catch (Exception e) {
	        return "Failed to generate JSON: " + e.getMessage();
	    }
	    loanApplicantDetailsRepository.updateLoanStatus(loanAccountNumber, "Disbursed", validJsonString);
	    
	    loanStatus = "Disbursed";
 
	    return "Status updated successfully";
	}
	

}