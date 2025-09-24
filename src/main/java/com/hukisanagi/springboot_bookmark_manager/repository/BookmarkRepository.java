package com.hukisanagi.springboot_bookmark_manager.repository;

import com.hukisanagi.springboot_bookmark_manager.model.AppUser;
import com.hukisanagi.springboot_bookmark_manager.model.Bookmark;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.hukisanagi.springboot_bookmark_manager.model.RankingItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {
    @Query("SELECT b FROM Bookmark b LEFT JOIN b.tags t " +
           "WHERE b.appUser = :appUser " +
           "AND (:keyword IS NULL OR :keyword = '' OR LOWER(b.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(b.url) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(t.name) LIKE LOWER(CONCAT('%', :keyword, '%')))" +
           "AND (:exactTagNames IS NULL OR b.id IN (SELECT b2.id FROM Bookmark b2 JOIN b2.tags t2 WHERE t2.name IN :exactTagNames GROUP BY b2.id HAVING COUNT(DISTINCT t2.name) = :exactTagNamesSize))" +
           "AND (:isFavorite IS NULL OR b.isFavorite = :isFavorite) " +
           "GROUP BY b.id")
    Page<Bookmark> findBookmarksByAppUserAndCriteria(@Param("appUser") AppUser appUser,
                                                      @Param("keyword") String keyword,
                                                      @Param("exactTagNames") java.util.Collection<String> exactTagNames,
                                                      @Param("exactTagNamesSize") int exactTagNamesSize,
                                                      @Param("isFavorite") Boolean isFavorite,
                                                      Pageable pageable);

    @Query("SELECT new com.hukisanagi.springboot_bookmark_manager.model.RankingItem(b.url, MIN(b.title), MIN(b.faviconPath), COUNT(DISTINCT b.appUser)) " +
           "FROM Bookmark b " +
           "GROUP BY b.url " +
           "HAVING COUNT(DISTINCT b.appUser) > 1 " +
           "ORDER BY COUNT(DISTINCT b.appUser) DESC")
    List<RankingItem> findTopBookmarksByUniqueUserCount(Pageable pageable);

    @Query("SELECT new com.hukisanagi.springboot_bookmark_manager.model.RankingItem(b.url, MIN(b.title), MIN(b.faviconPath), SUM(b.totalClickCount)) " +
           "FROM Bookmark b " +
           "GROUP BY b.url " +
           "HAVING COUNT(DISTINCT b.appUser) > 1 " +
           "ORDER BY SUM(b.totalClickCount) DESC")
    List<RankingItem> findTopBookmarksByTotalClickCount(Pageable pageable);

    List<Bookmark> findByAppUserAndUrl(AppUser appUser, String url);

    List<Bookmark> findByAppUserAndUrlContaining(AppUser appUser, String urlKeyword);

    @Query("SELECT COUNT(DISTINCT b.url) FROM Bookmark b WHERE b.url IN (SELECT b2.url FROM Bookmark b2 GROUP BY b2.url HAVING COUNT(DISTINCT b2.appUser) > 1)")
    Long countPublicBookmarks();

    @Query(value = "SELECT new com.hukisanagi.springboot_bookmark_manager.model.RankingItem(b.url, MIN(b.title), MIN(b.faviconPath), COUNT(DISTINCT b.appUser)) " +
                   "FROM Bookmark b " +
                   "GROUP BY b.url " +
                   "HAVING COUNT(DISTINCT b.appUser) > 1 " +
                   "ORDER BY RAND()", // H2DBの場合のランダム関数
           countQuery = "SELECT COUNT(DISTINCT b.url) FROM Bookmark b GROUP BY b.url HAVING COUNT(DISTINCT b.appUser) > 1")
    Page<RankingItem> findRandomPublicBookmarks(Pageable pageable);

    List<Bookmark> findByUrlIn(List<String> urls);
    
    @Query("SELECT b.url FROM Bookmark b WHERE b.url = :url GROUP BY b.url HAVING COUNT(DISTINCT b.appUser) >= 2")
    List<String> findByUrlAndUniqueUserCountGreaterThanEqual(@Param("url") String url);

    @Query("SELECT b.url FROM Bookmark b WHERE b.url LIKE CONCAT(:urlPrefix, '%') GROUP BY b.url HAVING COUNT(DISTINCT b.appUser) >= 2")
    List<String> findByUrlStartingWithAndUniqueUserCountGreaterThanEqual(@Param("urlPrefix") String urlPrefix);
}
