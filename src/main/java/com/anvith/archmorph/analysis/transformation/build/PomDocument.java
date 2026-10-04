package com.anvith.archmorph.analysis.transformation.build;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Minimal, position-preserving view of a {@code pom.xml}: element paths with the offsets of their start and end
 * tags, so content can be inserted without reformatting the file. Comments, CDATA, processing instructions and
 * doctype declarations are skipped; entities are not expanded and nothing is ever resolved or fetched.
 */
public final class PomDocument {

    /** One element occurrence: {@code openEnd} is just after its start tag, {@code closeStart} at its end tag. */
    public record Element(String path, int openStart, int openEnd, int closeStart, boolean selfClosing) {
    }

    private final String text;
    private final List<Element> elements = new ArrayList<>();

    private PomDocument(String text) {
        this.text = text;
    }

    public static PomDocument parse(String text) {
        PomDocument document = new PomDocument(text);
        document.scan();
        return document;
    }

    public String text() {
        return text;
    }

    public List<Element> all(String path) {
        return elements.stream().filter(e -> e.path().equals(path)).toList();
    }

    public Optional<Element> first(String path) {
        return elements.stream().filter(e -> e.path().equals(path)).findFirst();
    }

    /** Trimmed text content of the first element at {@code path} (for leaf elements such as versions). */
    public Optional<String> value(String path) {
        return first(path).filter(e -> !e.selfClosing()).map(e -> text.substring(e.openEnd(), e.closeStart()).trim());
    }

    /** Text content of a child element of a given element occurrence. */
    public Optional<String> childValue(Element parent, String child) {
        String path = parent.path() + "/" + child;
        return elements.stream()
                .filter(e -> e.path().equals(path) && e.openStart() > parent.openStart() && e.closeStart() < parent.closeStart())
                .findFirst()
                .filter(e -> !e.selfClosing())
                .map(e -> text.substring(e.openEnd(), e.closeStart()).trim());
    }

    /** Leading whitespace of the line that contains {@code offset}. */
    public String indentationAt(int offset) {
        int lineStart = text.lastIndexOf('\n', Math.max(0, offset - 1)) + 1;
        int i = lineStart;
        while (i < text.length() && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) {
            i++;
        }
        return text.substring(lineStart, i);
    }

    public String lineSeparator() {
        return text.contains("\r\n") ? "\r\n" : "\n";
    }

    private void scan() {
        Deque<String> stack = new ArrayDeque<>();
        Deque<int[]> opens = new ArrayDeque<>();
        Map<Integer, Integer> indexOfOpen = new HashMap<>();
        int i = 0;
        int length = text.length();
        while (i < length) {
            if (text.startsWith("<!--", i)) {
                i = skipTo(i, "-->");
            } else if (text.startsWith("<![CDATA[", i)) {
                i = skipTo(i, "]]>");
            } else if (text.startsWith("<?", i)) {
                i = skipTo(i, "?>");
            } else if (text.startsWith("<!", i)) {
                i = skipTo(i, ">");
            } else if (text.startsWith("</", i)) {
                int end = text.indexOf('>', i);
                if (end < 0 || stack.isEmpty()) {
                    return;
                }
                String path = String.join("/", stack.reversed());
                int[] open = opens.pop();
                stack.pop();
                elements.set(indexOfOpen.get(open[0]), new Element(path, open[0], open[1], i, false));
                i = end + 1;
            } else if (text.charAt(i) == '<') {
                int end = tagEnd(i);
                if (end < 0) {
                    return;
                }
                String name = tagName(i + 1);
                boolean selfClosing = text.charAt(end - 1) == '/';
                stack.push(name);
                String path = String.join("/", stack.reversed());
                indexOfOpen.put(i, elements.size());
                elements.add(new Element(path, i, end + 1, end + 1, selfClosing));
                if (selfClosing) {
                    stack.pop();
                } else {
                    opens.push(new int[]{i, end + 1});
                }
                i = end + 1;
            } else {
                i++;
            }
        }
    }

    private int skipTo(int from, String terminator) {
        int end = text.indexOf(terminator, from);
        return end < 0 ? text.length() : end + terminator.length();
    }

    /** End of a start tag, honouring quoted attribute values. */
    private int tagEnd(int from) {
        char quote = 0;
        for (int i = from + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '>') {
                return i;
            }
        }
        return -1;
    }

    private String tagName(int from) {
        int i = from;
        while (i < text.length() && !Character.isWhitespace(text.charAt(i)) && text.charAt(i) != '>' && text.charAt(i) != '/') {
            i++;
        }
        String name = text.substring(from, i);
        int colon = name.indexOf(':');
        return colon >= 0 ? name.substring(colon + 1) : name;
    }
}
