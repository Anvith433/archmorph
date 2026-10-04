package com.anvith.archmorph.analysis.transformation;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;
import org.springframework.stereotype.Service;

import java.util.List;

/** Line diffs for the review UI. Source text is data; it is never interpreted. */
@Service
public class DiffService {

    private static final int CONTEXT_LINES = 3;

    /** @return {linesAdded, linesRemoved} */
    public int[] countChangedLines(String before, String after) {
        if (before.equals(after)) {
            return new int[]{0, 0};
        }
        Patch<String> patch = DiffUtils.diff(lines(before), lines(after));
        int added = 0;
        int removed = 0;
        for (AbstractDelta<String> delta : patch.getDeltas()) {
            added += delta.getTarget().size();
            removed += delta.getSource().size();
        }
        return new int[]{added, removed};
    }

    public String unifiedDiff(String sourcePath, String targetPath, String before, String after) {
        if (before.equals(after)) {
            return "";
        }
        Patch<String> patch = DiffUtils.diff(lines(before), lines(after));
        List<String> diff = UnifiedDiffUtils.generateUnifiedDiff("a/" + sourcePath, "b/" + targetPath,
                lines(before), patch, CONTEXT_LINES);
        return String.join("\n", diff);
    }

    private static List<String> lines(String text) {
        return text.lines().toList();
    }
}
