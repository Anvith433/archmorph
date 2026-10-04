package com.anvith.archmorph.report;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownReportsTest {

    @Test
    void escapesUntrustedTextSoReportsCannotInjectMarkupOrBreakTables() {
        String escaped = MarkdownReports.text("<img src=x onerror=alert(1)> | `code`\nnext");
        assertThat(escaped).doesNotContain("<img", "\n", "`").contains("&lt;img").contains("\\|");
        assertThat(MarkdownReports.code("a`b")).isEqualTo("a'b");
    }
}
