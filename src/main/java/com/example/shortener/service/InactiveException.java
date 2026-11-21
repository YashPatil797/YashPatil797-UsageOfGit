package com.example.shortener.service;

public class InactiveException extends RuntimeException {

	public InactiveException(String code) {
		super("Short URL has been deactivated: " + code);
	}
}
