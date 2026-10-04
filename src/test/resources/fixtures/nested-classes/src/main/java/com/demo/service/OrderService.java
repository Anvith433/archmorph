package com.demo.service;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    public enum Status { NEW, PAID }

    public record Summary(long id, Status status) { }

    public interface Listener {
        void onChange(Summary summary);
    }

    public static class Config {
        public int retries = 3;
    }

    private final List<Listener> listeners = new ArrayList<>();
    private final PriceCalc calc = new PriceCalc();

    public Summary summarize(long id) {
        double price = calc.total(10.0);
        listeners.forEach(l -> l.onChange(new Summary(id, Status.NEW)));
        return new Summary(id, price > 0 ? Status.NEW : Status.PAID);
    }
}
