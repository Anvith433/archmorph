package com.demo.service;

import static com.demo.common.Constants.CURRENCY;
import static com.demo.common.Constants.*;
import static java.util.Objects.requireNonNull;

public class Billing {
    public String currency() {
        return requireNonNull(CURRENCY) + SCALE;
    }
}
