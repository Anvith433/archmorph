package com.anvith.archmorph;

import java.io.IOException;

/** Test access to the package-private CLI entry point. */
public final class ArchMorphApplicationCliAccess {

    private ArchMorphApplicationCliAccess() {
    }

    public static int runCli(String... args) throws IOException {
        return ArchMorphApplication.runCli(args);
    }
}
