package com.example.shortener.api;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.example.shortener.service.UrlService;

@RestController
public class RedirectController {

	private final UrlService urlService;

	public RedirectController(UrlService urlService) {
		this.urlService = urlService;
	}

	@GetMapping("/{code:(?!api$|actuator$)[A-Za-z0-9_-]{1,30}}")
	public ResponseEntity<Void> redirect(@PathVariable String code) {
		URI location = URI.create(urlService.resolve(code));
		return ResponseEntity.status(HttpStatus.FOUND)
			.location(location)
			.build();
	}
}
