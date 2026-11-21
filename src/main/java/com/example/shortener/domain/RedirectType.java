package com.example.shortener.domain;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;

public enum RedirectType {

	TEMPORARY,
	PERMANENT;

	@JsonValue
	public String apiValue() {
		return name().toLowerCase(Locale.ROOT);
	}
}
