package com.demo;

import org.springframework.stereotype.Component;

@Component
public class RequestProcessor {
    private final DataHelper helper;
    private final Manager manager;

    public RequestProcessor(DataHelper helper, Manager manager) {
        this.helper = helper;
        this.manager = manager;
    }

    public String process(String input) {
        return manager.run(helper.clean(input));
    }
}
