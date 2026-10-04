package com.anvith.archmorph.parser;

import com.anvith.archmorph.common.exception.JavaParsingException;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

/**
 * Creates configured JavaParser instances and parses sources.
 *
 * <p>Parsers are created per analysis because the symbol solver is bound to
 * the source roots of one project. JavaParser instances are not thread-safe
 * and must not be shared between analyses.</p>
 */
@Service
public class JavaParserService {

    /** Files larger than this are almost always generated; they are not parsed. */
    public static final long MAX_SOURCE_BYTES = 2L * 1024 * 1024;

    /** Parser without symbol resolution (used for rewriting and validation). */
    public JavaParser createParser() {
        return new JavaParser(baseConfiguration());
    }

    /**
     * Parser with a JavaParser symbol solver over the given source roots and
     * the JDK. Framework types are not on the solver's classpath, so callers
     * must treat resolution failures as "unknown", never as errors.
     */
    public JavaParser createParser(Collection<Path> sourceRoots, boolean symbolSolver) {
        ParserConfiguration configuration = baseConfiguration();
        if (symbolSolver) {
            CombinedTypeSolver typeSolver = new CombinedTypeSolver(new ReflectionTypeSolver(true));
            for (Path root : sourceRoots) {
                if (Files.isDirectory(root)) {
                    typeSolver.add(new JavaParserTypeSolver(root, baseConfiguration()));
                }
            }
            configuration.setSymbolResolver(new JavaSymbolSolver(typeSolver));
        }
        return new JavaParser(configuration);
    }

    private ParserConfiguration baseConfiguration() {
        return new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21)
                .setCharacterEncoding(StandardCharsets.UTF_8)
                .setStoreTokens(true);
    }

    /** Backwards-compatible strict parse: throws when the file cannot be parsed. */
    public CompilationUnit parse(Path javaFile) {
        ParsedSource parsed = parse(createParser(), javaFile);
        if (!parsed.successful()) {
            throw new JavaParsingException("Unable to parse : " + javaFile.getFileName());
        }
        return parsed.compilationUnit();
    }

    /** Lenient parse: never throws for syntax errors. */
    public ParsedSource parse(JavaParser parser, Path javaFile) {
        String source;
        try {
            if (Files.size(javaFile) > MAX_SOURCE_BYTES) {
                return new ParsedSource(null, "", List.of(new ParsedSource.ParseProblem(0,
                        "File exceeds " + (MAX_SOURCE_BYTES / 1024) + " KB and was not parsed")));
            }
            source = readSource(javaFile);
        } catch (IOException e) {
            throw new JavaParsingException("Unable to read : " + javaFile.getFileName(), e);
        }
        return parse(parser, source);
    }

    public ParsedSource parse(JavaParser parser, String source) {
        ParseResult<CompilationUnit> result = parser.parse(source);
        List<ParsedSource.ParseProblem> problems = result.getProblems().stream()
                .map(problem -> new ParsedSource.ParseProblem(
                        problem.getLocation().flatMap(l -> l.getBegin().getRange())
                                .map(r -> r.begin.line).orElse(0),
                        firstLine(problem.getMessage())))
                .toList();
        CompilationUnit cu = result.isSuccessful() ? result.getResult().orElse(null) : null;
        return new ParsedSource(cu, source, problems);
    }

    /** Reads UTF-8, falling back to ISO-8859-1 for legacy encodings. */
    public static String readSource(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, StandardCharsets.ISO_8859_1);
        }
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "Parse error";
        }
        String line = message.lines().findFirst().orElse("Parse error");
        return line.length() > 300 ? line.substring(0, 300) : line;
    }
}
