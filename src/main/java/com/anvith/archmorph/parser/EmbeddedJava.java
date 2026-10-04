package com.anvith.archmorph.parser;

import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.StringLiteralExpr;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Java code embedded in annotation strings, as MapStruct does:
 * {@code @Mapping(target = "total", expression = "java(com.demo.util.Money.sum(order))")}. MapStruct copies the code
 * into the generated mapper, so the classes it names are real dependencies and must follow a move.
 */
public final class EmbeddedJava {

    private static final Set<String> ATTRIBUTES = Set.of("expression", "defaultExpression", "conditionExpression");
    private static final Pattern DOTTED = Pattern.compile("(?<![A-Za-z0-9_$.])[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)+");

    private EmbeddedJava() {
    }

    /** True for a {@code java(...)} string given to an {@code expression}-like annotation attribute. */
    public static boolean isEmbeddedJava(StringLiteralExpr literal) {
        return literal.getParentNode().orElse(null) instanceof MemberValuePair pair
                && ATTRIBUTES.contains(pair.getNameAsString())
                && literal.getValue().trim().startsWith("java(");
    }

    /** Dotted names in the code, e.g. {@code com.demo.util.Money.sum} for {@code java(com.demo.util.Money.sum(o))}. */
    public static List<String> dottedNames(String code) {
        List<String> names = new ArrayList<>();
        Matcher matcher = DOTTED.matcher(code);
        while (matcher.find()) {
            names.add(matcher.group());
        }
        return names;
    }

    /** {@code a.b.C.d} → {@code [a.b.C.d, a.b.C, a.b]}: candidates for the class a dotted name refers to. */
    public static List<String> prefixes(String dotted) {
        List<String> result = new ArrayList<>();
        String current = dotted;
        while (current.contains(".")) {
            result.add(current);
            current = current.substring(0, current.lastIndexOf('.'));
        }
        return result;
    }
}
