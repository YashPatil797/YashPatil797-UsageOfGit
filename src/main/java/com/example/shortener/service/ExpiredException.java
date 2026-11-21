package com.example.shortener.service;

public class ExpiredException extends RuntimeException {

	public ExpiredException(String code) {
		super("Short URL has expired: " + code);
	}
}
