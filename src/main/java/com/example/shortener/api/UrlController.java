package com.example.shortener.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.shortener.api.dto.CreateUrlRequest;
import com.example.shortener.api.dto.CreateUrlResponse;
import com.example.shortener.service.UrlService;

@RestController
@RequestMapping("/api/v1/urls")
public class UrlController {

	private final UrlService urlService;

	public UrlController(UrlService urlService) {
		this.urlService = urlService;
	}

	@PostMapping
	public ResponseEntity<CreateUrlResponse> create(@RequestBody CreateUrlRequest request) {
		CreateUrlResponse response = urlService.create(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}
}
