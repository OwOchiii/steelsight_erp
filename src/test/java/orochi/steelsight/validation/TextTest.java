package orochi.steelsight.validation;

import org.junit.jupiter.api.Test;
import orochi.steelsight.exception.BusinessRuleException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TextTest {

    /** The browser's maxlength counts a line break once, but sends it as \r\n. */
    @Test
    void textThatFitsTheBrowsersLimitFitsOursToo() {
        String typed = "a\r\n".repeat(1000) + "b".repeat(1000);   // 3000 characters in the textarea

        String kept = Text.required("A note", typed, 3000);

        assertThat(kept).hasSize(3000).doesNotContain("\r");
    }

    @Test
    void textOverTheLimitIsStillRefused() {
        assertThatThrownBy(() -> Text.required("A note", "x".repeat(11), 10))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A note can be at most 10 characters");
    }
}
