package com.example.shortener.service;

public class ForbiddenException extends RuntimeException {

	public ForbiddenException() {
		super("The management token does not own this short URL");
	}
}
