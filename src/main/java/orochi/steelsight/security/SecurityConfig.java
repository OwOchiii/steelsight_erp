package orochi.steelsight.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.WebAttributes;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import orochi.steelsight.identity.repository.AppUserRepository;
import orochi.steelsight.web.RequestLogFilter;
import orochi.steelsight.web.ZaloController;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    /** Software calls /api/ with a key and no session. CSRF protects browsers' cookies, so it isn't needed here. */
    @Bean
    @Order(1)
    SecurityFilterChain apiFilterChain(HttpSecurity http, ApiLimiter limiter,
                                       @Value("${steelsight.detection.api-key:}") String apiKey) throws Exception {
        return http
                .securityMatcher("/api/**")
                .addFilterAfter(new RequestLogFilter(), SecurityContextHolderFilter.class)
                .addFilterBefore(new ApiKeyFilter(apiKey, limiter), AuthorizationFilter.class)
                .authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(failures -> failures.authenticationEntryPoint(
                        (request, response, e) -> ApiKeyFilter.refuse(response)))
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(HttpSecurity http, SignInLimiter signInLimiter,
                                           AppUserRepository users, SessionRegistry sessionRegistry) throws Exception {
        return http
                // Just inside the security context, so it sees the user and also logs refused requests.
                .addFilterAfter(new RequestLogFilter(), SecurityContextHolderFilter.class)
                .addFilterBefore(new SignInLimitFilter(signInLimiter), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new PasswordChangeRequiredFilter(), AuthorizationFilter.class)
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/login", "/language", "/css/**", "/js/**", "/img/**", "/error").permitAll()
                        // The Zalo bot calls this with no session; the controller checks its webhook secret.
                        .requestMatchers(HttpMethod.POST, ZaloController.WEBHOOK).permitAll()
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf.ignoringRequestMatchers(ZaloController.WEBHOOK))
                .formLogin(form -> form
                        .loginPage("/login")
                        .usernameParameter("employeeCode")
                        .successHandler(new FirstSignInHandler(users))
                        .permitAll())
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?signedOut")
                        .permitAll())
                // Spring's default handler doesn't say why it refused. Keeping the reason lets the error page
                // tell a form whose session ran out (a CSRF failure) apart from a page the person can't open.
                .exceptionHandling(failures -> failures.accessDeniedHandler((request, response, e) -> {
                    request.setAttribute(WebAttributes.ACCESS_DENIED_403, e);
                    response.sendError(HttpServletResponse.SC_FORBIDDEN);
                }))
                // No limit on sign-ins per person. The registry is what lets UserSessions end them.
                .sessionManagement(session -> session
                        .maximumSessions(-1)
                        .sessionRegistry(sessionRegistry)
                        .expiredUrl("/login?signedOut"))
                .build();
    }

    @Bean
    SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /** Tells the registry when a session ends, so it doesn't keep every session forever. */
    @Bean
    HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }
}
