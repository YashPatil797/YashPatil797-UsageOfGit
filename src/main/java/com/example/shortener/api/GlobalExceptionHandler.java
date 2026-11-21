package com.example.shortener.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiError> handleValidation(IllegalArgumentException exception) {
		return ResponseEntity.badRequest()
			.body(new ApiError("invalid_request", exception.getMessage()));
	}

	@ExceptionHandler({ MethodArgumentNotValidException.class, HttpMessageNotReadableException.class })
	public ResponseEntity<ApiError> handleInvalidRequest(Exception exception) {
		return ResponseEntity.badRequest()
			.body(new ApiError("invalid_request", "Request body is invalid"));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiError> handleUnexpected(Exception exception) {
		LOGGER.error("Unexpected request failure", exception);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
			.body(new ApiError("internal_error", "An unexpected error occurred"));
	}

	private record ApiError(String error, String message) {
	}
}
