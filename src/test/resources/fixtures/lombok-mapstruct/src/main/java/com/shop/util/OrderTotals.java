package com.shop.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class OrderTotals {
    private OrderTotals() {
    }

    public static String format(BigDecimal amount) {
        return amount == null ? "0.00" : amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
