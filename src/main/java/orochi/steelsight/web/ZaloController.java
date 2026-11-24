package orochi.steelsight.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import orochi.steelsight.exception.BusinessRuleException;
import orochi.steelsight.notification.zalo.ZaloBot;
import orochi.steelsight.notification.zalo.ZaloLinks;
import orochi.steelsight.security.AuthenticatedUser;

import java.util.Map;

/** Linking a person's own Zalo, and the webhook the Zalo Bot Platform calls with messages sent to the bot. */
@Controller
@RequiredArgsConstructor
public class ZaloController extends PageController {

    public static final String WEBHOOK = "/zalo/webhook";

    private final ZaloBot bot;
    private final ZaloLinks links;

    /** Shows a fresh one-time code on the home page, to send to the bot. */
    @PostMapping("/account/zalo/link")
    public String link(@AuthenticationPrincipal AuthenticatedUser user, RedirectAttributes redirect) {
        try {
            redirect.addFlashAttribute("zaloCode", links.newCode(user.getUserId()));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/";
    }

    @PostMapping("/account/zalo/unlink")
    public String unlink(@AuthenticationPrincipal AuthenticatedUser user, RedirectAttributes redirect) {
        return attempt(redirect, "/", "Zalo was unlinked.", () -> links.unlink(user.getUserId()));
    }

    /**
     * Zalo posts each event of the bot here. Only text in a private chat means anything; the reply is
     * sent with sendMessage, after the link is saved.
     */
    @PostMapping(WEBHOOK)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> webhook(
            @RequestHeader(value = "X-Bot-Api-Secret-Token", required = false) String secret,
            @RequestBody Map<String, Object> body) {
        if (!bot.acceptsSecret(secret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        // The docs show the event inside {"ok": true, "result": {...}}; take it bare too.
        Map<?, ?> event = body.get("result") instanceof Map<?, ?> result ? result : body;
        if (!"message.text.received".equals(event.get("event_name"))
                || !(event.get("message") instanceof Map<?, ?> message)
                || !(message.get("chat") instanceof Map<?, ?> chat)
                || chat.get("id") == null
                || (chat.get("chat_type") != null && !"PRIVATE".equals(chat.get("chat_type")))) {
            return ResponseEntity.ok(Map.of());          // stickers, photos, groups: nothing to do
        }
        String chatId = String.valueOf(chat.get("id"));
        Object text = message.get("text");
        for (String reply : links.handle(chatId, text instanceof String s ? s : null)) {
            bot.send(chatId, reply);                     // in order: one sending thread
        }
        return ResponseEntity.ok(Map.of());
    }
}
