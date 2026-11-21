package com.example.shortener.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.shortener.service.AliasTakenException;
import com.example.shortener.service.ExpiredException;
import com.example.shortener.service.NotFoundException;

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

	@ExceptionHandler(NotFoundException.class)
	public ResponseEntity<ApiError> handleNotFound(NotFoundException exception) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
			.body(new ApiError("not_found", exception.getMessage()));
	}

	@ExceptionHandler(AliasTakenException.class)
	public ResponseEntity<ApiError> handleAliasTaken(AliasTakenException exception) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
			.body(new ApiError("alias_taken", exception.getMessage()));
	}

	@ExceptionHandler(ExpiredException.class)
	public ResponseEntity<ApiError> handleExpired(ExpiredException exception) {
		return ResponseEntity.status(HttpStatus.GONE)
			.body(new ApiError("expired", exception.getMessage()));
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
