package com.anvith.archmorph.common.util;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Produces safe display names from untrusted file names. The result is
 * metadata only; it is never used to build a filesystem path.
 */
public final class FilenameSanitizer {

    private static final int MAX_LENGTH = 80;

    private FilenameSanitizer() {
    }

    public static String displayName(String originalFilename) {
        if (originalFilename == null) {
            return "project";
        }
        String name = originalFilename.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = Normalizer.normalize(name, Normalizer.Form.NFKC);
        if (name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            name = name.substring(0, name.length() - 4);
        }
        name = name.replaceAll("[^A-Za-z0-9._ -]", "_").replaceAll("_{2,}", "_").trim();
        while (name.startsWith(".")) {
            name = name.substring(1);
        }
        if (name.length() > MAX_LENGTH) {
            name = name.substring(0, MAX_LENGTH);
        }
        return name.isBlank() ? "project" : name;
    }

    /** Safe ASCII slug for download file names. */
    public static String slug(String displayName) {
        String slug = displayName(displayName).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        return slug.isBlank() ? "project" : slug;
    }
}
