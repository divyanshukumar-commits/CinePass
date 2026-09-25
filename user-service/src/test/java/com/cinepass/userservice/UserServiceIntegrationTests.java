package com.cinepass.userservice;

import com.cinepass.userservice.controller.AuthController;
import com.cinepass.userservice.dto.AuthResponse;
import com.cinepass.userservice.dto.LoginRequest;
import com.cinepass.userservice.dto.RegisterRequest;
import com.cinepass.userservice.model.User;
import com.cinepass.userservice.repository.UserRepository;
import com.cinepass.userservice.security.JwtUtil;
import com.cinepass.userservice.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class UserServiceIntegrationTests {

    @Autowired
    private UserService userService;

    @Autowired
    private AuthController authController;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    private String testUsername;
    private String testEmail;
    private final String testPassword = "Password@123";

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        testUsername = "testuser_" + suffix;
        testEmail = "test_" + suffix + "@cinepass.com";
    }

    @Test
    @DisplayName("User registration succeeds and persists BCrypt hashed password")
    void testUserRegistrationAndPasswordHashing() {
        RegisterRequest registerRequest = new RegisterRequest(
                testUsername,
                testEmail,
                testPassword,
                "Test User",
                "ROLE_CUSTOMER"
        );

        ResponseEntity<?> responseEntity = authController.register(registerRequest);
        assertEquals(HttpStatus.CREATED, responseEntity.getStatusCode());
        assertNotNull(responseEntity.getBody());
        assertTrue(responseEntity.getBody() instanceof AuthResponse);

        AuthResponse authResponse = (AuthResponse) responseEntity.getBody();
        assertNotNull(authResponse.getToken());
        assertNotNull(authResponse.getUserId());
        assertEquals(testUsername, authResponse.getUsername());
        assertEquals("ROLE_CUSTOMER", authResponse.getRole());

        // Verify database persistence and password hashing
        User persistedUser = userRepository.findById(authResponse.getUserId()).orElseThrow();
        assertEquals(testUsername, persistedUser.getUsername());
        assertEquals(testEmail, persistedUser.getEmail());

        // Verify password is NOT plain text
        assertNotEquals(testPassword, persistedUser.getPassword());
        assertTrue(passwordEncoder.matches(testPassword, persistedUser.getPassword()));
    }

    @Test
    @DisplayName("Duplicate registration is rejected")
    void testDuplicateRegistration() {
        RegisterRequest registerRequest = new RegisterRequest(
                testUsername,
                testEmail,
                testPassword,
                "Original User",
                "ROLE_CUSTOMER"
        );
        ResponseEntity<?> firstResp = authController.register(registerRequest);
        assertEquals(HttpStatus.CREATED, firstResp.getStatusCode());

        // Duplicate username
        RegisterRequest dupUsername = new RegisterRequest(
                testUsername,
                "other_" + testEmail,
                testPassword,
                "Duplicate Username",
                "ROLE_CUSTOMER"
        );
        ResponseEntity<?> dupUserResp = authController.register(dupUsername);
        assertEquals(HttpStatus.BAD_REQUEST, dupUserResp.getStatusCode());

        // Duplicate email
        RegisterRequest dupEmail = new RegisterRequest(
                "other_" + testUsername,
                testEmail,
                testPassword,
                "Duplicate Email",
                "ROLE_CUSTOMER"
        );
        ResponseEntity<?> dupEmailResp = authController.register(dupEmail);
        assertEquals(HttpStatus.BAD_REQUEST, dupEmailResp.getStatusCode());
    }

    @Test
    @DisplayName("User login succeeds with correct credentials and returns valid JWT")
    void testUserLoginSuccess() {
        // Register user
        RegisterRequest registerRequest = new RegisterRequest(
                testUsername,
                testEmail,
                testPassword,
                "Login Tester",
                "ROLE_CUSTOMER"
        );
        authController.register(registerRequest);

        // Login
        LoginRequest loginRequest = new LoginRequest(testUsername, testPassword);
        ResponseEntity<?> loginResp = authController.login(loginRequest);

        assertEquals(HttpStatus.OK, loginResp.getStatusCode());
        assertTrue(loginResp.getBody() instanceof AuthResponse);

        AuthResponse authResponse = (AuthResponse) loginResp.getBody();
        assertNotNull(authResponse.getToken());

        // Validate JWT token
        assertTrue(jwtUtil.validateToken(authResponse.getToken()));
        assertEquals(testUsername, jwtUtil.extractUsername(authResponse.getToken()));
        assertEquals(authResponse.getUserId(), jwtUtil.extractUserId(authResponse.getToken()));

        // Validate via /auth/validate endpoint
        ResponseEntity<?> validateResp = authController.validateToken("Bearer " + authResponse.getToken());
        assertEquals(HttpStatus.OK, validateResp.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) validateResp.getBody();
        assertNotNull(body);
        assertEquals(true, body.get("valid"));
        assertEquals(testUsername, body.get("username"));
    }

    @Test
    @DisplayName("User login fails with incorrect password and returns 401")
    void testUserLoginFailure() {
        RegisterRequest registerRequest = new RegisterRequest(
                testUsername,
                testEmail,
                testPassword,
                "Bad Password Tester",
                "ROLE_CUSTOMER"
        );
        authController.register(registerRequest);

        LoginRequest badLogin = new LoginRequest(testUsername, "WrongPassword!");
        ResponseEntity<?> response = authController.login(badLogin);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @DisplayName("JWT validation rejects invalid or missing token")
    void testInvalidJwtValidation() {
        // Missing token
        ResponseEntity<?> missingResp = authController.validateToken(null);
        assertEquals(HttpStatus.UNAUTHORIZED, missingResp.getStatusCode());

        // Invalid token
        ResponseEntity<?> invalidResp = authController.validateToken("Bearer invalid.token.value");
        assertEquals(HttpStatus.UNAUTHORIZED, invalidResp.getStatusCode());
    }
}
