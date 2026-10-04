package com.clinic.rest.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RootRestController {
    @GetMapping("/")
    public String root() {
        return "redirect:/swagger-ui.html";
    }
}
