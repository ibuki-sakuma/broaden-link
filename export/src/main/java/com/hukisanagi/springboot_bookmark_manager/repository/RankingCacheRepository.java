package com.hukisanagi.springboot_bookmark_manager.repository;

import com.hukisanagi.springboot_bookmark_manager.model.RankingCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RankingCacheRepository extends JpaRepository<RankingCache, Long> {
    Optional<RankingCache> findByUrl(String url);
}