package orochi.steelsight.notification.zalo;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import orochi.steelsight.exception.BusinessRuleException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Linking an employee's Zalo chat (SRS 3.2.7, BR-13, BR-14). The Admin makes a 6-digit code on the
 * Account Form (or the employee on the home page); the employee sends it to the SteelSight bot. A code
 * is valid for 24 hours and works once, and a new code makes the old one invalid at once.
 *
 * Six digits alone could be guessed, so a chat that sends 5 wrong codes in an hour is ignored for an
 * hour, and the webhook only takes events signed with the bot's secret. A chat already linked to
 * another active account is refused (MSG23).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ZaloLinks {

    static final Duration CODE_LIFETIME = Duration.ofHours(24);
    static final int CODE_LENGTH = 6;
    static final int WRONG_CODES_ALLOWED = 5;
    private static final Pattern CODE = Pattern.compile("(?:/start)?(\\d{" + CODE_LENGTH + "})");
    private final com.github.benmanes.caffeine.cache.Cache<String, Integer> wrongCodes =
            com.github.benmanes.caffeine.cache.Caffeine.newBuilder().expireAfterWrite(Duration.ofHours(1)).maximumSize(10_000).build();
    private static final Locale SITE = Locale.forLanguageTag("vi");

    private final ZaloBot bot;
    private final ZaloStore store;
    private final ZaloCommands commands;
    private final MessageSource messages;
    private final SecureRandom random = new SecureRandom();

    /** A fresh code for the person's own account, to send to the bot. */
    public String newCode(Long actorId) {
        return newCodeFor(actorId);
    }

    /** A fresh code for an account; the old one stops working at once (BR-13, MSG20). */
    public String newCodeFor(Long userId) {
        if (!bot.canSend()) {
            throw new BusinessRuleException("Zalo is not set up on this server");
        }
        for (int attempt = 0; attempt < 20; attempt++) {
            String code = String.format("%06d", random.nextInt(1_000_000));
            if (store.saveCode(userId, code, OffsetDateTime.now().plus(CODE_LIFETIME))) {
                return code;
            }
        }
        throw new BusinessRuleException("Try again in a moment");
    }

    public boolean canMakeCodes() {
        return bot.canSend();
    }

    /** The code of an account that is still valid, and until when; empty when there is none. */
    public java.util.Optional<ZaloStore.LinkCode> currentCode(Long userId) {
        return store.codeOf(userId);
    }

    public void unlink(Long actorId) {
        store.unlinkUser(actorId);
    }

    @Transactional(readOnly = true)
    public boolean isLinked(Long actorId) {
        return store.chatOf(actorId).isPresent();
    }

    /**
     * Handles one text sent to the bot, and returns the reply, or null to stay silent. Spaces in the
     * text are removed, so "123 456" works (SRS 3.2.7).
     */
    public String handle(String chatId, String text) {
        String message = text == null ? "" : text.replaceAll("\\s+", "");
        if (message.equalsIgnoreCase("/stop")) {
            return store.unlinkChat(chatId).isPresent() ? text("zalo.unlinked") : text("zalo.help");
        }
        var code = CODE.matcher(message);
        Optional<Long> owner = store.activeOwnerOf(chatId);
        if (owner.isPresent() && !code.matches()) {
            return commands.reply(owner.get(), text);    // a linked person: /help, /lich, /chamcong
        }
        Integer wrong = wrongCodes.getIfPresent(chatId);
        if (wrong != null && wrong >= WRONG_CODES_ALLOWED) {
            return null;
        }
        if (code.matches()) {
            Optional<Long> user = store.peekCode(code.group(1));
            if (user.isEmpty()) {
                wrongCodes.asMap().merge(chatId, 1, Integer::sum);
                return text("zalo.badCode");
            }
            if (owner.isPresent() && !owner.get().equals(user.get())) {
                return text("zalo.taken");
            }
            store.useCode(code.group(1));
            store.link(user.get(), chatId);
            return text("zalo.linked", store.fullName(user.get()), store.employeeCode(user.get()));
        }
        return text("zalo.help");
    }

    private String text(String key, Object... args) {
        return messages.getMessage(key, args, SITE);
    }
}
