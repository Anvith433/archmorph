package com.anvith.archmorph.parser;

import java.util.List;

/** A method or constructor declared by a type. */
public record MethodInfo(
        String name,
        String returnType,
        List<ParameterInfo> parameters,
        List<String> annotations,
        List<String> modifiers,
        List<String> typeParameters,
        int line,
        boolean constructor) {
}
