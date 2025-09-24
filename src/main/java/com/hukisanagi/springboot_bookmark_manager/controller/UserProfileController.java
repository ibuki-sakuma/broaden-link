package com.hukisanagi.springboot_bookmark_manager.controller;

import com.hukisanagi.springboot_bookmark_manager.service.AppUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller
public class UserProfileController {

    private static final Logger logger = LoggerFactory.getLogger(UserProfileController.class);

    private final AppUserService appUserService;
    private final OAuth2AuthorizedClientService authorizedClientService;

    public UserProfileController(AppUserService appUserService, OAuth2AuthorizedClientService authorizedClientService) {
        this.appUserService = appUserService;
        this.authorizedClientService = authorizedClientService;
    }

    @PostMapping("/profile/delete")
    public String deleteAccount(@AuthenticationPrincipal OidcUser principal, HttpServletRequest request, RedirectAttributes redirectAttributes) {
        if (principal == null) {
            logger.warn("Attempted to delete account without principal.");
            return "redirect:/login";
        }

        try {
            String cognitoSub = principal.getSubject();
            String username = principal.getClaimAsString("cognito:username");

            logger.info("Attempting to delete account for user. sub: {}, username: {}", cognitoSub, username);
            appUserService.deleteAccount(cognitoSub, username);

            // セッションを無効化してログアウト
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
                logger.info("Session invalidated for user: {}", username);
            }

            redirectAttributes.addFlashAttribute("accountDeleted", "アカウントが正常に削除されました。");
            logger.info("Account deleted successfully for user: {}", username);
            return "redirect:/account_deleted";

        } catch (Exception e) {
            logger.error("Error deleting account for user: {}. Error: {}", principal != null ? principal.getName() : "unknown", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "アカウントの削除中にエラーが発生しました。もう一度お試しください。");
            return "redirect:/account_deleted";
        }
    }

    @GetMapping("/account_deleted")
    public String accountDeleted(Model model) {
        // Flash attributes are automatically added to the model
        return "account_deleted";
    }

    @PostMapping("/api/profile/change-password-internal")
    public String changePasswordInternal(@AuthenticationPrincipal OidcUser principal,
                                         @RequestParam("currentPassword") String currentPassword,
                                         @RequestParam("newPassword") String newPassword,
                                         Model model, Authentication authentication) {
        if (principal == null) {
            logger.warn("Attempted to change password without principal.");
            model.addAttribute("error", "認証情報が見つかりません。");
            return "fragments/change_password_modal :: change_password_modal";
        }

        try {
            OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken)authentication;
            OAuth2AuthorizedClient authorizedClient = authorizedClientService.loadAuthorizedClient(oauthToken.getAuthorizedClientRegistrationId(), oauthToken.getName());
            
            if (authorizedClient == null) {
                logger.warn("OAuth2AuthorizedClient is null for user: {}", principal.getName());
                model.addAttribute("error", "認証情報が見つかりません。再度ログインしてください。");
                return "fragments/change_password_modal :: change_password_modal";
            }

            String accessToken = authorizedClient.getAccessToken().getTokenValue();
            appUserService.changePassword(accessToken, currentPassword, newPassword);
            model.addAttribute("success", "パスワードが正常に変更されました。");
        } catch (software.amazon.awssdk.services.cognitoidentityprovider.model.NotAuthorizedException e) {
            logger.error("Error changing password for user: {}. Error: {}", principal.getName(), e.getMessage(), e);
            model.addAttribute("error", "現在のパスワードが正しくありません。");
        } catch (software.amazon.awssdk.services.cognitoidentityprovider.model.InvalidPasswordException e) {
            logger.error("Error changing password for user: {}. Error: {}", principal.getName(), e.getMessage(), e);
            model.addAttribute("error", "新しいパスワードがパスワードポリシーを満たしていません。");
        } catch (software.amazon.awssdk.services.cognitoidentityprovider.model.LimitExceededException e) {
            logger.error("Error changing password for user: {}. Error: {}", principal.getName(), e.getMessage(), e);
            model.addAttribute("error", "パスワード変更の試行回数が上限を超えました。しばらく時間をおいてから再度お試しください。");
        } catch (IllegalArgumentException e) {
            logger.error("Error changing password for user: {}. Error: {}", principal.getName(), e.getMessage(), e);
            model.addAttribute("error", "新しいパスワードは現在のパスワードと同じにできません。");
        } catch (Exception e) {
            logger.error("Error changing password for user: {}. Error: {}", principal.getName(), e.getMessage(), e);
            model.addAttribute("error", "パスワードの変更に失敗しました。もう一度お試しください。");
        }
        return "fragments/change_password_modal :: change_password_modal";
    }
}


