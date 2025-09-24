package com.hukisanagi.springboot_bookmark_manager.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.CascadeType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;
import jakarta.persistence.Transient;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.HashSet;
import java.util.Set;

@Entity
public class Bookmark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @NotBlank
    private String title;
    @NotBlank @URL
    @Column(length = 2048)
    private String url;
    @Column(length = 2048)
    private String faviconPath;
    
    private long totalClickCount = 0;

    private boolean isFavorite = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "app_user_id")
    @JsonIgnore
    private AppUser appUser;

    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(name = "bookmark_tag",
            joinColumns = @JoinColumn(name = "bookmark_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new HashSet<>();

    @OneToMany(mappedBy = "bookmark", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private Set<BookmarkClickLog> clickLogs = new HashSet<>();

    public Bookmark() {
    }

    public Bookmark(Long id, String title, String url) {
        this.id = id;
        this.title = title;
        this.url = url;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getFaviconPath() {
        return faviconPath;
    }

    public void setFaviconPath(String faviconPath) {
        this.faviconPath = faviconPath;
    }

    public AppUser getAppUser() {
        return appUser;
    }

    public void setAppUser(AppUser appUser) {
        this.appUser = appUser;
    }

    public Set<Tag> getTags() {
        return tags;
    }

    public void setTags(Set<Tag> tags) {
        this.tags = tags;
    }

    public long getTotalClickCount() {
        return totalClickCount;
    }

    public void setTotalClickCount(long totalClickCount) {
        this.totalClickCount = totalClickCount;
    }

    public boolean isFavorite() {
        return isFavorite;
    }

    public void setFavorite(boolean favorite) {
        isFavorite = favorite;
    }

    public Set<BookmarkClickLog> getClickLogs() {
        return clickLogs;
    }

    public void setClickLogs(Set<BookmarkClickLog> clickLogs) {
        this.clickLogs = clickLogs;
    }

    @Transient
    private String displayFaviconUrl;

    public String getDisplayFaviconUrl() {
        return displayFaviconUrl;
    }

    public void setDisplayFaviconUrl(String displayFaviconUrl) {
        this.displayFaviconUrl = displayFaviconUrl;
    }
}