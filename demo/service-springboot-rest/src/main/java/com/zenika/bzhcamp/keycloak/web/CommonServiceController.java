package com.zenika.bzhcamp.keycloak.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Collections;
import java.util.Map;

@RestController
public class CommonServiceController {

    private static final Logger logger = LoggerFactory.getLogger(CommonServiceController.class);

	@GetMapping(value = "/public", produces = MediaType.APPLICATION_JSON_VALUE)
	@CrossOrigin(origins = "*")
	public Map<String, String> handlePublicRequest(HttpServletRequest request) {
		logger.info("new request");		
		return Collections.singletonMap("message", "public");
	}

}
