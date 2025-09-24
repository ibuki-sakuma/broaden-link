package com.hukisanagi.springboot_bookmark_manager.model;

import jakarta.persistence.Transient;

public class RankingItem {
    private String url;
    private String title;
    private String faviconPath;
    private Long countValue;
    private boolean bookmarked; // 追加

    @Transient
    private String displayFaviconUrl;

    public RankingItem(String url, String title, Long countValue) {
        this.url = url;
        this.title = title;
        this.countValue = countValue;
    }

    public RankingItem(String url, String title, String faviconPath, Long countValue) {
        this.url = url;
        this.title = title;
        this.faviconPath = faviconPath;
        this.countValue = countValue;
    }

    public boolean isBookmarked() {
        return bookmarked;
    }

    public void setBookmarked(boolean bookmarked) {
        this.bookmarked = bookmarked;
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

    public Long getCountValue() {
        return countValue;
    }

    public void setCountValue(Long countValue) {
        this.countValue = countValue;
    }

    public String getDisplayFaviconUrl() {
        return displayFaviconUrl;
    }

    public void setDisplayFaviconUrl(String displayFaviconUrl) {
        this.displayFaviconUrl = displayFaviconUrl;
    }
}
