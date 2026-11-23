package orochi.steelsight.web;

import io.nayuki.qrcodegen.QrCode;

/**
 * QR codes as inline SVG, drawn on the server so the page needs no script or image request. Used for the
 * link to the SteelSight Zalo bot (SRS CR-10, workflow 1.2.2): the employee scans it with Zalo, the bot
 * opens, and they send their 6-digit code.
 */
final class QrCodes {

    private static final int QUIET_ZONE = 4;   // modules of white border that scanners need

    private QrCodes() {
    }

    /** An SVG of the text as a QR code, or null when there is no text. Only path data, never the text itself. */
    static String svg(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        QrCode qr = QrCode.encodeText(text.strip(), QrCode.Ecc.MEDIUM);
        int size = qr.size + QUIET_ZONE * 2;
        StringBuilder path = new StringBuilder();
        for (int y = 0; y < qr.size; y++) {
            for (int x = 0; x < qr.size; x++) {
                if (qr.getModule(x, y)) {
                    path.append('M').append(x + QUIET_ZONE).append(',').append(y + QUIET_ZONE).append("h1v1h-1z");
                }
            }
        }
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 " + size + " " + size + "\" "
                + "class=\"qr\" role=\"img\" shape-rendering=\"crispEdges\">"
                + "<rect width=\"100%\" height=\"100%\" fill=\"#fff\"/>"
                + "<path d=\"" + path + "\" fill=\"#000\"/></svg>";
    }
}
