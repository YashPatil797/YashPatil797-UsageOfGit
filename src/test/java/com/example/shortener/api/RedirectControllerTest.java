package com.example.shortener.api;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import com.example.shortener.service.UrlService;

@WebMvcTest(RedirectController.class)
@ContextConfiguration(classes = { RedirectController.class, GlobalExceptionHandler.class })
class RedirectControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private UrlService urlService;

	@ParameterizedTest
	@ValueSource(strings = { "apiv2", "actuator1", "1000" })
	void redirectsCodesThatOnlyStartWithReservedWords(String code) throws Exception {
		when(urlService.resolve(code)).thenReturn("https://example.com/" + code);

		mockMvc.perform(get("/" + code))
			.andExpect(status().isFound())
			.andExpect(header().string("Location", "https://example.com/" + code));

		verify(urlService).resolve(code);
	}

	@ParameterizedTest
	@ValueSource(strings = { "/api", "/actuator", "/abcdefghijklmnopqrstuvwxyz12345" })
	void returnsNotFoundWithoutResolvingUnroutablePaths(String path) throws Exception {
		mockMvc.perform(get(path))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error").value("not_found"));

		verifyNoInteractions(urlService);
	}
}
