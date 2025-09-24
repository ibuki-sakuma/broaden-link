package com.hukisanagi.springboot_bookmark_manager.service;

import com.hukisanagi.springboot_bookmark_manager.model.BookmarkClickLog;
import com.hukisanagi.springboot_bookmark_manager.repository.BookmarkClickLogRepository;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import de.malkusch.whoisServerList.publicSuffixList.PublicSuffixList;
import de.malkusch.whoisServerList.publicSuffixList.PublicSuffixListFactory;

import com.hukisanagi.springboot_bookmark_manager.model.AppUser;
import com.hukisanagi.springboot_bookmark_manager.model.Bookmark;
import com.hukisanagi.springboot_bookmark_manager.model.Tag;
import com.hukisanagi.springboot_bookmark_manager.repository.AppUserRepository;
import com.hukisanagi.springboot_bookmark_manager.repository.BookmarkRepository;
import com.hukisanagi.springboot_bookmark_manager.repository.TagRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.io.InputStream;
import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import com.hukisanagi.springboot_bookmark_manager.model.RankingItem;
import com.hukisanagi.springboot_bookmark_manager.model.RankingCache;
import com.hukisanagi.springboot_bookmark_manager.repository.RankingCacheRepository;
import org.springframework.data.domain.Page;
import java.util.Map;
import java.util.HashMap;
import java.util.Comparator;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
@Transactional
public class BookmarkService {

    private static final Logger logger = LoggerFactory.getLogger(BookmarkService.class);

    private final BookmarkRepository bookmarkRepository;
    private final AppUserRepository appUserRepository;
    private final TagRepository tagRepository;
    private final BookmarkClickLogRepository bookmarkClickLogRepository;
    private final PublicSuffixList publicSuffixList;
    private final RankingCacheRepository rankingCacheRepository;
    private final StorageService storageService;

    public BookmarkService(BookmarkRepository bookmarkRepository, AppUserRepository appUserRepository, TagRepository tagRepository, BookmarkClickLogRepository bookmarkClickLogRepository, RankingCacheRepository rankingCacheRepository, StorageService storageService) {
        this.bookmarkRepository = bookmarkRepository;
        this.appUserRepository = appUserRepository;
        this.tagRepository = tagRepository;
        this.bookmarkClickLogRepository = bookmarkClickLogRepository;
        this.publicSuffixList = new PublicSuffixListFactory().build();
        this.rankingCacheRepository = rankingCacheRepository;
        this.storageService = storageService;
    }

    public Page<Bookmark> findBookmarks(AppUser appUser, String keyword, List<String> tags, Pageable pageable, Boolean showFavorites) {
        String actualKeyword = keyword;
        List<String> exactTagNames = new ArrayList<>();

        if (keyword != null && !keyword.isEmpty()) {
            // キーワードからハッシュタグを抽出
            Pattern pattern = Pattern.compile("#(\\S+)");
            Matcher matcher = pattern.matcher(keyword);
            StringBuffer sb = new StringBuffer();
            while (matcher.find()) {
                exactTagNames.add(matcher.group(1));
                matcher.appendReplacement(sb, ""); // ハッシュタグ部分を削除
            }
            matcher.appendTail(sb);
            actualKeyword = sb.toString().trim(); // 残った部分がキーワード
        }

        // tagsパラメータが渡された場合、exactTagNamesに追加
        if (tags != null && !tags.isEmpty()) {
            exactTagNames.addAll(tags);
        }

        // exactTagNamesが空の場合はnullを設定し、クエリで無視されるようにする
        List<String> finalExactTagNames = exactTagNames.isEmpty() ? null : exactTagNames;
        int finalExactTagNamesSize = (finalExactTagNames == null) ? 0 : finalExactTagNames.size();

        return bookmarkRepository.findBookmarksByAppUserAndCriteria(
                appUser,
                actualKeyword,
                finalExactTagNames,
                finalExactTagNamesSize,
                showFavorites, // showFavoritesを直接渡す
                pageable
        );
    }

    public Set<Tag> findAllTags(AppUser appUser) { // showFavoritesパラメータを削除
        // ユーザーが持つ全てのブックマークからタグを取得
        return bookmarkRepository.findBookmarksByAppUserAndCriteria(
                appUser,
                null, // keyword
                null, // exactTagNames
                0,    // exactTagNamesSize
                null, // isFavorite (常に全てのブックマークからタグを取得するためnull)
                Pageable.unpaged() // 全ページ取得
            )
            .stream()
            .flatMap(b -> b.getTags().stream())
            .collect(Collectors.toSet());
    }

    public void addBookmark(Bookmark bookmark, String tagsInput, AppUser appUser) {
        // URLのバリデーション
        try {
            new URL(bookmark.getUrl());
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid URL format: " + bookmark.getUrl(), e);
        }

        bookmark.setAppUser(appUser);
        Set<Tag> tags = parseTags(tagsInput);
        bookmark.setTags(tags);

        String normalizedUrl = normalizeUrl(bookmark.getUrl());
        bookmark.setUrl(normalizedUrl);

        // 重複チェック
        List<Bookmark> existingBookmarks = bookmarkRepository.findByAppUserAndUrl(appUser, normalizedUrl);
        if (!existingBookmarks.isEmpty()) {
            throw new IllegalArgumentException("You have already bookmarked this URL.");
        }

        // ファビコンURLの生成
        try {
            String faviconUrl = extractFaviconUrl(bookmark.getUrl());
            byte[] faviconBytes = null;

            if (faviconUrl != null) {
                faviconBytes = downloadFavicon(faviconUrl);
            }

            // Jsoupでの取得に失敗した場合、外部APIを試す
            if (faviconBytes == null) {
                logger.info("Favicon not found with Jsoup, trying external APIs for {}", bookmark.getUrl());
                faviconBytes = downloadFaviconFromExternalApis(bookmark.getUrl());
            }

            if (faviconBytes != null) {
                String fileName = appUser.getId() + "/" + getMd5Hash(bookmark.getUrl()) + ".png";
                String savedFaviconPath = storageService.saveFile(faviconBytes, fileName);
                bookmark.setFaviconPath(savedFaviconPath);
            } else {
                 bookmark.setFaviconPath(null); // 最終的に取得失敗時はnullを設定
            }
        } catch (Exception e) {
            logger.error("Failed to extract or save favicon for URL: " + bookmark.getUrl(), e);
            bookmark.setFaviconPath(null); // 取得失敗時はnullを設定
        }
        
        bookmarkRepository.save(bookmark);
    }

    public void deleteBookmark(Long id, AppUser appUser) {
        Optional<Bookmark> bookmarkOptional = bookmarkRepository.findById(id);
        bookmarkOptional.ifPresent(bookmark -> {
            if (bookmark.getAppUser().equals(appUser)) {
                // Delete favicon from storage
                if (bookmark.getFaviconPath() != null && !bookmark.getFaviconPath().isEmpty()) {
                    storageService.deleteFile(bookmark.getFaviconPath());
                }

                // 関連するクリックログを先に削除
                bookmarkClickLogRepository.deleteByBookmark(bookmark);
                Set<Tag> tagsToDeleteCheck = new HashSet<>(bookmark.getTags());
                bookmarkRepository.deleteById(id);
                deleteOrphanedTags(tagsToDeleteCheck);
            }
        });
    }

    public void deleteBookmarksByIds(List<Long> ids, AppUser appUser) {
        ids.forEach(id -> {
            Optional<Bookmark> bookmarkOptional = bookmarkRepository.findById(id);
            bookmarkOptional.ifPresent(bookmark -> {
                if (bookmark.getAppUser().equals(appUser)) {
                    // Delete favicon from storage
                    if (bookmark.getFaviconPath() != null && !bookmark.getFaviconPath().isEmpty()) {
                        storageService.deleteFile(bookmark.getFaviconPath());
                    }

                    // 関連するクリックログを先に削除
                    bookmarkClickLogRepository.deleteByBookmark(bookmark);
                    Set<Tag> tagsToDeleteCheck = new HashSet<>(bookmark.getTags());
                    bookmarkRepository.deleteById(id);
                    deleteOrphanedTags(tagsToDeleteCheck);
                }
            });
        });
    }

    public Optional<Bookmark> findBookmarkByIdAndUser(Long id, AppUser appUser) {
        return bookmarkRepository.findById(id)
                .filter(bookmark -> bookmark.getAppUser().equals(appUser));
    }

    public void updateBookmark(Long id, Bookmark updatedBookmark, String tagsInput, AppUser appUser) {
        Optional<Bookmark> bookmarkOptional = bookmarkRepository.findById(id);
        if (bookmarkOptional.isPresent()) {
            Bookmark existingBookmark = bookmarkOptional.get();
            if (existingBookmark.getAppUser().equals(appUser)) {
                Set<Tag> oldTags = new HashSet<>(existingBookmark.getTags());

                existingBookmark.setTitle(updatedBookmark.getTitle());
                String normalizedUrl = normalizeUrl(updatedBookmark.getUrl());

                if (!existingBookmark.getUrl().equals(normalizedUrl)) {
                    throw new IllegalArgumentException("URL cannot be changed.");
                }
                
                existingBookmark.setUrl(normalizedUrl);

                // 更新時に重複チェック（自分自身を除く）
                List<Bookmark> duplicateBookmarks = bookmarkRepository.findByAppUserAndUrl(appUser, normalizedUrl);
                if (!duplicateBookmarks.isEmpty()) {
                    for (Bookmark duplicate : duplicateBookmarks) {
                        if (!duplicate.getId().equals(existingBookmark.getId())) {
                            throw new IllegalArgumentException("You have already bookmarked this URL.");
                        }
                    }
                }

                Set<Tag> newTags = parseTags(tagsInput);
                existingBookmark.setTags(newTags);

                bookmarkRepository.save(existingBookmark);

                Set<Tag> tagsToCheckForOrphan = new HashSet<>(oldTags);
                tagsToCheckForOrphan.removeAll(newTags);

                deleteOrphanedTags(tagsToCheckForOrphan);
            }
        }
    }

    private String normalizeUrl(String originalUrl) {
        try {
            URL url = new URL(originalUrl);
            String protocol = url.getProtocol();
            String host = url.getHost().toLowerCase(); // ホスト名を小文字に変換
            int port = url.getPort();
            String path = url.getPath();
            String query = url.getQuery();
            String ref = url.getRef();

            // パスの末尾のスラッシュを削除
            if (path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }

            // ポート番号を再構築
            String portString = "";
            if (port != -1 && !((protocol.equals("http") && port == 80) || (protocol.equals("https") && port == 443))) {
                portString = ":" + port;
            }

            // クエリパラメータとフラグメントはそのまま維持
            String queryString = (query != null && !query.isEmpty()) ? "?" + query : "";
            String refString = (ref != null && !ref.isEmpty()) ? "#" + ref : "";

            return protocol + "://" + host + portString + path + queryString + refString;
        } catch (MalformedURLException e) {
            System.err.println("Invalid URL for normalization: " + originalUrl + " - " + e.getMessage());
            return originalUrl; // 不正なURLの場合は元のURLを返す
        }
    }

    private Set<Tag> parseTags(String tagsInput) {
        if (tagsInput == null || tagsInput.trim().isEmpty()) {
            return new HashSet<>();
        }
        return Arrays.stream(tagsInput.split(","))
                .map(String::trim)
                .filter(tagName -> !tagName.isEmpty())
                .map(tagName -> tagRepository.findByName(tagName).orElseGet(() -> tagRepository.save(new Tag(tagName))))
                .collect(Collectors.toSet());
    }

    public String tagsSetToString(Set<Tag> tags) {
        return tags.stream()
                .map(Tag::getName)
                .collect(Collectors.joining(", "));
    }

    public List<RankingItem> getTopBookmarks(Pageable pageable) {
        return rankingCacheRepository.findAll(pageable.getSort()).stream()
                .filter(item -> item.getUniqueUserCount() != null)
                .sorted(Comparator.comparing(RankingCache::getUniqueUserCount).reversed())
                .skip(pageable.getOffset())
                .limit(pageable.getPageSize())
                .map(item -> new RankingItem(item.getUrl(), item.getTitle(), item.getFaviconPath(), item.getUniqueUserCount()))
                .collect(Collectors.toList());
    }

    public List<RankingItem> getTopBookmarksByTotalClickCount(Pageable pageable) {
        return rankingCacheRepository.findAll(pageable.getSort()).stream()
                .filter(item -> item.getTotalClickCount() != null)
                .sorted(Comparator.comparing(RankingCache::getTotalClickCount).reversed())
                .skip(pageable.getOffset())
                .limit(pageable.getPageSize())
                .map(item -> new RankingItem(item.getUrl(), item.getTitle(), item.getFaviconPath(), item.getTotalClickCount()))
                .collect(Collectors.toList());
    }

    public List<RankingItem> getTopBookmarksByRecentClickCount(Pageable pageable) {
        return rankingCacheRepository.findAll(pageable.getSort()).stream()
                .filter(item -> item.getRecentClickCount() != null)
                .sorted(Comparator.comparing(RankingCache::getRecentClickCount).reversed())
                .skip(pageable.getOffset())
                .limit(pageable.getPageSize())
                .map(item -> new RankingItem(item.getUrl(), item.getTitle(), item.getFaviconPath(), item.getRecentClickCount()))
                .collect(Collectors.toList());
    }

    public List<RankingItem> getTopBookmarksByOverallScore(Pageable pageable) {
        return rankingCacheRepository.findAll(pageable.getSort()).stream()
                .filter(item -> item.getOverallScore() != null)
                .sorted(Comparator.comparing(RankingCache::getOverallScore).reversed())
                .skip(pageable.getOffset())
                .limit(pageable.getPageSize())
                .map(item -> new RankingItem(item.getUrl(), item.getTitle(), item.getFaviconPath(), item.getOverallScore()))
                .collect(Collectors.toList());
    }

    public Optional<RankingItem> getRandomPublicBookmark() {
        // 全てのRankingCacheを取得
        List<RankingCache> allRankings = rankingCacheRepository.findAll();
        
        // overallScoreがnullでないものにフィルタリング
        List<RankingCache> filteredRankings = allRankings.stream()
                                                        .filter(item -> item.getOverallScore() != null)
                                                        .collect(Collectors.toList());

        if (filteredRankings.isEmpty()) {
            return Optional.empty();
        }

        // フィルタリングされたリストからランダムに1件選択
        int randomIndex = new java.util.Random().nextInt(filteredRankings.size());
        RankingCache randomItem = filteredRankings.get(randomIndex);
        
        return Optional.of(new RankingItem(randomItem.getUrl(), randomItem.getTitle(), randomItem.getFaviconPath(), randomItem.getOverallScore()));
    }

    public Optional<RankingItem> getPublicBookmarkByUrl(String url) {
        Optional<RankingCache> cachedItem = rankingCacheRepository.findByUrl(url);
        if (cachedItem.isPresent()) {
            return Optional.of(new RankingItem(cachedItem.get().getUrl(), cachedItem.get().getTitle(), cachedItem.get().getFaviconPath(), cachedItem.get().getOverallScore()));
        } else {
            return Optional.empty();
        }
    }

    public List<String> getSimilarUrls(AppUser appUser, String inputUrl) {
        inputUrl = inputUrl.trim(); // 前後のスペースを削除
        Set<String> suggestedUrls = new LinkedHashSet<>();

        // 1. 入力URLの完全一致検索
        List<String> exactMatchUrls = bookmarkRepository.findByUrlAndUniqueUserCountGreaterThanEqual(inputUrl);
        suggestedUrls.addAll(exactMatchUrls);

        // 2. 入力URLから親パスを生成し、それらの完全一致検索
        String normalizedInputUrl = normalizeUrl(inputUrl); // 既存のnormalizeUrlを使用
        try {
            URL urlObj = new URL(normalizedInputUrl);
            String protocol = urlObj.getProtocol();
            String host = urlObj.getHost();
            int port = urlObj.getPort();
            String path = urlObj.getPath();

            StringBuilder baseUrlBuilder = new StringBuilder();
            baseUrlBuilder.append(protocol).append("://").append(host);
            if (port != -1 && !((protocol.equals("http") && port == 80) || (protocol.equals("https") && port == 443))) {
                baseUrlBuilder.append(":").append(port);
            }
            String baseHostUrl = baseUrlBuilder.toString();

            // ルートパスまで遡る
            String tempPath = path;
            while (tempPath.lastIndexOf('/') > 0) {
                tempPath = tempPath.substring(0, tempPath.lastIndexOf('/'));
                String parentUrl = baseHostUrl + tempPath;
                List<String> parentMatchUrls = bookmarkRepository.findByUrlAndUniqueUserCountGreaterThanEqual(parentUrl);
                suggestedUrls.addAll(parentMatchUrls);

            }
            // ルートパス自体も追加
            List<String> bareHostMatchUrls = bookmarkRepository.findByUrlAndUniqueUserCountGreaterThanEqual(baseHostUrl);
            suggestedUrls.addAll(bareHostMatchUrls);

        } catch (MalformedURLException e) {
            logger.warn("Malformed URL during parent path generation: " + inputUrl, e);
        }

        // 3. 入力URLでの前方一致検索
        List<String> startsWithUrls = bookmarkRepository.findByUrlStartingWithAndUniqueUserCountGreaterThanEqual(inputUrl);
        startsWithUrls.sort(Comparator.comparingInt(String::length)); // 長さでソート
        suggestedUrls.addAll(startsWithUrls);

        return suggestedUrls.stream().limit(5).collect(Collectors.toCollection(ArrayList::new));
    }

    public boolean isBookmarkedByUser(AppUser appUser, String url) {
        return !bookmarkRepository.findByAppUserAndUrl(appUser, url).isEmpty();
    }

    public void toggleFavorite(Long bookmarkId, AppUser appUser) {
        Optional<Bookmark> bookmarkOptional = bookmarkRepository.findById(bookmarkId);
        bookmarkOptional.ifPresent(bookmark -> {
            if (bookmark.getAppUser().equals(appUser)) {
                bookmark.setFavorite(!bookmark.isFavorite());
                bookmarkRepository.save(bookmark);
            }
        });
    }

    private void deleteOrphanedTags(Set<Tag> tags) {
        tags.forEach(tag -> {
            if (tagRepository.countBookmarksByTag(tag) == 0) {
                tagRepository.delete(tag);
            }
        });
    }

    public void recordBookmarkClick(Long bookmarkId, AppUser appUser) {
        Optional<Bookmark> bookmarkOptional = bookmarkRepository.findById(bookmarkId);
        bookmarkOptional.ifPresent(bookmark -> {
            bookmark.setTotalClickCount(bookmark.getTotalClickCount() + 1);
            bookmarkRepository.save(bookmark);

            // 1分以内の重複クリックを制限
            LocalDateTime oneMinuteAgo = LocalDateTime.now().minusMinutes(1);
            Optional<BookmarkClickLog> recentClick = bookmarkClickLogRepository.findTopByBookmarkAndAppUserAndClickedAtAfterOrderByClickedAtDesc(bookmark, appUser, oneMinuteAgo);

            if (recentClick.isEmpty()) {
                // BookmarkClickLogにも記録
                BookmarkClickLog clickLog = new BookmarkClickLog(bookmark, appUser, LocalDateTime.now());
                bookmarkClickLogRepository.save(clickLog);
            } else {
                logger.info("Duplicate click from user {} for bookmark {} within 1 minute. Skipping log and totalClickCount update.", appUser.getCognitoSub(), bookmark.getTitle());
            }
        });
    }

    @Scheduled(cron = "0 0 3 * * *") // 毎日午前3時に実行
    public void cleanupOldClickLogs() {
        logger.info("Cleaning up old click logs...");
        LocalDateTime threshold = LocalDateTime.now().minusMonths(1);
        List<BookmarkClickLog> oldLogs = bookmarkClickLogRepository.findByClickedAtBefore(threshold);
        bookmarkClickLogRepository.deleteAll(oldLogs);
        logger.info("Cleaned up {} old click logs.", oldLogs.size());
    }

    public String fetchTitleFromUrl(String url) {
        try {
            Document doc = Jsoup.connect(url)
                                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.36")
                                .timeout(5000).get();
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

    private String extractFaviconUrl(String pageUrl) {
        String faviconPath = null;
        try {
            org.jsoup.nodes.Document doc = org.jsoup.Jsoup.connect(pageUrl)
                                                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.36")
                                                    .timeout(5000).get(); // タイムアウトを5秒に設定
            
            // <link rel="icon">, <link rel="shortcut icon">, <link rel="apple-touch-icon">などを探す
            org.jsoup.select.Elements iconLinks = doc.select("link[rel~=(?i)^(shortcut|icon|apple-touch-icon)]");
            for (org.jsoup.nodes.Element link : iconLinks) {
                String href = link.attr("abs:href"); // 絶対URLを取得
                if (!href.isEmpty()) {
                    faviconPath = href;
                    break;
                }
            }

            // 見つからない場合、/favicon.icoを試す
            if (faviconPath == null || faviconPath.isEmpty()) {
                URL urlObj = new URL(pageUrl);
                faviconPath = urlObj.getProtocol() + "://" + urlObj.getHost() + "/favicon.ico";
            }

        } catch (Exception e) {
            logger.warn("Could not extract favicon from " + pageUrl + ". Error: " + e.getMessage());
            // エラー時はデフォルトのfavicon.icoを試す
            try {
                URL urlObj = new URL(pageUrl);
                faviconPath = urlObj.getProtocol() + "://" + urlObj.getHost() + "/favicon.ico";
            } catch (MalformedURLException ex) {
                logger.error("Malformed URL when trying default favicon: " + pageUrl, ex);
                faviconPath = null;
            }
        }
        return faviconPath;
    }

    private byte[] downloadFavicon(String faviconPath) {
        logger.info("Attempting to download favicon from: {}", faviconPath);
        if (faviconPath == null || faviconPath.isEmpty()) {
            logger.warn("Favicon URL is null or empty. Skipping download.");
            return null;
        }

        try {
            URL url = new URL(faviconPath);
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(5000); // 5 seconds
            connection.setReadTimeout(5000);    // 5 seconds

            try (InputStream in = connection.getInputStream()) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    baos.write(buffer, 0, bytesRead);
                }
                byte[] faviconBytes = baos.toByteArray();

                if (faviconPath.toLowerCase().endsWith(".svg")) {
                    PNGTranscoder transcoder = new PNGTranscoder();
                    TranscoderInput input = new TranscoderInput(new ByteArrayInputStream(faviconBytes));
                    try (ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream()) {
                        TranscoderOutput output = new TranscoderOutput(pngOutputStream);
                        transcoder.transcode(input, output);
                        return pngOutputStream.toByteArray();
                    }
                } else {
                    try (InputStream imageCheckStream = new ByteArrayInputStream(faviconBytes)) {
                        if (ImageIO.read(imageCheckStream) == null) {
                            logger.warn("Downloaded data from {} is not a valid image.", faviconPath);
                            return null;
                        }
                    }
                    return faviconBytes;
                }
            }
        } catch (Exception e) {
            logger.error("Failed to download and process favicon from " + faviconPath, e);
            return null;
        }
    }

    public String getFaviconUrl(String fileName) {
        return storageService.getFileUrl(fileName);
    }

    private String getMd5Hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : messageDigest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not found", e);
        }
    }

    private byte[] downloadFaviconFromExternalApis(String pageUrl) {
        String domain;
        try {
            domain = getDomainName(pageUrl);
        } catch (MalformedURLException e) {
            logger.error("Invalid URL for external API favicon fetching: {}", pageUrl, e);
            return null;
        }

        // 1. Google Favicon API
        byte[] favicon = tryDownloadFavicon("https://www.google.com/s2/favicons?domain=" + domain + "&sz=128");
        if (favicon != null) {
            logger.info("Successfully downloaded favicon from Google API for {}", pageUrl);
            return favicon;
        }
        
        logger.warn("Failed to download favicon from all external APIs for {}", pageUrl);
        return null;
    }

    private byte[] tryDownloadFavicon(String apiUrl) {
        try {
            logger.info("Trying to download favicon from API: {}", apiUrl);
            URL url = new URL(apiUrl);
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(3000); // 3 seconds
            connection.setReadTimeout(3000);    // 3 seconds
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.36");

            try (InputStream in = connection.getInputStream()) {
                 ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    baos.write(buffer, 0, bytesRead);
                }
                byte[] faviconBytes = baos.toByteArray();

                // Check if the downloaded content is a valid image
                try (InputStream imageCheckStream = new ByteArrayInputStream(faviconBytes)) {
                    if (ImageIO.read(imageCheckStream) == null) {
                        logger.warn("Downloaded data from {} is not a valid image.", apiUrl);
                        return null;
                    }
                }
                return faviconBytes;
            }
        } catch (Exception e) {
            logger.warn("Failed to download favicon from API: {}. Error: {}", apiUrl, e.getMessage());
            return null;
        }
    }

    private String getDomainName(String pageUrl) throws MalformedURLException {
        URL url = new URL(pageUrl);
        return url.getHost();
    }
}
