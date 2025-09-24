package com.hukisanagi.springboot_bookmark_manager.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "ranking_cache")
public class RankingCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 2048, unique = true, nullable = false)
    private String url;

    @Column(length = 512, nullable = false)
    private String title;

    @Column(length = 2048)
    private String faviconPath;

    private Long uniqueUserCount;
    private Long totalClickCount;
    private Long recentClickCount;
    private Long overallScore;

    private LocalDateTime lastUpdated;

    // Constructors
    public RankingCache() {
    }

    public RankingCache(String url, String title, String faviconPath, Long uniqueUserCount, Long totalClickCount, Long recentClickCount, Long overallScore, LocalDateTime lastUpdated) {
        this.url = url;
        this.title = title;
        this.faviconPath = faviconPath;
        this.uniqueUserCount = uniqueUserCount;
        this.totalClickCount = totalClickCount;
        this.recentClickCount = recentClickCount;
        this.overallScore = overallScore;
        this.lastUpdated = lastUpdated;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getFaviconPath() {
        return faviconPath;
    }

    public void setFaviconPath(String faviconPath) {
        this.faviconPath = faviconPath;
    }

    public Long getUniqueUserCount() {
        return uniqueUserCount;
    }

    public void setUniqueUserCount(Long uniqueUserCount) {
        this.uniqueUserCount = uniqueUserCount;
    }

    public Long getTotalClickCount() {
        return totalClickCount;
    }

    public void setTotalClickCount(Long totalClickCount) {
        this.totalClickCount = totalClickCount;
    }

    public Long getRecentClickCount() {
        return recentClickCount;
    }

    public void setRecentClickCount(Long recentClickCount) {
        this.recentClickCount = recentClickCount;
    }

    public Long getOverallScore() {
        return overallScore;
    }

    public void setOverallScore(Long overallScore) {
        this.overallScore = overallScore;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}
