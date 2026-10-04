package com.demo.util;

import java.util.Locale;

/*
 * Nothing here refers to a moved class; the file must stay byte-identical.
 */
public final class Strings {
    private Strings() {
    }

    public static String upper(String value) {
        return value.toUpperCase(Locale.ROOT);
    }
}
