package com.demo.service;

public class PriceCalc {

    public double total(double net) {
        return PriceRounding.round(net * 1.2);
    }
}

class PriceRounding {

    static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
