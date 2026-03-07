package com.zenika.bzhcamp.keycloak.appspringboot.web;

import java.security.Principal;
import java.util.List;

import com.zenika.bzhcamp.keycloak.appspringboot.service.PlanetsService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class PlanetsController {

    private static final Logger logger = LoggerFactory.getLogger(PlanetsController.class);

    private final PlanetsService planetsService;
    private final HttpServletRequest request;

    /**
     * Injection par constructeur : bonne pratique recommandée par Spring.
     * Avantages : les dépendances sont explicites, la classe est testable sans conteneur Spring,
     * et les champs peuvent être déclarés final (immuabilité).
     */
    public PlanetsController(PlanetsService planetsService, HttpServletRequest request) {
        this.planetsService = planetsService;
        this.request = request;
    }

    @RequestMapping(value = "/")
    public String landing() {
        return "landing";
    }

    @RequestMapping(value = "/logout")
    public String handleLogout(HttpServletRequest request) throws ServletException {
        request.logout();
        return "redirect:/";
    }

    @RequestMapping(value = "/about")
    public String about() {
        return "about";
    }

    @GetMapping("favicon.ico")
    @ResponseBody
    void returnNoFavicon() {
    }

    /**
     * Endpoint protégé par rôle Keycloak.
     *
     * @PreAuthorize("hasRole('user')") vérifie que l'utilisateur possède le rôle "user"
     * dans Keycloak (mappé en "ROLE_user" par userAuthoritiesMapperForKeycloak).
     *
     * Pour tester avec un rôle "admin" uniquement : @PreAuthorize("hasRole('admin')")
     * Pour plusieurs rôles : @PreAuthorize("hasAnyRole('admin', 'user')")
     *
     * Note : ce rôle doit être créé dans le realm Keycloak et assigné à l'utilisateur.
     */
    @RequestMapping(value = "/planets")
    @PreAuthorize("hasRole('user')")
    public String handlePlanetsRequest(Principal principal, Model model) {
        logger.info("User {} accessed /planets", principal.getName());
        model.addAttribute("principal", principal);

        List<String> planets = planetsService.getPlanets();
        model.addAttribute("planets", planets);

        return "planets";
    }
}