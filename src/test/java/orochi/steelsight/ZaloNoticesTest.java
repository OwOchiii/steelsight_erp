package orochi.steelsight;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import orochi.steelsight.attendance.service.CheckInService;
import orochi.steelsight.notification.service.ZaloDispatcher;
import orochi.steelsight.notification.zalo.ZaloBot;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Zalo linking and notices (SRS 3.2.7, 3.2.37, BR-13 to BR-17). The bot is a mock, so nothing leaves the
 * machine: replies to the webhook are bot.send calls, and the deliveries are bot.sendNow calls.
 */
@SpringBootTest(properties = "steelsight.storage.dir=target/test-uploads")
@AutoConfigureMockMvc
@Import(TestDatabase.class)
@Transactional
class ZaloNoticesTest extends PageTestSupport {

    private static final ZoneId SITE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String SECRET = "test-zalo-secret";
    private static final String CHAT = "a1b2c3d4e5";

    @MockitoBean ZaloBot bot;
    @Autowired CheckInService checkIns;
    @Autowired ZaloDispatcher dispatcher;

    Long admin;
    Long worker;
    Long other;
    Long hse;

    @BeforeEach
    void seed() {
        when(bot.enabled()).thenReturn(true);
        when(bot.canSend()).thenReturn(true);
        when(bot.botLink()).thenReturn("https://zalo.me/steelsight-test-bot");
        when(bot.acceptsSecret(SECRET)).thenReturn(true);
        when(bot.sendNow(anyString(), anyString())).thenReturn(Optional.empty());
        admin = userWithRole("ZL-A", "An Admin", "ADMIN");
        worker = userWithRole("ZL-W", "Lan Worker", "WORKER");
        other = userWithRole("ZL-O", "Oanh Other", "WORKER");
        hse = userWithRole("ZL-H", "Hà Hse", "HSE_OFFICER");
    }

    /** BR-13: the Admin makes a 6-digit code valid for 24 hours; a new code makes the old one invalid. */
    @Test
    void br13_theAdminMakesASixDigitCodeAndANewOneReplacesTheOld() throws Exception {
        mvc.perform(post("/team/" + worker + "/zalo-code").with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("success", "A new link code was made. The old code no longer works."));
        String first = code(worker);
        assertThat(first).matches("\\d{6}");
        assertThat(jdbc.queryForObject("SELECT expires_at BETWEEN now() + interval '23 hours 59 minutes' AND now() + interval '24 hours 1 minute' FROM zalo_link_code WHERE user_id = ?",
                Boolean.class, worker)).isTrue();

        mvc.perform(post("/team/" + worker + "/zalo-code").with(as(admin)).with(csrf()));
        String second = code(worker);
        webhook(first, CHAT).andExpect(status().isOk());
        assertThat(chatOf(worker)).isNull();
        if (!second.equals(first)) {
            webhook(second, CHAT);
            assertThat(chatOf(worker)).isEqualTo(CHAT);
        }
    }

    /** MSG21: the right code links the chat, once; spaces typed in the code don't matter. */
    @Test
    void msg21_theRightCodeLinksTheChatOnce() throws Exception {
        String code = newCode(worker);

        webhook(code.substring(0, 3) + " " + code.substring(3), CHAT).andExpect(status().isOk());

        assertThat(chatOf(worker)).isEqualTo(CHAT);
        verify(bot).send(eq(CHAT), contains("Tài khoản Zalo của bạn đã được liên kết với SteelSight (Lan Worker, ZL-W)"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM zalo_link_code WHERE user_id = ?", Integer.class, worker))
                .as("a code works once").isZero();
    }

    /** MSG22: a wrong or expired code links nothing. */
    @Test
    void msg22_anExpiredOrWrongCodeLinksNothing() throws Exception {
        String code = newCode(worker);
        jdbc.update("UPDATE zalo_link_code SET expires_at = now() - interval '1 minute' WHERE user_id = ?", worker);

        webhook(code, CHAT);
        webhook("000000", CHAT);

        assertThat(chatOf(worker)).isNull();
        verify(bot, org.mockito.Mockito.times(2)).send(eq(CHAT), contains("Mã không hợp lệ"));
    }

    /** BR-14, MSG23: a chat linked to another active account is refused. */
    @Test
    void br14_aChatLinkedToAnotherActiveAccountIsRefused() throws Exception {
        link(other, CHAT);
        String code = newCode(worker);

        webhook(code, CHAT);

        assertThat(chatOf(worker)).isNull();
        assertThat(chatOf(other)).isEqualTo(CHAT);
        verify(bot).send(eq(CHAT), contains("Tài khoản Zalo này đã liên kết với nhân viên khác"));
    }

    /** SRS 3.2.7: after 5 wrong codes in an hour, the bot stops answering that chat. */
    @Test
    void fiveWrongCodesSilenceTheChat() throws Exception {
        for (int i = 0; i < 5; i++) {
            webhook("11111" + i, "guesser");
        }
        String code = newCode(worker);
        webhook(code, "guesser");

        assertThat(chatOf(worker)).isNull();
        verify(bot, org.mockito.Mockito.times(5)).send(eq("guesser"), anyString());
    }

    @Test
    void aWrongSecretAGroupOrAStickerLinksNothing() throws Exception {
        String code = newCode(worker);
        mvc.perform(post("/zalo/webhook").header("X-Bot-Api-Secret-Token", "wrong")
                        .contentType(MediaType.APPLICATION_JSON).content(event(code, CHAT)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/zalo/webhook").header("X-Bot-Api-Secret-Token", SECRET).contentType(MediaType.APPLICATION_JSON)
                        .content(event(code, CHAT).replace("PRIVATE", "GROUP")))
                .andExpect(status().isOk());
        mvc.perform(post("/zalo/webhook").header("X-Bot-Api-Secret-Token", SECRET).contentType(MediaType.APPLICATION_JSON)
                        .content(event(code, CHAT).replace("message.text.received", "message.sticker.received")))
                .andExpect(status().isOk());

        assertThat(chatOf(worker)).isNull();
    }

    @Test
    void theEventMayComeWrappedInResult() throws Exception {
        String code = newCode(worker);
        mvc.perform(post("/zalo/webhook").header("X-Bot-Api-Secret-Token", SECRET).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ok\": true, \"result\": " + event(code, CHAT) + "}"))
                .andExpect(status().isOk());

        assertThat(chatOf(worker)).isEqualTo(CHAT);
    }

    /** BR-15: every notice is saved in the ERP first; someone without Zalo gets it there only. */
    @Test
    void br15_aNoticeIsSavedInTheErpEvenWithoutZalo() {
        OffsetDateTime in = LocalDateTime.of(2026, 11, 20, 7, 55).atZone(SITE).toOffsetDateTime();
        checkIns.recordManual(hse, new CheckInService.ManualCheckIn(worker, null, null, in, null, "Gate camera was down"));

        assertThat(jdbc.queryForObject("SELECT kind FROM notification WHERE user_id = ?", String.class, worker)).isEqualTo("VISIT_RECORDED");
        assertThat(jdbc.queryForObject("""
                SELECT d.status FROM notification_delivery d JOIN notification n ON n.id = d.notification_id
                WHERE n.user_id = ?""", String.class, worker)).isEqualTo("SKIPPED");
        dispatcher.sendDue();
        verify(bot, never()).sendNow(anyString(), anyString());
    }

    /** SRS 3.2.37, BR-72: the Zalo copy is sent in Vietnamese with the link to the page. */
    @Test
    void aLinkedPersonGetsTheNoticeOnZaloInVietnamese() {
        link(worker, CHAT);
        OffsetDateTime in = LocalDateTime.of(2026, 11, 20, 7, 55).atZone(SITE).toOffsetDateTime();
        checkIns.recordManual(hse, new CheckInService.ManualCheckIn(worker, null, null, in, null, "Gate camera was down"));

        assertThat(dispatcher.sendDue()).isEqualTo(1);

        verify(bot).sendNow(eq(CHAT), contains("Hà Hse đã ghi chấm công cho bạn: vào 20/11/2026 07:55"));
        assertThat(delivery(worker)).isEqualTo("SENT");
    }

    /** BR-16: a failed message is tried again 1, 5 and 15 minutes later, then FAILED with the error. */
    @Test
    void br16_aFailedMessageIsRetriedThreeTimesThenFailed() {
        link(worker, CHAT);
        when(bot.sendNow(anyString(), anyString())).thenReturn(Optional.of("Zalo is down"));
        checkIns.recordManual(hse, new CheckInService.ManualCheckIn(worker, null, null, OffsetDateTime.now(), null, "Gate camera was down"));

        dispatcher.sendDue();
        assertThat(delivery(worker)).isEqualTo("PENDING");
        assertThat(retryInMinutes(worker)).isEqualTo(1);
        for (int expected : new int[]{5, 15}) {
            makeDue(worker);
            dispatcher.sendDue();
            assertThat(retryInMinutes(worker)).isEqualTo(expected);
        }
        makeDue(worker);
        dispatcher.sendDue();

        assertThat(delivery(worker)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("""
                SELECT d.last_error FROM notification_delivery d JOIN notification n ON n.id = d.notification_id
                WHERE n.user_id = ?""", String.class, worker)).isEqualTo("Zalo is down");
    }

    /** BR-17: at most 2,000 characters; a longer text is cut at 1,900 and ends with the link. */
    @Test
    void br17_aLongTextIsCutAndEndsWithTheLink() throws Exception {
        var fit = ZaloDispatcher.class.getDeclaredMethod("fit", String.class, String.class);
        fit.setAccessible(true);
        String text = (String) fit.invoke(null, "x".repeat(2500), "https://erp/tickets/1");

        assertThat(text.length()).isLessThanOrEqualTo(2000);
        assertThat(text).startsWith("x".repeat(1900)).endsWith("https://erp/tickets/1");
        assertThat((String) fit.invoke(null, "short", "https://erp/a")).isEqualTo("short\nhttps://erp/a");
    }

    /** BR-07: deleting an account removes its Zalo link. */
    @Test
    void br07_deletingAnAccountRemovesItsZaloLink() throws Exception {
        link(worker, CHAT);
        mvc.perform(post("/team/" + worker + "/delete").param("confirm", "true").with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("success", "The account was deleted."));

        assertThat(chatOf(worker)).isNull();
    }

    private String newCode(Long user) throws Exception {
        mvc.perform(post("/team/" + user + "/zalo-code").with(as(admin)).with(csrf()));
        return code(user);
    }

    private ResultActions webhook(String text, String chatId) throws Exception {
        return mvc.perform(post("/zalo/webhook").header("X-Bot-Api-Secret-Token", SECRET)
                .contentType(MediaType.APPLICATION_JSON).content(event(text, chatId)));
    }

    private static String event(String text, String chatId) {
        return """
                {"event_name": "message.text.received", "message": {"message_id": "m1", "date": 1750316131602,
                 "from": {"id": "%2$s", "display_name": "Lan", "is_bot": false},
                 "chat": {"id": "%2$s", "chat_type": "PRIVATE"}, "text": "%1$s"}}""".formatted(text, chatId);
    }

    private String code(Long user) {
        return textColumn("SELECT code FROM zalo_link_code WHERE user_id = ?", user);
    }

    private String chatOf(Long user) {
        return jdbc.queryForObject("SELECT zalo_chat_id FROM app_user WHERE id = ?", String.class, user);
    }

    private void link(Long user, String chat) {
        jdbc.update("UPDATE app_user SET zalo_chat_id = ? WHERE id = ?", chat, user);
    }

    private String delivery(Long user) {
        return jdbc.queryForObject("""
                SELECT d.status FROM notification_delivery d JOIN notification n ON n.id = d.notification_id
                WHERE n.user_id = ? ORDER BY n.id DESC LIMIT 1""", String.class, user);
    }

    private long retryInMinutes(Long user) {
        return Math.round(jdbc.queryForObject("""
                SELECT extract(epoch FROM d.next_attempt_at - now()) / 60 FROM notification_delivery d
                JOIN notification n ON n.id = d.notification_id WHERE n.user_id = ? ORDER BY n.id DESC LIMIT 1""", Double.class, user));
    }


    /** CR-10, workflow 1.2.2: the account page shows the code and a QR code that opens the bot. */
    @Test
    void cr10_theAccountPageShowsTheCodeWithAQrCodeOfTheBot() throws Exception {
        mvc.perform(post("/team/" + worker + "/zalo-code").with(as(admin)).with(csrf()));

        mvc.perform(get("/team/" + worker + "/edit").with(as(admin)))
                .andExpect(content().string(containsString(code(worker))))
                .andExpect(content().string(containsString("<svg xmlns=\"http://www.w3.org/2000/svg\"")))
                .andExpect(content().string(containsString("Scan with Zalo to open the SteelSight bot")));
    }

    /** CR-10: the worker's own code on the Dashboard comes with the same QR code; none without a bot link. */
    @Test
    void cr10_theWorkersCodeComesWithTheQrCode() throws Exception {
        mvc.perform(get("/").with(as(worker)).flashAttr("zaloCode", "123456"))
                .andExpect(content().string(containsString("123456")))
                .andExpect(content().string(containsString("class=\"qr\"")));

        when(bot.botLink()).thenReturn("");
        mvc.perform(get("/").with(as(worker)).flashAttr("zaloCode", "123456"))
                .andExpect(content().string(not(containsString("class=\"qr\""))));
    }

    private void makeDue(Long user) {
        jdbc.update("""
                UPDATE notification_delivery d SET next_attempt_at = now() - interval '1 second'
                FROM notification n WHERE n.id = d.notification_id AND n.user_id = ? AND d.status = 'PENDING'""", user);
    }
}
