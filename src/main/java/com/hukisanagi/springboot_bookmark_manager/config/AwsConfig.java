package com.hukisanagi.springboot_bookmark_manager.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class AwsConfig {

    @Value("${spring.cloud.aws.region.static}")
    private String AWS_REGION;

    @Bean
    public CognitoIdentityProviderClient cognitoIdentityProviderClient() {
        CognitoIdentityProviderClient cognitoClient = CognitoIdentityProviderClient.builder()
            .region(Region.of(AWS_REGION))
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build();
        return cognitoClient;
    }
}