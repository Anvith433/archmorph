package com.anvith.archmorph.common.util;

import java.util.UUID;

public final class ProjectIdGenerator {

    private ProjectIdGenerator() {
        // Prevent instantiation
    }

    public static String generate() {
        return "PROJECT-" +
                UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .substring(0, 8)
                        .toUpperCase();
    }
}