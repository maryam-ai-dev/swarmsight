package com.swarmsight.authority.proof;

import static org.assertj.core.api.Assertions.assertThat;

import com.swarmsight.authority.proof.TextDiff.Segment;
import com.swarmsight.authority.proof.TextDiff.Segment.Op;
import java.util.List;
import org.junit.jupiter.api.Test;

class TextDiffTest {

    private final TextDiff textDiff = new TextDiff();

    @Test
    void identicalTextIsAllEqual() {
        List<Segment> diff = textDiff.diff("the next steps in your case", "the next steps in your case");
        assertThat(diff).singleElement().extracting(Segment::op).isEqualTo(Op.EQUAL);
    }

    @Test
    void deletionsAndInsertionsAreDetected() {
        List<Segment> diff = textDiff.diff(
                "I am writing to inform you that your application has been refused.",
                "I am writing to confirm the next steps in your case.");

        // The shared prefix survives, the refusal wording is deleted, new wording inserted.
        assertThat(diff).anyMatch(s -> s.op() == Op.EQUAL && s.text().contains("I am writing to"));
        assertThat(diff).anyMatch(s -> s.op() == Op.DELETE && s.text().contains("refused"));
        assertThat(diff).anyMatch(s -> s.op() == Op.INSERT && s.text().contains("next steps"));
    }

    @Test
    void emptyAuthorMakesEverythingInserted() {
        List<Segment> diff = textDiff.diff("", "a brand new draft");
        assertThat(diff).singleElement().extracting(Segment::op).isEqualTo(Op.INSERT);
    }
}
