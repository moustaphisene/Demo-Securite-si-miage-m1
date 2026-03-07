package com.zenika.bzhcamp.keycloak.web;

import com.zenika.bzhcamp.keycloak.service.PlanetsService;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class PlanetsServiceController {
    private static final Logger logger = LoggerFactory.getLogger(PlanetsServiceController.class);

	@Autowired
	private PlanetsService planetsService;

	@GetMapping(value = "/planets", produces = MediaType.APPLICATION_JSON_VALUE)
	public List<String> handlePlanetsRequest(HttpServletRequest request) {
		logger.info("new request");
		logger.info(request.getHeader("Authorization"));
		return planetsService.getPlanets();
	}
}
