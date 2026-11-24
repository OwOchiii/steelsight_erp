package orochi.steelsight.notification.zalo;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The SteelSight bot on the Zalo Bot Platform (bot.zaloplatforms.com), which works with a token,
 * sendMessage, and a webhook. Without a token and the bot's link it is switched off, and
 * every send does nothing, so the ERP runs the same with or without Zalo.
 *
 * Messages go out on one background thread, so a slow or unreachable Zalo never holds up a page. A
 * message that fails is logged and dropped: a notice is a convenience, the ERP is the record.
 */
@Component
public class ZaloBot {

    /** Zalo refuses longer texts. */
    public static final int MAX_TEXT = 2000;
    private static final Logger log = LoggerFactory.getLogger(ZaloBot.class);

    private final RestClient client;
    private final String botLink;
    private final String secret;
    private final String webhookUrl;
    private final ExecutorService sender = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "zalo-sender");
        thread.setDaemon(true);
        return thread;
    });

    @Autowired
    public ZaloBot(RestClient.Builder builder,
                   @Value("${steelsight.zalo.bot-token:}") String token,
                   @Value("${steelsight.zalo.bot-link:}") String botLink,
                   @Value("${steelsight.zalo.webhook-secret:}") String secret,
                   @Value("${steelsight.zalo.webhook-url:}") String webhookUrl) {
        this.client = token.isBlank() ? null : builder
                .requestFactory(timeouts())
                .baseUrl("https://bot-api.zaloplatforms.com/bot" + token.strip())
                .build();
        this.botLink = botLink.strip();
        this.secret = secret.strip();
        this.webhookUrl = webhookUrl.strip();
    }

    public boolean enabled() {
        return client != null && !botLink.isBlank();
    }

    /** A token is all that sending needs; the link is only for people opening the bot. */
    public boolean canSend() {
        return client != null;
    }

    /**
     * Sends one message and waits for Zalo's answer, for the dispatcher that retries (SRS BR-16).
     * Returns the error, or empty when Zalo accepted it. The token is never written to the log.
     */
    public java.util.Optional<String> sendNow(String chatId, String text) {
        if (client == null) {
            return java.util.Optional.of("Zalo is not set up on this server");
        }
        try {
            Map<?, ?> answer = client.post().uri("/sendMessage")
                    .body(Map.of("chat_id", chatId, "text", text)).retrieve().body(Map.class);
            if (answer == null || !Boolean.TRUE.equals(answer.get("ok"))) {
                return java.util.Optional.of(String.valueOf(answer));
            }
            return java.util.Optional.empty();
        } catch (RestClientException e) {
            return java.util.Optional.of(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** The bot's own link or QR page from Zalo Bot Creator, where a worker opens the chat. */
    public String botLink() {
        return botLink;
    }

    /** Zalo sends the secret chosen at setWebhook with every event; anything else is refused. */
    public boolean acceptsSecret(String given) {
        return !secret.isBlank() && given != null
                && MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8));
    }

    public void send(String chatId, String text) {
        if (!enabled()) {
            return;
        }
        String body = text.length() > MAX_TEXT ? text.substring(0, MAX_TEXT - 1) + "…" : text;
        sender.execute(() -> call("/sendMessage", Map.of("chat_id", chatId, "text", body), "message to chat " + chatId));
    }

    /** Points Zalo at this ERP. Needs ZALO_WEBHOOK_URL and ZALO_WEBHOOK_SECRET; otherwise set it by hand. */
    @EventListener(ApplicationReadyEvent.class)
    public void registerWebhook() {
        if (!enabled() || webhookUrl.isBlank() || secret.isBlank()) {
            return;
        }
        sender.execute(() -> call("/setWebhook", Map.of("url", webhookUrl, "secret_token", secret), "webhook registration"));
    }

    /** Zalo answers 200 with {"ok": false, ...} for some failures, so the body is checked too. */
    private void call(String method, Map<String, Object> body, String what) {
        try {
            Map<?, ?> answer = client.post().uri(method).body(body).retrieve().body(Map.class);
            if (answer == null || !Boolean.TRUE.equals(answer.get("ok"))) {
                log.warn("Zalo {} failed: {}", what, answer);
            }
        } catch (RestClientException e) {
            log.warn("Zalo {} failed: {}", what, e.getMessage());
        }
    }

    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));
        return factory;
    }

    @PreDestroy
    void stop() {
        sender.shutdown();
    }
}
