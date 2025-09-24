package com.hukisanagi.springboot_bookmark_manager.controller;

import org.springframework.beans.factory.annotation.Value;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;

import java.util.UUID;

@Controller
public class AuthController {
    private final ClientRegistrationRepository clientRegistrationRepository;

    @Value("${spring.security.oauth2.client.provider.cognito.hosted-ui-domain}")
    private String hostedUiDomain;

    public AuthController(ClientRegistrationRepository clientRegistrationRepository) {
        this.clientRegistrationRepository = clientRegistrationRepository;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public RedirectView registerForm(HttpServletRequest request) {
        ClientRegistration cognitoRegistration = clientRegistrationRepository.findByRegistrationId("cognito");
        
        String clientId = cognitoRegistration.getClientId();
        String redirectUriTemplate = cognitoRegistration.getRedirectUri();

        // 現在のリクエストからbaseUrlを動的に構築
        String baseUrl = UriComponentsBuilder.fromUriString(request.getRequestURL().toString())
                .replacePath(request.getContextPath())
                .build().toUriString();

        // redirectUriTemplateの{baseUrl}と{registrationId}を動的に構築したbaseUrlで置き換える
        String redirectUri = redirectUriTemplate
                .replace("{baseUrl}", baseUrl)
                .replace("{registrationId}", cognitoRegistration.getRegistrationId());

        String scope = String.join("+", cognitoRegistration.getScopes());

        // nonceとstateを生成
        String nonce = UUID.randomUUID().toString();
        String state = UUID.randomUUID().toString();

        String signupUrl = UriComponentsBuilder.fromUriString(hostedUiDomain)
                .path("/signup")
                .queryParam("client_id", clientId)
                .queryParam("response_type", "code")
                .queryParam("scope", scope)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("lang", "ja")
                .queryParam("nonce", nonce)
                .queryParam(OAuth2ParameterNames.STATE, state)
                .build().toUriString();

        return new RedirectView(signupUrl);
    }
}