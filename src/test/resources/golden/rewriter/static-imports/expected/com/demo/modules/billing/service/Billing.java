package com.demo.modules.billing.service;

import static com.demo.shared.common.Constants.CURRENCY;
import static com.demo.shared.common.Constants.*;
import static java.util.Objects.requireNonNull;

public class Billing {
    public String currency() {
        return requireNonNull(CURRENCY) + SCALE;
    }
}
