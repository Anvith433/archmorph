package com.anvith.archmorph.parser;

import java.util.List;

/** A method or constructor parameter. */
public record ParameterInfo(String name, String type, List<String> annotations) {
}
