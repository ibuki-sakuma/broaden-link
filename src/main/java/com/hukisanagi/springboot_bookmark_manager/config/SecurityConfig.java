package com.hukisanagi.springboot_bookmark_manager.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final ClientRegistrationRepository clientRegistrationRepository;
    private final LogoutSuccessHandler cognitoLogoutSuccessHandler;

    public SecurityConfig(ClientRegistrationRepository clientRegistrationRepository, LogoutSuccessHandler cognitoLogoutSuccessHandler) {
        this.clientRegistrationRepository = clientRegistrationRepository;
        this.cognitoLogoutSuccessHandler = cognitoLogoutSuccessHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/register", "/css/**", "/js/**", "/images/**", "/favicons/**", "/h2-console/**", "/webjars/**", "/ranking", "/about", "/broaden", "/oauth2/authorization/**", "/login/oauth2/code/**", "/account_deleted").permitAll()
                .anyRequest().authenticated()
            )
            .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**"))
            .headers(headers -> headers.frameOptions().sameOrigin())
            .oauth2Login(oauth2Login -> oauth2Login
                .loginPage("/oauth2/authorization/cognito")
                .defaultSuccessUrl("/", true)
                .authorizationEndpoint(authorization -> authorization
                    .authorizationRequestResolver(customAuthorizationRequestResolver())
                )
            )
            .logout(logout -> logout
                .logoutSuccessHandler(cognitoLogoutSuccessHandler)
            );
        return http.build();
    }

    @Bean
    public OAuth2AuthorizationRequestResolver customAuthorizationRequestResolver() {
        return new CustomOAuth2AuthorizationRequestResolver(clientRegistrationRepository);
    }

    private static class CustomOAuth2AuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {
        private final OAuth2AuthorizationRequestResolver defaultResolver;

        public CustomOAuth2AuthorizationRequestResolver(ClientRegistrationRepository clientRegistrationRepository) {
            this.defaultResolver = new DefaultOAuth2AuthorizationRequestResolver(
                    clientRegistrationRepository, "/oauth2/authorization");
        }

        @Override
        public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
            return customizeAuthorizationRequest(defaultResolver.resolve(request));
        }

        @Override
        public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
            return customizeAuthorizationRequest(defaultResolver.resolve(request, clientRegistrationId));
        }

        private OAuth2AuthorizationRequest customizeAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest) {
            if (authorizationRequest == null) {
                return null;
            }

            Map<String, Object> additionalParameters = new LinkedHashMap<>(
                    authorizationRequest.getAdditionalParameters());
            additionalParameters.put("lang", "ja"); // 日本語を設定

            // 既存のスコープにaws.cognito.signin.user.adminを追加
            Set<String> scopes = new LinkedHashSet<>(authorizationRequest.getScopes());
            scopes.add("aws.cognito.signin.user.admin");

            return OAuth2AuthorizationRequest.from(authorizationRequest)
                    .additionalParameters(additionalParameters)
                    .scopes(scopes) // スコープを設定
                    .build();
        }
    }
}

