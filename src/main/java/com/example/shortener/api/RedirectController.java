package com.example.shortener.api;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.example.shortener.domain.RedirectType;
import com.example.shortener.service.ResolvedUrl;
import com.example.shortener.service.UrlService;

@RestController
public class RedirectController {

	private final UrlService urlService;

	public RedirectController(UrlService urlService) {
		this.urlService = urlService;
	}

	@GetMapping("/{code:(?!api$|actuator$|health$)[A-Za-z0-9_-]{1,30}}")
	public ResponseEntity<Void> redirect(@PathVariable String code) {
		ResolvedUrl resolved = urlService.resolve(code);
		HttpStatus status = resolved.redirectType() == RedirectType.PERMANENT
				? HttpStatus.MOVED_PERMANENTLY
				: HttpStatus.FOUND;
		return ResponseEntity.status(status)
			.location(URI.create(resolved.url()))
			.build();
	}
}
