package orochi.steelsight.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.LocaleResolver;
import orochi.steelsight.config.LocaleConfig;

import java.util.Locale;

@Controller
@RequiredArgsConstructor
public class LanguageController {

    private final LocaleResolver locales;

    /** Saves the language and goes back to the page it was chosen on. */
    @PostMapping("/language")
    public String choose(@RequestParam(required = false) String lang, HttpServletRequest request,
                         HttpServletResponse response) {
        locales.setLocale(request, response, "vi".equals(lang) ? LocaleConfig.VIETNAMESE : Locale.ENGLISH);
        return "redirect:" + SamePage.of(request).orElse("/");
    }
}
