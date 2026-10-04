package com.demo;

import org.springframework.stereotype.Component;

@Component
public class DataHelper {
    public String clean(String input) {
        return input == null ? "" : input.trim();
    }
}
