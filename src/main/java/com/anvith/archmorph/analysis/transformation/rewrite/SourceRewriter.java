package com.anvith.archmorph.analysis.transformation.rewrite;

import com.anvith.archmorph.analysis.dependency.resolve.TypeContext;
import com.anvith.archmorph.analysis.dependency.resolve.TypeResolver;
import com.anvith.archmorph.analysis.registry.ProjectClassInfo;
import com.anvith.archmorph.analysis.registry.ProjectClassRegistry;
import com.anvith.archmorph.analysis.transformation.rewrite.RewriteResult.ChangeKind;
import com.anvith.archmorph.analysis.transformation.rewrite.RewriteResult.ImportChange;
import com.anvith.archmorph.common.exception.JavaParsingException;
import com.anvith.archmorph.parser.JavaParserService;
import com.anvith.archmorph.parser.ParsedSource;
import com.github.javaparser.Position;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic, AST-driven source rewriting.
 *
 * <ol>
 *   <li>change the package declaration</li>
 *   <li>rewrite imports through the complete {@code old → new} class map (never by module name)</li>
 *   <li>rewrite fully-qualified references (types, expressions, annotations, Javadoc)</li>
 *   <li>add imports that were implicit through the old same-package or wildcard visibility</li>
 *   <li>remove imports that became obsolete (class now in the same package, project wildcards)</li>
 *   <li>keep unrelated and external imports untouched</li>
 * </ol>
 *
 * <p><b>How:</b> JavaParser parses the file and the import-aware resolver decides <em>what</em> must change;
 * the AST node ranges decide exactly <em>where</em>. The edits are then applied to the original text, so
 * everything the transformation does not touch (formatting, comments, blank lines, line endings) is preserved
 * byte for byte. No regular expression is used to find code; the only pattern matching is for exact
 * fully-qualified names inside comments. (JavaParser's lexical-preserving printer was evaluated and not
 * used: it did not reliably reflect type changes inside local variable declarations.)</p>
 *
 * <p>Nested types move with their top-level type: {@code Outer.Inner} imports and references follow {@code Outer}.</p>
 */
@Service
public class SourceRewriter {

    private final JavaParserService javaParserService;

    public SourceRewriter(JavaParserService javaParserService) {
        this.javaParserService = javaParserService;
    }

    /** One import line. */
    private record Imp(String name, boolean isStatic, boolean asterisk) {
        String line() {
            return "import " + (isStatic ? "static " : "") + name + (asterisk ? ".*" : "") + ";";
        }

        String sortKey() {
            return name + (asterisk ? ".*" : "");
        }
    }

    /** Replace [start, end) of the original text. */
    private record Edit(int start, int end, String replacement) {
    }

    /**
     * @param source      original source text
     * @param newPackage  package declaration after the transformation (equal to the old one when the file is not moved)
     * @param classMap    old → new qualified names of moved top-level classes (identity entries optional)
     * @param registry    registry of all project types (old names)
     */
    public RewriteResult rewrite(String source, String newPackage, Map<String, String> classMap, ProjectClassRegistry registry) {
        ParsedSource parsed = javaParserService.parse(javaParserService.createParser(), source);
        if (!parsed.successful()) {
            throw new JavaParsingException("The file could not be parsed and cannot be rewritten.");
        }
        CompilationUnit cu = parsed.compilationUnit();
        LineIndex lines = new LineIndex(source);

        String oldPackage = cu.getPackageDeclaration().map(PackageDeclaration::getNameAsString).orElse("");
        TypeResolver resolver = new TypeResolver(registry, Set.of());
        List<ReferenceFinder.Reference> references = ReferenceFinder.find(cu, TypeContext.of(cu), resolver);

        List<ImportChange> importChanges = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<Edit> edits = new ArrayList<>();

        int qualified = qualifiedEdits(references, classMap, lines, edits);
        commentEdits(cu, classMap, source, lines, edits);
        ImportPlan importPlan = planImports(cu, newPackage, classMap, registry, references, importChanges, warnings);
        boolean packageChanged = !oldPackage.equals(newPackage);
        headerEdits(cu, source, lines, newPackage, packageChanged, importPlan, edits, warnings);

        String result = apply(source, edits, warnings);
        return new RewriteResult(result, !result.equals(source), List.copyOf(importChanges), qualified, packageChanged,
                List.copyOf(warnings));
    }

    // ------------------------------------------------------------------ qualified references

    private int qualifiedEdits(List<ReferenceFinder.Reference> references, Map<String, String> classMap, LineIndex lines,
                               List<Edit> edits) {
        int count = 0;
        for (ReferenceFinder.Reference reference : references) {
            String newTop = classMap.getOrDefault(reference.oldTopLevel(), reference.oldTopLevel());
            if (newTop.equals(reference.oldTopLevel())) {
                continue;
            }
            Node span = switch (reference.kind()) {
                case QUALIFIED_TYPE -> null;
                case QUALIFIED_EXPRESSION -> reference.node();
                case QUALIFIED_ANNOTATION -> ((AnnotationExpr) reference.node()).getName();
                case SIMPLE -> null;
            };
            if (reference.kind() == ReferenceFinder.Kind.QUALIFIED_TYPE) {
                // From the start of the scope to the end of the simple name; type arguments stay untouched.
                ClassOrInterfaceType type = (ClassOrInterfaceType) reference.node();
                Optional<Position> begin = type.getBegin();
                Optional<Position> end = type.getName().getEnd();
                if (begin.isPresent() && end.isPresent()) {
                    edits.add(new Edit(lines.offset(begin.get()), lines.offset(end.get()) + 1, newTop));
                    count++;
                }
            } else if (span != null && span.getBegin().isPresent() && span.getEnd().isPresent()) {
                String replacement = newTop + reference.remainder();
                edits.add(new Edit(lines.offset(span.getBegin().get()), lines.offset(span.getEnd().get()) + 1, replacement));
                count++;
            }
        }
        return count;
    }

    /** Exact fully-qualified names of moved classes inside comments, e.g. Javadoc {@code {@link ...}}. */
    private void commentEdits(CompilationUnit cu, Map<String, String> classMap, String source, LineIndex lines, List<Edit> edits) {
        for (Comment comment : cu.getAllComments()) {
            if (comment.getBegin().isEmpty() || comment.getEnd().isEmpty()) {
                continue;
            }
            int start = lines.offset(comment.getBegin().get());
            int end = lines.offset(comment.getEnd().get()) + 1;
            String text = source.substring(start, end);
            String updated = text;
            for (Map.Entry<String, String> entry : classMap.entrySet()) {
                if (!entry.getKey().equals(entry.getValue()) && updated.contains(entry.getKey())) {
                    updated = Pattern.compile("(?<![A-Za-z0-9_$.])" + Pattern.quote(entry.getKey()) + "(?![A-Za-z0-9_$])")
                            .matcher(updated).replaceAll(Matcher.quoteReplacement(entry.getValue()));
                }
            }
            if (!updated.equals(text)) {
                edits.add(new Edit(start, end, updated));
            }
        }
    }

    // ------------------------------------------------------------------ imports

    /** Final import groups plus the information needed to place them. */
    private record ImportPlan(List<List<Imp>> groups, List<Optional<Imp>> perImport, List<Imp> additions,
                              boolean changed, boolean commentsInside) {
    }

    private ImportPlan planImports(CompilationUnit cu, String newPackage, Map<String, String> classMap,
                                   ProjectClassRegistry registry, List<ReferenceFinder.Reference> references,
                                   List<ImportChange> changes, List<String> warnings) {

        Set<String> needed = new TreeSet<>();
        for (ReferenceFinder.Reference reference : references) {
            if (reference.kind() == ReferenceFinder.Kind.SIMPLE) {
                needed.add(classMap.getOrDefault(reference.oldTopLevel(), reference.oldTopLevel()));
            }
        }

        // Original groups: consecutive imports without a blank line between them.
        List<List<ImportDeclaration>> originalGroups = new ArrayList<>();
        int previousEnd = -10;
        for (ImportDeclaration imp : cu.getImports()) {
            int begin = imp.getBegin().map(p -> p.line).orElse(0);
            if (originalGroups.isEmpty() || begin - previousEnd > 1) {
                originalGroups.add(new ArrayList<>());
            }
            originalGroups.getLast().add(imp);
            previousEnd = imp.getEnd().map(p -> p.line).orElse(begin);
        }

        boolean commentsInside = false;
        if (!cu.getImports().isEmpty()) {
            int first = cu.getImports().get(0).getBegin().map(p -> p.line).orElse(0);
            int last = cu.getImports().get(cu.getImports().size() - 1).getEnd().map(p -> p.line).orElse(0);
            commentsInside = cu.getAllComments().stream().anyMatch(c ->
                    c.getBegin().map(p -> p.line >= first && p.line <= last).orElse(false));
        }

        Set<String> present = new LinkedHashSet<>();
        List<List<Imp>> groups = new ArrayList<>();
        List<Boolean> groupSorted = new ArrayList<>();
        List<Optional<Imp>> perImport = new ArrayList<>();
        boolean changed = false;

        for (List<ImportDeclaration> originalGroup : originalGroups) {
            List<Imp> rewritten = new ArrayList<>();
            boolean sorted = isSorted(originalGroup);
            for (ImportDeclaration imp : originalGroup) {
                String name = imp.getNameAsString();
                Optional<Imp> result;
                if (imp.isStatic()) {
                    result = rewriteStatic(imp, name, classMap, registry, changes);
                } else if (imp.isAsterisk()) {
                    result = rewriteWildcard(name, classMap, registry, changes);
                } else {
                    result = rewriteSingle(name, newPackage, classMap, registry, changes, present);
                }
                perImport.add(result);
                if (result.isEmpty()) {
                    changed = true;
                    continue;
                }
                changed |= !result.get().name().equals(name);
                rewritten.add(result.get());
            }
            if (sorted) {
                rewritten.sort(Comparator.comparing(Imp::sortKey));
            }
            groups.add(rewritten);
            groupSorted.add(sorted);
        }

        // Imports for classes that used to be visible without one (same package or wildcard).
        List<Imp> additions = new ArrayList<>();
        for (String type : needed) {
            String pkg = packageOf(type);
            if (pkg.equals(newPackage)) {
                continue;
            }
            if (pkg.isEmpty()) {
                warnings.add("Class " + type + " is in the default package and cannot be imported.");
                continue;
            }
            if (!present.contains(type)) {
                additions.add(new Imp(type, false, false));
                present.add(type);
                changes.add(new ImportChange(ChangeKind.ADDED, null, type));
            }
        }
        if (!additions.isEmpty()) {
            changed = true;
            addToRegularGroup(groups, groupSorted, originalGroups, additions);
        }
        groups.removeIf(List::isEmpty);
        return new ImportPlan(groups, perImport, additions, changed, commentsInside);
    }

    /** Add to the last regular-import group (sorted in place when that group is sorted); create one if absent. */
    private void addToRegularGroup(List<List<Imp>> groups, List<Boolean> groupSorted,
                                   List<List<ImportDeclaration>> originalGroups, List<Imp> additions) {
        int target = -1;
        for (int i = 0; i < groups.size(); i++) {
            if (originalGroups.get(i).stream().anyMatch(d -> !d.isStatic())) {
                target = i;
            }
        }
        if (target < 0) {
            List<Imp> group = new ArrayList<>(additions);
            group.sort(Comparator.comparing(Imp::sortKey));
            groups.add(group);
            groupSorted.add(true);
            return;
        }
        List<Imp> group = groups.get(target);
        group.addAll(additions);
        if (groupSorted.get(target)) {
            group.sort(Comparator.comparing(Imp::sortKey));
        }
    }

    private Optional<Imp> rewriteSingle(String name, String newPackage, Map<String, String> classMap,
                                        ProjectClassRegistry registry, List<ImportChange> changes, Set<String> present) {
        Optional<ProjectClassInfo> info = registry.findByQualifiedName(name);
        if (info.isEmpty()) {
            present.add(name);
            return Optional.of(new Imp(name, false, false));
        }
        String oldTop = info.get().getTopLevelQualifiedName();
        String newTop = classMap.getOrDefault(oldTop, oldTop);
        String newName = newTop + name.substring(oldTop.length());
        boolean topLevelImport = name.equals(oldTop);
        if (topLevelImport && packageOf(newName).equals(newPackage)) {
            changes.add(new ImportChange(ChangeKind.REMOVED_OBSOLETE, name, null));
            return Optional.empty();
        }
        if (!newName.equals(name)) {
            changes.add(new ImportChange(ChangeKind.REWRITTEN, name, newName));
        }
        present.add(newName);
        return Optional.of(new Imp(newName, false, false));
    }

    private Optional<Imp> rewriteWildcard(String name, Map<String, String> classMap, ProjectClassRegistry registry,
                                          List<ImportChange> changes) {
        if (!registry.classesInPackage(name).isEmpty()) {
            // Project package: explicit imports (added separately) replace it, since the classes may now live in several packages.
            changes.add(new ImportChange(ChangeKind.REMOVED_WILDCARD, name + ".*", null));
            return Optional.empty();
        }
        Optional<ProjectClassInfo> owner = registry.findByQualifiedName(name);
        if (owner.isPresent()) {
            String oldTop = owner.get().getTopLevelQualifiedName();
            String newName = classMap.getOrDefault(oldTop, oldTop) + name.substring(oldTop.length());
            if (!newName.equals(name)) {
                changes.add(new ImportChange(ChangeKind.REWRITTEN, name + ".*", newName + ".*"));
                return Optional.of(new Imp(newName, false, true));
            }
        }
        return Optional.of(new Imp(name, false, true));
    }

    private Optional<Imp> rewriteStatic(ImportDeclaration imp, String name, Map<String, String> classMap,
                                        ProjectClassRegistry registry, List<ImportChange> changes) {
        String owner = imp.isAsterisk() ? name : name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name;
        String member = imp.isAsterisk() ? "" : name.substring(owner.length());
        Optional<ProjectClassInfo> info = registry.findByQualifiedName(owner);
        if (info.isPresent()) {
            String oldTop = info.get().getTopLevelQualifiedName();
            String newOwner = classMap.getOrDefault(oldTop, oldTop) + owner.substring(oldTop.length());
            if (!newOwner.equals(owner)) {
                changes.add(new ImportChange(ChangeKind.REWRITTEN,
                        name + (imp.isAsterisk() ? ".*" : ""), newOwner + member + (imp.isAsterisk() ? ".*" : "")));
                return Optional.of(new Imp(newOwner + member, true, imp.isAsterisk()));
            }
        }
        return Optional.of(new Imp(name, true, imp.isAsterisk()));
    }

    // ------------------------------------------------------------------ package declaration + import block

    private void headerEdits(CompilationUnit cu, String source, LineIndex lines, String newPackage, boolean packageChanged,
                             ImportPlan plan, List<Edit> edits, List<String> warnings) {
        Optional<PackageDeclaration> pkg = cu.getPackageDeclaration();
        boolean hadImports = !cu.getImports().isEmpty();
        String block = plan.groups().stream()
                .map(group -> String.join("\n", group.stream().map(Imp::line).toList()))
                .reduce((a, b) -> a + "\n\n" + b).orElse("");

        // 1. package declaration
        String prefix = "";
        if (packageChanged) {
            if (pkg.isPresent() && !newPackage.isEmpty()) {
                var name = pkg.get().getName();
                edits.add(new Edit(lines.offset(name.getBegin().orElseThrow()), lines.offset(name.getEnd().orElseThrow()) + 1, newPackage));
            } else if (pkg.isPresent()) {
                int start = lines.offset(pkg.get().getBegin().orElseThrow());
                int end = skipLineBreaks(source, lines.offset(pkg.get().getEnd().orElseThrow()) + 1);
                edits.add(new Edit(start, end, ""));
            } else {
                prefix = "package " + newPackage + ";\n\n";
            }
        }

        // 2. import block
        if (!plan.changed()) {
            if (!prefix.isEmpty()) {
                int at = firstCodeOffset(cu, lines);
                edits.add(new Edit(at, at, prefix));
            }
            return;
        }
        if (hadImports && plan.commentsInside()) {
            importLineEdits(cu, source, lines, plan, edits);
            warnings.add("The import block contains comments; imports were updated line by line.");
            if (!prefix.isEmpty()) {
                int at = firstCodeOffset(cu, lines);
                edits.add(new Edit(at, at, prefix));
            }
            return;
        }
        if (hadImports) {
            int start = lines.offset(cu.getImports().get(0).getBegin().orElseThrow());
            int end = lines.offset(cu.getImports().get(cu.getImports().size() - 1).getEnd().orElseThrow()) + 1;
            if (block.isEmpty()) {
                end = skipLineBreaks(source, end);
            }
            edits.add(new Edit(start, end, block));
            if (!prefix.isEmpty()) {
                edits.add(new Edit(start, start, prefix));
            }
            return;
        }
        if (block.isEmpty()) {
            if (!prefix.isEmpty()) {
                int at = firstCodeOffset(cu, lines);
                edits.add(new Edit(at, at, prefix));
            }
            return;
        }
        if (pkg.isPresent() && !(packageChanged && newPackage.isEmpty())) {
            int end = lines.offset(pkg.get().getEnd().orElseThrow()) + 1;
            edits.add(new Edit(end, end, "\n\n" + block));
        } else {
            int at = firstCodeOffset(cu, lines);
            edits.add(new Edit(at, at, prefix + block + "\n\n"));
        }
    }

    /** Fallback when comments sit between imports: rewrite each import on its own line, append additions. */
    private void importLineEdits(CompilationUnit cu, String source, LineIndex lines, ImportPlan plan, List<Edit> edits) {
        int lastRegularEnd = -1;
        for (int i = 0; i < cu.getImports().size(); i++) {
            ImportDeclaration imp = cu.getImports().get(i);
            int start = lines.offset(imp.getBegin().orElseThrow());
            int end = lines.offset(imp.getEnd().orElseThrow()) + 1;
            Optional<Imp> result = plan.perImport().get(i);
            if (result.isEmpty()) {
                edits.add(new Edit(start, skipLineBreaks(source, end, 1), ""));
            } else {
                edits.add(new Edit(start, end, result.get().line()));
                if (!imp.isStatic()) {
                    lastRegularEnd = end;
                }
            }
        }
        if (!plan.additions().isEmpty()) {
            int at = lastRegularEnd >= 0 ? lastRegularEnd
                    : lines.offset(cu.getImports().get(cu.getImports().size() - 1).getEnd().orElseThrow()) + 1;
            StringBuilder text = new StringBuilder();
            plan.additions().forEach(a -> text.append('\n').append(a.line()));
            edits.add(new Edit(at, at, text.toString()));
        }
    }

    private int firstCodeOffset(CompilationUnit cu, LineIndex lines) {
        int offset = Integer.MAX_VALUE;
        for (ImportDeclaration imp : cu.getImports()) {
            offset = Math.min(offset, lines.offset(imp.getBegin().orElseThrow()));
        }
        for (TypeDeclaration<?> type : cu.getTypes()) {
            offset = Math.min(offset, lines.offset(type.getBegin().orElseThrow()));
            if (type.getComment().isPresent() && type.getComment().get().getBegin().isPresent()) {
                offset = Math.min(offset, lines.offset(type.getComment().get().getBegin().get()));
            }
        }
        return offset == Integer.MAX_VALUE ? 0 : offset;
    }

    // ------------------------------------------------------------------ edit application

    private String apply(String source, List<Edit> edits, List<String> warnings) {
        List<Edit> ordered = new ArrayList<>(edits);
        // Descending start; for equal starts apply the later-registered insertion first so text keeps registration order.
        ordered.sort(Comparator.comparingInt(Edit::start).thenComparingInt(Edit::end).reversed());
        StringBuilder text = new StringBuilder(source);
        int limit = Integer.MAX_VALUE;
        for (Edit edit : ordered) {
            if (edit.end() > limit) {
                warnings.add("Overlapping rewrite skipped near offset " + edit.start() + ".");
                continue;
            }
            text.replace(edit.start(), edit.end(), edit.replacement());
            limit = edit.start();
        }
        return text.toString();
    }

    private static int skipLineBreaks(String source, int offset) {
        return skipLineBreaks(source, offset, Integer.MAX_VALUE);
    }

    private static int skipLineBreaks(String source, int offset, int maxLines) {
        int position = offset;
        int newlines = 0;
        while (position < source.length() && newlines < maxLines
                && (source.charAt(position) == '\n' || source.charAt(position) == '\r')) {
            if (source.charAt(position) == '\n') {
                newlines++;
            }
            position++;
        }
        return position;
    }

    private static boolean isSorted(List<ImportDeclaration> imports) {
        String previous = null;
        for (ImportDeclaration imp : imports) {
            String key = imp.getNameAsString() + (imp.isAsterisk() ? ".*" : "");
            if (previous != null && previous.compareTo(key) > 0) {
                return false;
            }
            previous = key;
        }
        return true;
    }

    static String packageOf(String qualifiedName) {
        int dot = qualifiedName.lastIndexOf('.');
        return dot < 0 ? "" : qualifiedName.substring(0, dot);
    }

    /** Maps JavaParser (line, column) positions to character offsets of a text. */
    private static final class LineIndex {
        private final int[] lineStarts;

        LineIndex(String text) {
            List<Integer> starts = new ArrayList<>();
            starts.add(0);
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) == '\n') {
                    starts.add(i + 1);
                }
            }
            lineStarts = starts.stream().mapToInt(Integer::intValue).toArray();
        }

        /** Offset of the character at the (1-based) position. */
        int offset(Position position) {
            return lineStarts[position.line - 1] + position.column - 1;
        }
    }
}
