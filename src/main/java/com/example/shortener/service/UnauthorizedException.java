package com.example.shortener.service;

public class UnauthorizedException extends RuntimeException {

	public UnauthorizedException() {
		super("A bearer management token is required");
	}
}
