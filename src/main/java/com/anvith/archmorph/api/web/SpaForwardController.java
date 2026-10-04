package com.anvith.archmorph.api.web;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;

/**
 * Serves the single-page frontend for client-side routes when the built UI is bundled in the jar
 * ({@code classpath:/static/index.html}). During development the UI runs on the Vite dev server instead.
 */
@Controller
public class SpaForwardController {

    private static final ClassPathResource INDEX = new ClassPathResource("static/index.html");

    @GetMapping({"/", "/projects/new", "/projects/{id}", "/projects/{id}/{view}"})
    public String forward() {
        if (!INDEX.exists()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return "forward:/index.html";
    }
}
