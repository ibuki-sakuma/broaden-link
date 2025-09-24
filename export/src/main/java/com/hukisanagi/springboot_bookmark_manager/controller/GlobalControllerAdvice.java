package com.hukisanagi.springboot_bookmark_manager.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalControllerAdvice {

    @ModelAttribute("username")
    public String getUsername(@AuthenticationPrincipal OidcUser oidcUser) {
        if (oidcUser != null) {
            String name = oidcUser.getClaimAsString("cognito:username");
            if (name != null && !name.isEmpty()) {
                return name;
            }
            return oidcUser.getEmail();
        }
        return null;
    }

    @ModelAttribute("email")
    public String getEmail(@AuthenticationPrincipal OidcUser oidcUser) {
        if (oidcUser != null) {
            return oidcUser.getEmail();
        }
        return null;
    }
}