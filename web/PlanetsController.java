package com.zenika.bzhcamp.keycloak.appspringboot.web;

import java.security.Principal;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import com.zenika.bzhcamp.keycloak.appspringboot.service.PlanetsService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;

@Controller
public class PlanetsController {

        private static final Logger logger = LoggerFactory.getLogger(PlanetsController.class);

        @Autowired
        private PlanetsService planetsService;

        @Autowired
        private HttpServletRequest request;

        public PlanetsController() {
        }

        @RequestMapping(value = "/")
        public String landing() throws ServletException {
                return "landing";
        }

        @RequestMapping(value = "/logout")
        public String handleLogoutt(HttpServletRequest request) throws ServletException {
                request.logout();
                return "redirect:/";
        }

        @RequestMapping(value = "/about")
        public String about() throws ServletException {
                return "about";
        }

        @GetMapping("favicon.ico")
        @ResponseBody
        void returnNoFavicon() {
        }

        @RequestMapping(value = "/planets")
        public String handleCustomersRequest(
                        Principal principal,
                        Model model) {
                logger.info(principal.toString());
                model.addAttribute("principal", principal);

                List<String> planets = planetsService.getPlanets();
                model.addAttribute("planets", planets);

                return "planets";
        }
}
