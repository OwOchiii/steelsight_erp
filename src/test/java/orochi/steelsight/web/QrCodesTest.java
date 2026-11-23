package orochi.steelsight.web;

import io.nayuki.qrcodegen.QrCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QrCodesTest {

    @Test
    void drawsTheLinkAsAnSvgWithAQuietZoneAndNeverTheTextItself() {
        String link = "https://zalo.me/steelsight-test-bot";
        String svg = QrCodes.svg(link);
        int modules = QrCode.encodeText(link, QrCode.Ecc.MEDIUM).size;

        assertThat(svg).startsWith("<svg").endsWith("</svg>")
                .contains("viewBox=\"0 0 " + (modules + 8) + " " + (modules + 8) + "\"")
                .contains("<path d=\"M")
                .doesNotContain("zalo.me");
    }

    @Test
    void noLinkMeansNoQrCode() {
        assertThat(QrCodes.svg(null)).isNull();
        assertThat(QrCodes.svg("  ")).isNull();
    }
}
