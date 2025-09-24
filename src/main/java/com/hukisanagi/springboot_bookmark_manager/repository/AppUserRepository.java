package com.hukisanagi.springboot_bookmark_manager.repository;

import com.hukisanagi.springboot_bookmark_manager.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByCognitoSub(String cognitoSub);
}