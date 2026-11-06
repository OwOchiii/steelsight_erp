package orochi.steelsight;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Nothing here needs the app running: a missing translation should fail the build, not surprise a user. */
class TranslationsTest {

    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final Pattern KEY_IN_TEMPLATE = Pattern.compile("#\\{([a-zA-Z][a-zA-Z0-9.]*)");
    private static final Pattern WRITTEN_MESSAGE =
            Pattern.compile("(?:BusinessRuleException\\(|addFlashAttribute\\(\"success\", |attempt\\(redirect, [^,]+, )\"([^\"]+)\"");

    @Test
    void bothLanguagesHaveTheSameKeys() throws IOException {
        Set<String> english = keys("messages.properties");
        Set<String> vietnamese = keys("messages_vi.properties");
        vietnamese.removeIf(key -> key.startsWith("msg.") || key.startsWith("category.")
                || (key.startsWith("enum.") && !english.contains(key)));

        assertThat(vietnamese).containsExactlyInAnyOrderElementsOf(english);
    }

    @Test
    void everyKeyATemplateUsesExists() throws IOException {
        Set<String> english = keys("messages.properties");
        try (Stream<Path> files = Files.walk(RESOURCES.resolve("templates"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".html")).toList()) {
                KEY_IN_TEMPLATE.matcher(Files.readString(file)).results().map(match -> match.group(1))
                        .filter(key -> key.contains("."))
                        .forEach(key -> assertThat(english).as(file.toString()).contains(key));
            }
        }
    }

    @Test
    void everyMessageWrittenInTheCodeHasAVietnameseTranslation() throws IOException {
        Set<String> vietnamese = keys("messages_vi.properties");
        List<String> untranslated;
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            untranslated = files.filter(path -> path.toString().endsWith(".java"))
                    .flatMap(TranslationsTest::writtenMessages)
                    .map(TranslationsTest::keyOf)
                    .filter(key -> !vietnamese.contains(key))
                    .toList();
        }
        assertThat(untranslated).isEmpty();
    }

    private static Stream<String> writtenMessages(Path file) {
        try {
            return WRITTEN_MESSAGE.matcher(Files.readString(file)).results().map(MatchResult::group).map(TranslationsTest::literal);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String literal(String match) {
        String withoutEnd = match.substring(0, match.length() - 1);
        return withoutEnd.substring(withoutEnd.lastIndexOf('"') + 1);
    }

    /** The same lookup as Display.message. A message built with a number ends before it, as "Enter between 0 and ". */
    private static String keyOf(String english) {
        String words = english.strip().replaceAll("\\d+", "n");
        if (words.equals("Enter between n and")) {
            words = "Enter between n and n";
        }
        return "msg." + words.toLowerCase(Locale.ENGLISH).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    private static Set<String> keys(String file) throws IOException {
        Properties properties = new Properties();
        properties.load(new StringReader(Files.readString(RESOURCES.resolve(file), StandardCharsets.UTF_8)));
        return new TreeSet<>(properties.stringPropertyNames());
    }
}
