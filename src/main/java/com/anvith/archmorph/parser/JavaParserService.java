package com.anvith.archmorph.parser;
import com.anvith.archmorph.common.exception.JavaParsingException;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.file.Path;

@Service
public class JavaParserService {

    private final JavaParser javaParser = new JavaParser();

    public CompilationUnit parse(Path javaFile) {

        try {

            ParseResult<CompilationUnit> result =
                    javaParser.parse(javaFile);

            if (result.isSuccessful()
                    && result.getResult().isPresent()) {

                return result.getResult().get();

            }

            throw new JavaParsingException(
                    "Unable to parse : "
                            + javaFile.getFileName()
            );

        } catch (IOException e) {

            throw new JavaParsingException(
                    "Unable to read : "
                            + javaFile.getFileName(),
                    e
            );

        }

    }

}