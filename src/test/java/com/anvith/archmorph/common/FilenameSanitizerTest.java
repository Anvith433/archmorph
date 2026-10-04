package com.anvith.archmorph.common;

import com.anvith.archmorph.common.util.FilenameSanitizer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FilenameSanitizerTest {

    @Test
    void stripsPathsAndDangerousCharacters() {
        assertThat(FilenameSanitizer.displayName("../../etc/passwd.zip")).isEqualTo("passwd");
        assertThat(FilenameSanitizer.displayName("C:\\Users\\me\\shop.zip")).isEqualTo("shop");
        assertThat(FilenameSanitizer.displayName("<script>alert(1)</script>.zip")).doesNotContain("<", ">", "/");
        assertThat(FilenameSanitizer.displayName(".hidden.zip")).isEqualTo("hidden");
        assertThat(FilenameSanitizer.displayName(null)).isEqualTo("project");
        assertThat(FilenameSanitizer.displayName("x".repeat(500) + ".zip")).hasSize(80);
    }

    @Test
    void slugIsAsciiOnly() {
        assertThat(FilenameSanitizer.slug("My Shop (v2)")).isEqualTo("my-shop-v2");
        assertThat(FilenameSanitizer.slug("\"; rm -rf /")).matches("[a-z0-9-]+");
    }
}
