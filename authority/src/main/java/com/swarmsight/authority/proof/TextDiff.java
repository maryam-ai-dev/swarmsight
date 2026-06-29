package com.swarmsight.authority.proof;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * A word-level diff between two texts, using a longest-common-subsequence. Used
 * to derive the draft-to-final diff from the author and final payloads at
 * assembly time. The diff is never stored; it is always recomputed from the
 * rows, so it cannot drift from them.
 */
@Component
public class TextDiff {

    /** One run of words that is unchanged, inserted in the final, or deleted. */
    public record Segment(Op op, String text) {
        public enum Op {
            EQUAL,
            INSERT,
            DELETE
        }
    }

    public List<Segment> diff(String author, String finalText) {
        String[] a = tokenize(author);
        String[] b = tokenize(finalText);

        // LCS length table.
        int[][] lcs = new int[a.length + 1][b.length + 1];
        for (int i = a.length - 1; i >= 0; i--) {
            for (int j = b.length - 1; j >= 0; j--) {
                lcs[i][j] = a[i].equals(b[j])
                        ? lcs[i + 1][j + 1] + 1
                        : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }

        List<Segment> out = new ArrayList<>();
        StringBuilder run = new StringBuilder();
        Segment.Op runOp = null;
        int i = 0;
        int j = 0;
        while (i < a.length && j < b.length) {
            if (a[i].equals(b[j])) {
                runOp = flush(out, run, runOp, Segment.Op.EQUAL, a[i]);
                i++;
                j++;
            } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                runOp = flush(out, run, runOp, Segment.Op.DELETE, a[i]);
                i++;
            } else {
                runOp = flush(out, run, runOp, Segment.Op.INSERT, b[j]);
                j++;
            }
        }
        while (i < a.length) {
            runOp = flush(out, run, runOp, Segment.Op.DELETE, a[i++]);
        }
        while (j < b.length) {
            runOp = flush(out, run, runOp, Segment.Op.INSERT, b[j++]);
        }
        if (run.length() > 0) {
            out.add(new Segment(runOp, run.toString().trim()));
        }
        return out;
    }

    private Segment.Op flush(List<Segment> out, StringBuilder run, Segment.Op runOp, Segment.Op op, String word) {
        if (runOp != null && runOp != op) {
            out.add(new Segment(runOp, run.toString().trim()));
            run.setLength(0);
        }
        run.append(word).append(' ');
        return op;
    }

    private String[] tokenize(String text) {
        if (text == null || text.isBlank()) {
            return new String[0];
        }
        return text.trim().split("\\s+");
    }
}
