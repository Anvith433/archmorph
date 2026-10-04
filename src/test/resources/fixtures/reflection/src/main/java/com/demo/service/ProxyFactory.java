package com.demo.service;

import java.lang.reflect.Proxy;
import org.springframework.stereotype.Service;

@Service
public class ProxyFactory {

    public Runnable create() {
        return (Runnable) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Runnable.class},
                (proxy, method, args) -> null);
    }
}
