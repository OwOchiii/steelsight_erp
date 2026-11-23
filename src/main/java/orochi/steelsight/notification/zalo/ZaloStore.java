package orochi.steelsight.notification.zalo;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Zalo links (V18). Plain SQL on purpose: the chat lives on app_user, and writing it through the
 * entity would bump app_user.version and make an admin's open form go stale.
 */
@Repository
@RequiredArgsConstructor
public class ZaloStore {

    private final JdbcTemplate jdbc;

    /** The chat of an active, not deleted user, if they linked one. */
    public Optional<String> chatOf(Long userId) {
        List<String> chats = jdbc.queryForList("""
                SELECT zalo_chat_id FROM app_user
                WHERE id = ? AND zalo_chat_id IS NOT NULL AND is_active AND deleted_at IS NULL""", String.class, userId);
        return chats.stream().findFirst();
    }

    public record LinkCode(String code, OffsetDateTime expiresAt) {
    }

    /** Saves the account's one code, replacing the old. False when another account holds the same digits. */
    public boolean saveCode(Long userId, String code, OffsetDateTime expiresAt) {
        jdbc.update("DELETE FROM zalo_link_code WHERE code = ? AND expires_at <= now()", code);
        try {
            jdbc.update("""
                    INSERT INTO zalo_link_code (user_id, code, expires_at) VALUES (?, ?, ?)
                    ON CONFLICT (user_id) DO UPDATE SET code = EXCLUDED.code, expires_at = EXCLUDED.expires_at""",
                    userId, code, expiresAt);
            return true;
        } catch (org.springframework.dao.DuplicateKeyException e) {
            return false;
        }
    }

    public Optional<LinkCode> codeOf(Long userId) {
        return jdbc.query("SELECT code, expires_at FROM zalo_link_code WHERE user_id = ? AND expires_at > now()",
                (row, n) -> new LinkCode(row.getString(1), row.getObject(2, OffsetDateTime.class)), userId).stream().findFirst();
    }

    /** Whose a valid code is, without using it. */
    public Optional<Long> peekCode(String code) {
        return jdbc.queryForList("SELECT user_id FROM zalo_link_code WHERE code = ? AND expires_at > now()", Long.class, code)
                .stream().findFirst();
    }

    /** Uses up a code that hasn't expired, and returns whose it was. */
    public Optional<Long> useCode(String code) {
        List<Long> users = jdbc.queryForList(
                "DELETE FROM zalo_link_code WHERE code = ? AND expires_at > now() RETURNING user_id", Long.class, code);
        return users.stream().findFirst();
    }

    /** The active account this chat is linked to, if any (BR-14). */
    public Optional<Long> activeOwnerOf(String chatId) {
        return jdbc.queryForList("SELECT id FROM app_user WHERE zalo_chat_id = ? AND is_active AND deleted_at IS NULL",
                Long.class, chatId).stream().findFirst();
    }

    /** Links the chat; an inactive or deleted account that still held it lets it go. */
    public void link(Long userId, String chatId) {
        jdbc.update("UPDATE app_user SET zalo_chat_id = NULL WHERE zalo_chat_id = ? AND id <> ?", chatId, userId);
        jdbc.update("UPDATE app_user SET zalo_chat_id = ? WHERE id = ?", chatId, userId);
    }

    public String employeeCode(Long userId) {
        return jdbc.queryForObject("SELECT employee_code FROM app_user WHERE id = ?", String.class, userId);
    }

    public void unlinkUser(Long userId) {
        jdbc.update("UPDATE app_user SET zalo_chat_id = NULL WHERE id = ?", userId);
    }

    public Optional<String> unlinkChat(String chatId) {
        List<String> names = jdbc.queryForList(
                "UPDATE app_user SET zalo_chat_id = NULL WHERE zalo_chat_id = ? RETURNING full_name", String.class, chatId);
        return names.stream().findFirst();
    }

    public String fullName(Long userId) {
        return jdbc.queryForObject("SELECT full_name FROM app_user WHERE id = ?", String.class, userId);
    }
}
