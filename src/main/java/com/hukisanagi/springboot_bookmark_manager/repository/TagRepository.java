package com.hukisanagi.springboot_bookmark_manager.repository;

import com.hukisanagi.springboot_bookmark_manager.model.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

@Repository
public interface TagRepository extends JpaRepository<Tag, Long> {
    Optional<Tag> findByName(String name);

    @Query("SELECT COUNT(b) FROM Bookmark b JOIN b.tags t WHERE t = :tag")
    long countBookmarksByTag(Tag tag);
}
