package com.hukisanagi.springboot_bookmark_manager.config;

import java.io.IOException;
import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class CognitoLogoutSuccessHandler implements LogoutSuccessHandler {

    private final ClientRegistrationRepository clientRegistrationRepository;

    @Value("${spring.security.oauth2.client.provider.cognito.hosted-ui-domain}")
    private String hostedUiDomain;

    public CognitoLogoutSuccessHandler(ClientRegistrationRepository clientRegistrationRepository) {
        this.clientRegistrationRepository = clientRegistrationRepository;
    }

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {
        
        ClientRegistration cognitoRegistration = this.clientRegistrationRepository.findByRegistrationId("cognito");
        String clientId = cognitoRegistration.getClientId();
        
        // アプリケーションのベースURLを動的に構築
        String logoutUri = UriComponentsBuilder.fromHttpUrl(request.getRequestURL().toString())
            .replacePath(request.getContextPath())
            .replaceQuery(null)
            .fragment(null)
            .path("/about") // ログアウト後のリダイレクト先
            .build().toUriString();

        // CognitoのログアウトURLを構築
        String cognitoLogoutUrl = UriComponentsBuilder.fromUriString(hostedUiDomain)
            .path("/logout")
            .queryParam("client_id", clientId)
            .queryParam("logout_uri", logoutUri)
            .build().toUriString();

        response.sendRedirect(cognitoLogoutUrl);
    }
}
