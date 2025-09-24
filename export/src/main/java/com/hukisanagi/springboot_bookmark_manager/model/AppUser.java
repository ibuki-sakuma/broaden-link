package com.hukisanagi.springboot_bookmark_manager.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import java.util.Set;
import java.util.HashSet;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String cognitoSub;

    @OneToMany(mappedBy = "appUser", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private Set<Bookmark> bookmarks = new HashSet<>();

    @OneToMany(mappedBy = "appUser", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private Set<BookmarkClickLog> bookmarkClickLogs = new HashSet<>();

    public AppUser() {
    }

    public AppUser(String cognitoSub) {
        this.cognitoSub = cognitoSub;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCognitoSub() {
        return cognitoSub;
    }

    public void setCognitoSub(String cognitoSub) {
        this.cognitoSub = cognitoSub;
    }

    public Set<Bookmark> getBookmarks() {
        return bookmarks;
    }

    public void setBookmarks(Set<Bookmark> bookmarks) {
        this.bookmarks = bookmarks;
    }

    public Set<BookmarkClickLog> getBookmarkClickLogs() {
        return bookmarkClickLogs;
    }

    public void setBookmarkClickLogs(Set<BookmarkClickLog> bookmarkClickLogs) {
        this.bookmarkClickLogs = bookmarkClickLogs;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AppUser appUser = (AppUser) o;
        return id != null && id.equals(appUser.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}