package com.surge.loanManagement.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.surge.loanManagement.model.User;
import com.surge.loanManagement.service.UserService;

@RestController
public class UserController {

	@Autowired
	UserService userService;

	@CrossOrigin
	@PostMapping("/createUser")
	public ResponseEntity<User> createUser(@RequestBody User user) {
		System.out.println(user);
		User createdUser = userService.createUser(user);
		return ResponseEntity.ok(createdUser);
	}

	@CrossOrigin
	@GetMapping("/getAllUsers")
	public ResponseEntity<List<User>> getAllUsers() {
		List<User> users = userService.getAllUsers();
		return ResponseEntity.ok(users);
	}

	@CrossOrigin
	@GetMapping("/getUserById/{userId}")
	public ResponseEntity<User> getUserById(@PathVariable long userId) {
		User user = userService.getUserById(userId);
		return ResponseEntity.ok(user);
	}

	@CrossOrigin
	@PutMapping("/updateUser/{userId}")
	public ResponseEntity<User> updateUser(@PathVariable long userId, @RequestBody User updatedUser) {
		User user = userService.updateUser(userId, updatedUser);
		return ResponseEntity.ok(user);
	}

	@CrossOrigin
	@DeleteMapping("/deleteUser/{userId}")
	public ResponseEntity<Void> deleteUser(@PathVariable long userId) {
		userService.deleteUser(userId);
		return ResponseEntity.noContent().build();
	}  
	
	  @CrossOrigin
	  @PostMapping("/login")
	    public ResponseEntity<String> login(@RequestBody User loginUser) {
	        User user = userService.login(loginUser.getEmail(), loginUser.getPassword());

	        if (user != null) {
	          //  return ResponseEntity.ok("Login successful! Welcome, " + user.getUsername());
	        	 return ResponseEntity.ok(user.getUsername());
	        } else {
	            return ResponseEntity.status(401).body("Invalid email/username or password");
	        }
	    }
//	  @PostMapping("/login")
//	    public ResponseEntity<String> login(@RequestBody User loginUser) {
//	        User user = userService.login(loginUser.getEmail(), loginUser.getPassword());
//	        if (user != null) {
//	            return ResponseEntity.ok("Login successful! Welcome, " + user.getUsername());
//	        } else {
//	            return ResponseEntity.status(401).body("Invalid email or password");
//	        }
//	    }
}
