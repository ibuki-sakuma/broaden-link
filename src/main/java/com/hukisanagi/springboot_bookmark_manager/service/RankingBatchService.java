package com.hukisanagi.springboot_bookmark_manager.service;

import com.hukisanagi.springboot_bookmark_manager.model.Bookmark;
import com.hukisanagi.springboot_bookmark_manager.model.BookmarkClickLog;
import com.hukisanagi.springboot_bookmark_manager.model.RankingCache;
import com.hukisanagi.springboot_bookmark_manager.model.RankingItem;
import com.hukisanagi.springboot_bookmark_manager.repository.BookmarkClickLogRepository;
import com.hukisanagi.springboot_bookmark_manager.repository.BookmarkRepository;
import com.hukisanagi.springboot_bookmark_manager.repository.RankingCacheRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import de.malkusch.whoisServerList.publicSuffixList.PublicSuffixList;
import de.malkusch.whoisServerList.publicSuffixList.PublicSuffixListFactory;

@Service
public class RankingBatchService {

    private static final Logger logger = LoggerFactory.getLogger(RankingBatchService.class);

    private final BookmarkRepository bookmarkRepository;
    private final RankingCacheRepository rankingCacheRepository;
    private final BookmarkClickLogRepository bookmarkClickLogRepository;
    private final PublicSuffixList publicSuffixList;
    private final StorageService storageService;

    public RankingBatchService(BookmarkRepository bookmarkRepository, RankingCacheRepository rankingCacheRepository, BookmarkClickLogRepository bookmarkClickLogRepository, StorageService storageService) {
        this.bookmarkRepository = bookmarkRepository;
        this.rankingCacheRepository = rankingCacheRepository;
        this.bookmarkClickLogRepository = bookmarkClickLogRepository;
        this.publicSuffixList = new PublicSuffixListFactory().build();
        this.storageService = storageService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        logger.info("Application ready event received. Initializing ranking cache...");
        updateRankingCache();
    }

    // 毎日午前4時にランキングを再集計
    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    public void updateRankingCache() {
        logger.info("Starting ranking cache update...");
        long startTime = System.currentTimeMillis();

        // 既存のキャッシュをクリア
        rankingCacheRepository.deleteAll();

        // ランキングファビコンをクリア
        storageService.clearRankingFavicons();

        // 各ランキングタイプごとにデータを集計し、キャッシュに保存
        updateRankingTypeCache("overallScore");
        updateRankingTypeCache("uniqueUserCount");
        updateRankingTypeCache("totalClickCount");
        updateRankingTypeCache("recentClickCount");

        // 最後にタイトルとファビコンを設定
        enrichAllRankingCacheItems();

        long endTime = System.currentTimeMillis();
        logger.info("Ranking cache update finished in {} ms.", (endTime - startTime));
    }

    private void updateRankingTypeCache(String type) {
        List<RankingItem> rankingItems;
        Pageable pageable = PageRequest.of(0, 100); // 上位100件をキャッシュ

        switch (type) {
            case "totalClickCount":
                rankingItems = bookmarkRepository.findTopBookmarksByTotalClickCount(pageable);
                break;
            case "recentClickCount":
                // 過去1ヶ月以内のクリックを対象とする
                LocalDateTime thresholdTimeRecent = LocalDateTime.now().minusMonths(1);
                rankingItems = bookmarkClickLogRepository.findRecentClicksByTotalClickCount(thresholdTimeRecent, pageable);
                break;
            case "overallScore":
                // 各ランキングデータを取得
                List<RankingItem> uniqueUserRanking = bookmarkRepository.findTopBookmarksByUniqueUserCount(Pageable.unpaged());
                List<RankingItem> totalClickRanking = bookmarkRepository.findTopBookmarksByTotalClickCount(Pageable.unpaged());
                LocalDateTime thresholdTimeOverall = LocalDateTime.now().minusMonths(1);
                List<RankingItem> recentClickRanking = bookmarkClickLogRepository.findRecentClicksByTotalClickCount(thresholdTimeOverall, Pageable.unpaged());

                // URLをキーとして各スコアをマッピング
                Map<String, OverallScoreItem> overallScores = new HashMap<>();

                // ユニークユーザー数スコア (重み: 2)
                uniqueUserRanking.forEach(item -> {
                    overallScores.computeIfAbsent(item.getUrl(), url -> new OverallScoreItem(item.getUrl(), item.getTitle(), item.getFaviconPath()))
                                 .addUniqueUserCount(item.getCountValue() * 2);
                });

                // 合計クリック数スコア (重み: 1)
                totalClickRanking.forEach(item -> {
                    overallScores.computeIfAbsent(item.getUrl(), url -> new OverallScoreItem(item.getUrl(), item.getTitle(), item.getFaviconPath()))
                                 .addTotalClickCount(item.getCountValue() * 1); // RankingItemのcountValueにSUM(totalClickCount)がマッピングされている
                });

                // 直近クリック数スコア (重み: 3)
                recentClickRanking.forEach(item -> {
                    overallScores.computeIfAbsent(item.getUrl(), url -> new OverallScoreItem(item.getUrl(), item.getTitle(), item.getFaviconPath()))
                                 .addRecentClickCount(item.getCountValue() * 3); // RankingItemのcountValueにCOUNT(bcl)がマッピングされている
                });

                // 総合スコアでソートし、RankingItemに変換
                rankingItems = overallScores.values().stream()
                        .sorted(Comparator.comparing(OverallScoreItem::getOverallScore).reversed())
                        .map(OverallScoreItem::toRankingItem)
                        .collect(Collectors.toList());
                break;
            case "uniqueUserCount":
            default:
                rankingItems = bookmarkRepository.findTopBookmarksByUniqueUserCount(pageable);
                break;
        }

        for (RankingItem item : rankingItems) {
            RankingCache rankingCache = rankingCacheRepository.findByUrl(item.getUrl())
                    .orElse(new RankingCache());

            rankingCache.setUrl(item.getUrl());
            rankingCache.setTitle(item.getUrl()); // 仮のタイトルとしてURLを設定
            rankingCache.setLastUpdated(LocalDateTime.now());

            // 各ランキングタイプに応じた値を設定
            switch (type) {
                case "uniqueUserCount":
                    rankingCache.setUniqueUserCount(item.getCountValue());
                    break;
                case "totalClickCount":
                    rankingCache.setTotalClickCount(item.getCountValue());
                    break;
                case "recentClickCount":
                    rankingCache.setRecentClickCount(item.getCountValue());
                    break;
                case "overallScore":
                    rankingCache.setOverallScore(item.getCountValue());
                    break;
            }
            rankingCacheRepository.save(rankingCache);
        }
        logger.info("Cached {} items for ranking type: {}", rankingItems.size(), type);
    }

    private void enrichAllRankingCacheItems() {
        logger.info("Starting to enrich all ranking cache items with titles and favicons...");
        List<RankingCache> allCachedItems = rankingCacheRepository.findAll();
    
        if (allCachedItems.isEmpty()) {
            logger.info("No cached items to enrich.");
            return;
        }
    
        List<String> urls = allCachedItems.stream().map(RankingCache::getUrl).collect(Collectors.toList());
        List<Bookmark> allBookmarksForUrls = bookmarkRepository.findByUrlIn(urls);
        Map<String, List<Bookmark>> bookmarksByUrl = allBookmarksForUrls.stream()
                .collect(Collectors.groupingBy(Bookmark::getUrl));
    
        for (RankingCache cacheItem : allCachedItems) {
            List<Bookmark> relatedBookmarks = bookmarksByUrl.get(cacheItem.getUrl());
    
            // タイトルを準備
            String title = prepareRepresentativeTitle(cacheItem.getUrl(), relatedBookmarks);
            cacheItem.setTitle(title);
    
            // ファビコンを準備
            String faviconPath = prepareRepresentativeFaviconPath(relatedBookmarks);
            cacheItem.setFaviconPath(faviconPath);
            
            rankingCacheRepository.save(cacheItem);
        }
        logger.info("Finished enriching {} cache items.", allCachedItems.size());
    }

    private String prepareRepresentativeFaviconPath(List<Bookmark> relatedBookmarks) {
        if (relatedBookmarks == null || relatedBookmarks.isEmpty()) {
            return null;
        }
    
        // faviconPathがnullでないブックマークを探す
        Optional<String> targetFaviconPathOpt = relatedBookmarks.stream()
                .map(Bookmark::getFaviconPath)
                .filter(path -> path != null && !path.isEmpty())
                .findFirst();

        if (targetFaviconPathOpt.isEmpty()) {
            return null;
        }
        String targetFaviconPath = targetFaviconPathOpt.get();

        String fileNameOnly = targetFaviconPath.substring(targetFaviconPath.lastIndexOf('/') + 1);
        String destinationFileName = "ranking/" + fileNameOnly;
        try {
            String copiedPath = storageService.copyFile(targetFaviconPath, destinationFileName);
            logger.info("Favicon copied successfully from {} to {}", targetFaviconPath, copiedPath);
            return copiedPath;
        } catch (Exception e) {
            logger.warn("Failed to copy favicon for ranking item {}. Error: {}", targetFaviconPath, e.getMessage());
            return null; // コピー失敗時はファビコンをnullにする
        }
    }

    private String prepareRepresentativeTitle(String targetUrl, List<Bookmark> relatedBookmarks) {
        // 1. 最も多く使われているタイトルを探す
        if (relatedBookmarks != null && !relatedBookmarks.isEmpty()) {
            Map<String, Long> titleCounts = relatedBookmarks.stream()
                    .filter(b -> b.getTitle() != null && !b.getTitle().trim().isEmpty())
                    .collect(Collectors.groupingBy(Bookmark::getTitle, Collectors.counting()));

            Optional<Map.Entry<String, Long>> mostFrequentTitle = titleCounts.entrySet().stream()
                    .max(Map.Entry.comparingByValue());

            if (mostFrequentTitle.isPresent() && mostFrequentTitle.get().getValue() > 1) {
                return mostFrequentTitle.get().getKey();
            }
        }

        // 2. URLからスクレイピングしたタイトルを試す
        String fetchedTitle = fetchTitleFromUrl(targetUrl);
        if (!fetchedTitle.startsWith("Error:")) {
            return fetchedTitle;
        }

        // 3. ドメイン名を整形したタイトル
        return getDomainAsTitle(targetUrl);
    }

    private String getDomainAsTitle(String url) {
        try {
            java.net.URL urlObj = new java.net.URL(url);
            String host = urlObj.getHost();

            // localhost の場合は特別処理
            if ("localhost".equalsIgnoreCase(host)) {
                return capitalize(host);
            }

            String publicSuffix = publicSuffixList.getPublicSuffix(host);

            String domainWithoutPublicSuffix = host; // 初期化
            if (publicSuffix != null) {
                domainWithoutPublicSuffix = host.substring(0, host.length() - publicSuffix.length());
                if (domainWithoutPublicSuffix.endsWith(".")) {
                    domainWithoutPublicSuffix = domainWithoutPublicSuffix.substring(0, domainWithoutPublicSuffix.length() - 1);
                }
            }

            // www. プレフィックスの削除
            String processedDomain = domainWithoutPublicSuffix;
            if (processedDomain.startsWith("www.")) {
                processedDomain = processedDomain.substring(4);
            }

            String[] parts = processedDomain.split("\\.");

            StringBuilder result = new StringBuilder();
            for (String part : parts) {
                result.append(capitalize(part));
            }
            return result.toString();

        } catch (java.net.MalformedURLException e) {
            logger.warn("Could not parse domain from URL: " + url, e);
            return "Unknown Title";
        }
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    private static class OverallScoreItem {
        private String url;
        private String title;
        private String faviconPath;
        private long uniqueUserScore = 0;
        private long totalClickScore = 0;
        private long recentClickScore = 0;

        public OverallScoreItem(String url, String title, String faviconPath) {
            this.url = url;
            this.title = title;
            this.faviconPath = faviconPath;
        }

        public void addUniqueUserCount(long count) {
            this.uniqueUserScore += count;
        }

        public void addTotalClickCount(long count) {
            this.totalClickScore += count;
        }

        public void addRecentClickCount(long count) {
            this.recentClickScore += count;
        }

        public long getOverallScore() {
            return uniqueUserScore + totalClickScore + recentClickScore;
        }

        public String getUrl() {
            return url;
        }

        public String getTitle() {
            return title;
        }

        public String getFaviconPath() {
            return faviconPath;
        }

        public RankingItem toRankingItem() {
            // 総合スコアをRankingItemのcountValueにマッピングして表示に利用
            return new RankingItem(url, title, faviconPath, getOverallScore());
        }
    }

    public String fetchTitleFromUrl(String url) {
        try {
            Document doc = Jsoup.connect(url).userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.36").timeout(5000).get();
            String title = doc.title();
            if (title == null || title.trim().isEmpty()) {
                return "Error: No Title Found"; // タイトルタグがない、または空の場合
            }
            return title;
        } catch (Exception e) {
            logger.warn("Could not fetch title from " + url + ". Error: " + e.getMessage());
            return "Error: Failed to Fetch Title"; // ネットワークエラーなど、取得自体が失敗した場合
        }
    }
}
