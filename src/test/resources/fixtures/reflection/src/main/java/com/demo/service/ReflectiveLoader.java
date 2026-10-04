package com.demo.service;

import org.springframework.stereotype.Service;

@Service
public class ReflectiveLoader {

    public Object load() throws Exception {
        Class<?> type = Class.forName("com.demo.plugin.PluginService");
        return type.getDeclaredConstructor().newInstance();
    }
}
