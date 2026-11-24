package orochi.steelsight.notification.zalo;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The parts of the bot commands that need no database; the replies themselves are in ZaloNoticesTest. */
class ZaloCommandsTest {

    @Test
    void splitKeepsEveryLineAndNeverGoesOverTheLimit() {
        String text = "header\n\n" + String.join("\n", Collections.nCopies(10, "x".repeat(30)));

        List<String> parts = ZaloCommands.split(text, 70);

        assertThat(parts).hasSizeGreaterThan(1)
                .allSatisfy(part -> assertThat(part.length()).isLessThanOrEqualTo(70));
        assertThat(parts.get(0)).startsWith("header");
        assertThat(String.join("\n", parts).lines().filter(line -> line.equals("x".repeat(30)))).hasSize(10);
    }

    @Test
    void aLineLongerThanTheLimitIsSplitInsideTheLine() {
        assertThat(ZaloCommands.split("y".repeat(150), 70)).containsExactly("y".repeat(70), "y".repeat(70), "y".repeat(10));
    }

    @Test
    void aShortReplyStaysOneMessage() {
        assertThat(ZaloCommands.split("Lịch làm việc tuần 24/11–30/11:\nT4 26/11 07:00–17:00 · Khu A", ZaloBot.MAX_TEXT))
                .containsExactly("Lịch làm việc tuần 24/11–30/11:\nT4 26/11 07:00–17:00 · Khu A");
    }

    @Test
    void commandsIgnoreCapitalsSpacesAndVietnameseMarks() {
        assertThat(ZaloCommands.plain("/Lịch tuần tới")).isEqualTo("/lichtuantoi");
        assertThat(ZaloCommands.plain(" /CHẤM CÔNG  tháng trước ")).isEqualTo("/chamcongthangtruoc");
        assertThat(ZaloCommands.plain(null)).isEmpty();
    }
}
