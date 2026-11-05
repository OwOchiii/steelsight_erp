package orochi.steelsight.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;

import java.time.Duration;
import java.util.Locale;

/** The language someone chose stays in a cookie. Until they choose, the browser's language decides. */
@Configuration(proxyBeanMethods = false)
public class LocaleConfig {

    public static final Locale VIETNAMESE = Locale.forLanguageTag("vi");

    @Bean
    LocaleResolver localeResolver() {
        CookieLocaleResolver resolver = new CookieLocaleResolver("steelsight-language");
        resolver.setCookieMaxAge(Duration.ofDays(365));
        resolver.setCookieSameSite("Lax");
        resolver.setDefaultLocaleFunction(request ->
                "vi".equals(request.getLocale().getLanguage()) ? VIETNAMESE : Locale.ENGLISH);
        return resolver;
    }
}
