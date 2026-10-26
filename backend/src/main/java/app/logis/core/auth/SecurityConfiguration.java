package app.logis.core.auth;

import app.logis.core.users.UserRepository;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthProperties.class)
class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, UserRepository users, AuthProperties properties,
            Clock clock) throws Exception {
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                // Der Login-Zustand liegt in eigenen Session-Attributen; der SecurityContext wird pro
                // Anfrage vom SessionAuthenticationFilter aufgebaut und nicht in der Session gespeichert.
                .securityContext(ctx -> ctx.securityContextRepository(new RequestAttributeSecurityContextRepository()))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(new SessionAuthenticationFilter(users, properties, clock),
                        AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/api/health", "/error").permitAll()
                        // Nur auf dem internen Management-Port 8081 (Docker-Healthcheck), nie ueber nginx.
                        .requestMatchers(EndpointRequest.to(HealthEndpoint.class)).permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").hasRole("USER")
                        .anyRequest().denyAll());
        return http.build();
    }
}
