package com.hukisanagi.springboot_bookmark_manager.service;

import com.hukisanagi.springboot_bookmark_manager.model.AppUser;
import com.hukisanagi.springboot_bookmark_manager.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDeleteUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ChangePasswordRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

@Service
public class AppUserService {

    private static final Logger logger = LoggerFactory.getLogger(AppUserService.class);

    private final AppUserRepository appUserRepository;
    private final CognitoIdentityProviderClient cognitoClient;
    private final StorageService storageService;

    @Value("${aws.cognito.user-pool-id}")
    private String userPoolId;

    public AppUserService(AppUserRepository appUserRepository, CognitoIdentityProviderClient cognitoClient, StorageService storageService) {
        this.appUserRepository = appUserRepository;
        this.cognitoClient = cognitoClient;
        this.storageService = storageService;
    }

    @Transactional
    public void deleteAccount(String cognitoSub, String username) {
        logger.info("Starting account deletion process for username: {}", username);
        try {
            // DBからユーザー情報を先に取得
            Optional<AppUser> userOptional = appUserRepository.findByCognitoSub(cognitoSub);
            
            // Step 1: (DBにユーザーが存在すれば) S3からファビコンフォルダを削除
            userOptional.ifPresent(appUser -> {
                String userFaviconFolder = String.valueOf(appUser.getId());
                logger.info("Attempting to delete favicon folder from S3: {}", userFaviconFolder);
                storageService.deleteFolder(userFaviconFolder);
                logger.info("Successfully deleted favicon folder from S3: {}", userFaviconFolder);
            });

            // Step 2: Cognitoからユーザーを削除
            logger.info("Attempting to delete user from Cognito: username={}", username);
            AdminDeleteUserRequest deleteRequest = AdminDeleteUserRequest.builder()
                    .userPoolId(userPoolId)
                    .username(username)
                    .build();
            cognitoClient.adminDeleteUser(deleteRequest);
            logger.info("Successfully deleted user from Cognito: username={}", username);

            // Step 3: (DBにユーザーが存在すれば) DBからユーザーと関連データを削除
            userOptional.ifPresent(appUser -> {
                logger.info("Attempting to delete user from DB: cognitoSub={}", cognitoSub);
                appUserRepository.delete(appUser);
                logger.info("Successfully deleted user from DB: cognitoSub={}", cognitoSub);
            });

            // DBにユーザーがいなかった場合の警告ログ
            if (userOptional.isEmpty()) {
                logger.warn("User not found in DB with cognitoSub: {}. S3 and DB deletion were skipped.", cognitoSub);
            }

            logger.info("Account deletion process completed successfully for username: {}", username);

        } catch (Exception e) {
            logger.error("Error during account deletion process for username: {}. Transaction will be rolled back.", username, e);
            throw e; // 例外を再スローしてトランザクションをロールバック
        }
    }

    public void changePassword(String accessToken, String previousPassword, String proposedPassword) {
        if (previousPassword.equals(proposedPassword)) {
            throw new IllegalArgumentException("New password cannot be the same as the current password.");
        }
        try {
            logger.info("Attempting to change password for user with accessToken.");
            ChangePasswordRequest changePasswordRequest = ChangePasswordRequest.builder()
                    .accessToken(accessToken)
                    .previousPassword(previousPassword)
                    .proposedPassword(proposedPassword)
                    .build();
            cognitoClient.changePassword(changePasswordRequest);
            logger.info("Password successfully changed.");
        } catch (Exception e) {
            logger.error("Error changing password: {}", e.getMessage(), e);
            throw e; // 例外を再スローして、コントローラで捕捉できるようにする
        }
    }
}
