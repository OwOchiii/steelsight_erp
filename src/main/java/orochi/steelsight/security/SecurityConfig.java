package orochi.steelsight.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import orochi.steelsight.web.RequestLogFilter;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    /** Software calls /api/ with a key and no session. CSRF protects browsers' cookies, so it isn't needed here. */
    @Bean
    @Order(1)
    SecurityFilterChain apiFilterChain(HttpSecurity http,
                                       @Value("${steelsight.detection.api-key:}") String apiKey) throws Exception {
        return http
                .securityMatcher("/api/**")
                .addFilterAfter(new RequestLogFilter(), SecurityContextHolderFilter.class)
                .addFilterBefore(new ApiKeyFilter(apiKey), AuthorizationFilter.class)
                .authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(failures -> failures.authenticationEntryPoint(
                        (request, response, e) -> ApiKeyFilter.refuse(response)))
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // Just inside the security context, so it sees the user and also logs refused requests.
                .addFilterAfter(new RequestLogFilter(), SecurityContextHolderFilter.class)
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/login", "/language", "/css/**", "/js/**", "/img/**", "/error").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .usernameParameter("employeeCode")
                        .defaultSuccessUrl("/", true)
                        .permitAll())
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?signedOut")
                        .permitAll())
                .build();
    }
}
