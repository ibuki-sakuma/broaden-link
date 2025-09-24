package com.hukisanagi.springboot_bookmark_manager.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;

@Entity
public class BookmarkClickLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "bookmark_id")
    @JsonIgnore
    private Bookmark bookmark;

    @ManyToOne
    @JoinColumn(name = "app_user_id")
    @JsonIgnore
    private AppUser appUser;

    private LocalDateTime clickedAt;

    public BookmarkClickLog() {
    }

    public BookmarkClickLog(Bookmark bookmark, AppUser appUser, LocalDateTime clickedAt) {
        this.bookmark = bookmark;
        this.appUser = appUser;
        this.clickedAt = clickedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Bookmark getBookmark() {
        return bookmark;
    }

    public void setBookmark(Bookmark bookmark) {
        this.bookmark = bookmark;
    }

    public AppUser getAppUser() {
        return appUser;
    }

    public void setAppUser(AppUser appUser) {
        this.appUser = appUser;
    }

    public LocalDateTime getClickedAt() {
        return clickedAt;
    }

    public void setClickedAt(LocalDateTime clickedAt) {
        this.clickedAt = clickedAt;
    }
}
