package com.hukisanagi.springboot_bookmark_manager.repository;

import com.hukisanagi.springboot_bookmark_manager.model.BookmarkClickLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import com.hukisanagi.springboot_bookmark_manager.model.RankingItem;

@Repository
public interface BookmarkClickLogRepository extends JpaRepository<BookmarkClickLog, Long> {

    // 直近のクリック数を集計するクエリ
    @Query("SELECT new com.hukisanagi.springboot_bookmark_manager.model.RankingItem(b.url, MIN(b.title), MIN(b.faviconPath), COUNT(bcl)) " +
           "FROM BookmarkClickLog bcl JOIN bcl.bookmark b " +
           "WHERE bcl.clickedAt >= :thresholdTime " +
           "GROUP BY b.url " +
           "HAVING COUNT(DISTINCT bcl.appUser) > 1 " +
           "ORDER BY COUNT(bcl) DESC")
    List<RankingItem> findRecentClicksByTotalClickCount(LocalDateTime thresholdTime, Pageable pageable);

    List<BookmarkClickLog> findByClickedAtBefore(LocalDateTime thresholdTime);

    Optional<BookmarkClickLog> findTopByBookmarkAndAppUserAndClickedAtAfterOrderByClickedAtDesc(com.hukisanagi.springboot_bookmark_manager.model.Bookmark bookmark, com.hukisanagi.springboot_bookmark_manager.model.AppUser appUser, LocalDateTime clickedAt);

    void deleteByBookmark(com.hukisanagi.springboot_bookmark_manager.model.Bookmark bookmark);
}
