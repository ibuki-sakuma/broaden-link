package com.hukisanagi.springboot_bookmark_manager.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import java.util.HashSet;
import java.util.Set;

@Entity
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;

    @ManyToMany(mappedBy = "tags")
    @JsonIgnore
    private Set<Bookmark> bookmarks = new HashSet<>();

    public Tag() {
    }

    public Tag(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<Bookmark> getBookmarks() {
        return bookmarks;
    }

    public void setBookmarks(Set<Bookmark> bookmarks) {
        this.bookmarks = bookmarks;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Tag tag = (Tag) o;
        // IDがnullでない場合はIDで比較
        if (id != null && tag.id != null) {
            return id.equals(tag.id);
        }
        // IDがnullの場合はnameで比較
        return name != null && name.equals(tag.name);
    }

    @Override
    public int hashCode() {
        // IDがnullでない場合はIDのハッシュコード、そうでない場合はnameのハッシュコード
        return id != null ? id.hashCode() : (name != null ? name.hashCode() : 0);
    }
}
