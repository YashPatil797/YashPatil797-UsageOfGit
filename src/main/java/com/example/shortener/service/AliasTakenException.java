package com.example.shortener.service;

public class AliasTakenException extends RuntimeException {

	public AliasTakenException(String alias) {
		super("Alias is already in use: " + alias);
	}
}
