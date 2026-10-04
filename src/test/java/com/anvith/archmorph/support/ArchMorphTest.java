package com.anvith.archmorph.support;

import org.springframework.boot.test.context.SpringBootTest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Full application context with an isolated workspace. Maven validation is disabled here (tests that
 * need it opt in); the in-JVM compiler is used to check that transformed fixtures compile.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@SpringBootTest(properties = {
        "archmorph.workspace.root=target/test-workspace",
        "archmorph.validation.build.enabled=false",
        "archmorph.security.rate-limit.enabled=false"
})
public @interface ArchMorphTest {
}
