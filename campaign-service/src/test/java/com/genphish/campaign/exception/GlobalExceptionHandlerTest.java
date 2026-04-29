package com.genphish.campaign.exception;

import com.genphish.campaign.dto.request.CreateCompanyRequest;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleNotFound_ShouldReturn404Body() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Company", "id", "123");

        ResponseEntity<Map<String, Object>> response = handler.handleNotFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("status", 404);
        assertThat(response.getBody()).containsEntry("error", "Not Found");
        assertThat(response.getBody().get("message")).asString().contains("Company not found");
        assertThat(response.getBody().get("timestamp")).isNotNull();
    }

    @Test
    void handleDuplicate_ShouldReturn409Body() {
        DuplicateResourceException ex = new DuplicateResourceException("Employee", "email", "a@b.com");

        ResponseEntity<Map<String, Object>> response = handler.handleDuplicate(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("status", 409);
        assertThat(response.getBody().get("message")).asString().contains("already exists");
    }

    @Test
    void handleInvalidOperation_ShouldReturn400Body() {
        InvalidOperationException ex = new InvalidOperationException("Invalid action");

        ResponseEntity<Map<String, Object>> response = handler.handleInvalidOperation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("status", 400);
        assertThat(response.getBody()).containsEntry("error", "Bad Request");
        assertThat(response.getBody()).containsEntry("message", "Invalid action");
    }

    @Test
    void handleValidation_ShouldReturnFieldErrors() throws Exception {
        CreateCompanyRequest request = new CreateCompanyRequest();
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(request, "request");
        bindingResult.addError(new FieldError("request", "name", "Company name is required"));
        bindingResult.addError(new FieldError("request", "domain", "Company domain is required"));

        Method method = ValidationDummy.class.getDeclaredMethod("createCompany", CreateCompanyRequest.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<Map<String, Object>> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("status", 400);
        assertThat(response.getBody()).containsEntry("error", "Validation Failed");

        @SuppressWarnings("unchecked")
        Map<String, String> details = (Map<String, String>) response.getBody().get("details");
        assertThat(details).containsEntry("name", "Company name is required");
        assertThat(details).containsEntry("domain", "Company domain is required");
    }

    @Test
    void handleGeneral_ShouldReturn500Body() {
        RuntimeException ex = new RuntimeException("boom");

        ResponseEntity<Map<String, Object>> response = handler.handleGeneral(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("status", 500);
        assertThat(response.getBody().get("message")).asString().contains("An unexpected error occurred: boom");
    }

    @SuppressWarnings("unused")
    private static class ValidationDummy {
        void createCompany(CreateCompanyRequest request) {
            // no-op
        }
    }
}
