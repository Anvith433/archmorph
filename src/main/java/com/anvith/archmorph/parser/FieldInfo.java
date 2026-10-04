package com.anvith.archmorph.parser;

import java.util.List;

/** A field declared by a type. */
public record FieldInfo(
        String name,
        String type,
        List<String> annotations,
        List<String> modifiers,
        int line) {
}
