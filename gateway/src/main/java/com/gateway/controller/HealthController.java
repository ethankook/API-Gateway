package com.gateway.controller;


import org.springframework.web.bind.annotation.GetMapping;

public class HealthController {

    @GetMapping("/health")
    public String health() {
        return "OK";
    }
}
