package com.demo;

import org.springframework.stereotype.Component;

@Component
public class Manager {
    private final DataHelper helper;

    public Manager(DataHelper helper) {
        this.helper = helper;
    }

    public String run(String value) {
        return helper.clean(value);
    }
}
