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
import com.github.javaparser.JavaParser;
import com.github.javaparser.Position;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.comments.BlockComment;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.comments.JavadocComment;
import com.github.javaparser.ast.comments.LineComment;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic, AST-based source rewriting.
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
 * <p>Node edits (package, qualified references, comments) use JavaParser's lexical-preserving printer, so
 * formatting and comments survive. The import block is regenerated explicitly from the AST, keeping its
 * original grouping and ordering; the lexical printer alone does not produce clean whitespace for
 * inserted or removed imports. Nested types move with their top-level type.</p>
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

    /**
     * @param source      original source text
     * @param newPackage  package declaration after the transformation (equal to the old one when the file is not moved)
     * @param classMap    old → new qualified names of moved top-level classes (identity entries optional)
     * @param registry    registry of all project types (old names)
     */
    public RewriteResult rewrite(String source, String newPackage, Map<String, String> classMap, ProjectClassRegistry registry) {
        JavaParser parser = javaParserService.createParser();
        ParsedSource parsed = javaParserService.parse(parser, source);
        if (!parsed.successful()) {
            throw new JavaParsingException("The file could not be parsed and cannot be rewritten.");
        }
        CompilationUnit cu = parsed.compilationUnit();
        LexicalPreservingPrinter.setup(cu);

        String oldPackage = cu.getPackageDeclaration().map(PackageDeclaration::getNameAsString).orElse("");
        TypeContext context = TypeContext.of(cu);
        TypeResolver resolver = new TypeResolver(registry, Set.of());
        List<ReferenceFinder.Reference> references = ReferenceFinder.find(cu, context, resolver);

        List<ImportChange> importChanges = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Plan the imports from the unmodified AST, then edit nodes, print, and splice the import block.
        ImportPlan importPlan = planImports(cu, newPackage, classMap, registry, references, importChanges, warnings);

        int qualified = rewriteQualified(references, classMap);
        rewriteComments(cu, classMap);
        boolean packageChanged = rewritePackage(cu, oldPackage, newPackage);

        String printed = LexicalPreservingPrinter.print(cu);
        String result = spliceImports(printed, importPlan, warnings);
        return new RewriteResult(result, !result.equals(source), List.copyOf(importChanges), qualified, packageChanged,
                List.copyOf(warnings));
    }

    // ------------------------------------------------------------------ package

    private boolean rewritePackage(CompilationUnit cu, String oldPackage, String newPackage) {
        if (oldPackage.equals(newPackage)) {
            return false;
        }
        if (newPackage.isEmpty()) {
            cu.removePackageDeclaration();
        } else if (cu.getPackageDeclaration().isPresent()) {
            // Renamed in place: keeps the whitespace and annotations around the declaration.
            cu.getPackageDeclaration().get().setName(newPackage);
        } else {
            cu.setPackageDeclaration(newPackage);
        }
        return true;
    }

    // ------------------------------------------------------------------ qualified references

    private int rewriteQualified(List<ReferenceFinder.Reference> references, Map<String, String> classMap) {
        int count = 0;
        for (ReferenceFinder.Reference reference : references) {
            String newTop = classMap.getOrDefault(reference.oldTopLevel(), reference.oldTopLevel());
            if (newTop.equals(reference.oldTopLevel())) {
                continue;
            }
            switch (reference.kind()) {
                case QUALIFIED_TYPE -> {
                    ClassOrInterfaceType type = (ClassOrInterfaceType) reference.node();
                    String pkg = packageOf(newTop);
                    if (pkg.isEmpty()) {
                        type.removeScope();
                    } else {
                        type.setScope(typeScope(pkg));
                    }
                    count++;
                }
                case QUALIFIED_EXPRESSION -> {
                    FieldAccessExpr access = (FieldAccessExpr) reference.node();
                    access.replace(expression(newTop));
                    count++;
                }
                case QUALIFIED_ANNOTATION -> {
                    AnnotationExpr annotation = (AnnotationExpr) reference.node();
                    annotation.setName(StaticJavaParser.parseName(newTop + reference.remainder()));
                    count++;
                }
                default -> {
                    // SIMPLE references keep their spelling; imports are handled separately.
                }
            }
        }
        return count;
    }

    private ClassOrInterfaceType typeScope(String pkg) {
        ClassOrInterfaceType scope = null;
        for (String segment : pkg.split("\\.")) {
            scope = new ClassOrInterfaceType(scope, segment);
        }
        return scope;
    }

    private Expression expression(String qualified) {
        String[] segments = qualified.split("\\.");
        Expression expression = new NameExpr(segments[0]);
        for (int i = 1; i < segments.length; i++) {
            expression = new FieldAccessExpr(expression, segments[i]);
        }
        return expression;
    }

    /**
     * Rewrites exact fully-qualified occurrences of moved classes inside comments (Javadoc
     * {@code {@link ...}}). Comments are replaced as a whole because the lexical printer does not
     * track in-place content changes.
     */
    private void rewriteComments(CompilationUnit cu, Map<String, String> classMap) {
        for (Comment comment : cu.getAllContainedComments()) {
            String content = comment.getContent();
            String updated = content;
            for (Map.Entry<String, String> entry : classMap.entrySet()) {
                if (!entry.getKey().equals(entry.getValue()) && updated.contains(entry.getKey())) {
                    updated = Pattern.compile("(?<![A-Za-z0-9_$.])" + Pattern.quote(entry.getKey()) + "(?![A-Za-z0-9_$])")
                            .matcher(updated).replaceAll(Matcher.quoteReplacement(entry.getValue()));
                }
            }
            if (updated.equals(content)) {
                continue;
            }
            Comment replacement = comment instanceof JavadocComment ? new JavadocComment(updated)
                    : comment instanceof BlockComment ? new BlockComment(updated) : new LineComment(updated);
            comment.getCommentedNode().ifPresent(node -> node.setComment(replacement));
        }
    }

    // ------------------------------------------------------------------ imports

    /** Final import groups plus how to place them into the file. */
    private record ImportPlan(List<List<Imp>> groups, boolean hadImports, boolean changed, boolean commentsInside) {
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
            commentsInside = cu.getAllContainedComments().stream().anyMatch(c ->
                    c.getBegin().map(p -> p.line >= first && p.line <= last).orElse(false));
        }

        Set<String> present = new LinkedHashSet<>();
        List<List<Imp>> groups = new ArrayList<>();
        List<Boolean> groupSorted = new ArrayList<>();
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
                if (result.isEmpty()) {
                    changed = true;
                    continue;
                }
                Imp updated = result.get();
                changed |= !updated.name().equals(name);
                rewritten.add(updated);
            }
            if (sorted) {
                rewritten.sort(java.util.Comparator.comparing(Imp::sortKey));
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
        return new ImportPlan(groups, !cu.getImports().isEmpty(), changed, commentsInside);
    }

    /** Add to the last regular-import group (sorted in place when that group is sorted); create one if absent. */
    private void addToRegularGroup(List<List<Imp>> groups, List<Boolean> groupSorted,
                                   List<List<ImportDeclaration>> originalGroups, List<Imp> additions) {
        int target = -1;
        for (int i = 0; i < groups.size(); i++) {
            List<ImportDeclaration> original = originalGroups.get(i);
            boolean regular = original.stream().anyMatch(d -> !d.isStatic());
            if (regular) {
                target = i;
            }
        }
        if (target < 0) {
            groups.add(new ArrayList<>(additions));
            groupSorted.add(true);
            groups.getLast().sort(java.util.Comparator.comparing(Imp::sortKey));
            return;
        }
        List<Imp> group = groups.get(target);
        group.addAll(additions);
        if (groupSorted.get(target)) {
            group.sort(java.util.Comparator.comparing(Imp::sortKey));
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

    /** Replace the import block of the printed source with the planned groups. */
    private String spliceImports(String printed, ImportPlan plan, List<String> warnings) {
        if (!plan.changed()) {
            return printed;
        }
        ParsedSource reparsed = javaParserService.parse(javaParserService.createParser(), printed);
        if (!reparsed.successful()) {
            warnings.add("The rewritten source could not be re-read; imports were left unchanged.");
            return printed;
        }
        CompilationUnit cu = reparsed.compilationUnit();
        if (plan.commentsInside()) {
            warnings.add("The import block contains comments; its layout could not be preserved exactly.");
        }

        String block = plan.groups().stream()
                .map(group -> String.join("\n", group.stream().map(Imp::line).toList()))
                .reduce((a, b) -> a + "\n\n" + b).orElse("");
        LineIndex lines = new LineIndex(printed);

        if (plan.hadImports()) {
            ImportDeclaration first = cu.getImports().get(0);
            ImportDeclaration last = cu.getImports().get(cu.getImports().size() - 1);
            int start = lines.offset(first.getBegin().orElseThrow());
            int end = lines.offset(last.getEnd().orElseThrow()) + 1;
            if (block.isEmpty()) {
                // Remove the block and one adjoining blank line so no double gap remains.
                int from = start;
                int to = end;
                while (to < printed.length() && (printed.charAt(to) == '\n' || printed.charAt(to) == '\r')) {
                    to++;
                }
                return printed.substring(0, from) + printed.substring(to);
            }
            return printed.substring(0, start) + block + printed.substring(end);
        }

        // No imports before: insert after the package declaration, or at the first type / comment.
        Optional<PackageDeclaration> pkg = cu.getPackageDeclaration();
        if (pkg.isPresent()) {
            int end = lines.offset(pkg.get().getEnd().orElseThrow()) + 1;
            return printed.substring(0, end) + "\n\n" + block + printed.substring(end);
        }
        int start = firstCodeOffset(cu, lines);
        return printed.substring(0, start) + block + "\n\n" + printed.substring(start);
    }

    private int firstCodeOffset(CompilationUnit cu, LineIndex lines) {
        int offset = Integer.MAX_VALUE;
        for (TypeDeclaration<?> type : cu.getTypes()) {
            offset = Math.min(offset, lines.offset(type.getBegin().orElseThrow()));
            if (type.getComment().isPresent() && type.getComment().get().getBegin().isPresent()) {
                offset = Math.min(offset, lines.offset(type.getComment().get().getBegin().get()));
            }
        }
        return offset == Integer.MAX_VALUE ? 0 : offset;
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
