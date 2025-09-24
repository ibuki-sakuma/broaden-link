package com.hukisanagi.springboot_bookmark_manager.controller;

import com.hukisanagi.springboot_bookmark_manager.model.AppUser;
import com.hukisanagi.springboot_bookmark_manager.model.Bookmark;
import com.hukisanagi.springboot_bookmark_manager.model.RankingItem;
import com.hukisanagi.springboot_bookmark_manager.repository.AppUserRepository;
import com.hukisanagi.springboot_bookmark_manager.repository.RankingCacheRepository;
import com.hukisanagi.springboot_bookmark_manager.service.BookmarkService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Optional;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class BookmarkController {

    private static final Logger logger = LoggerFactory.getLogger(BookmarkController.class);

    private final BookmarkService bookmarkService;
    private final AppUserRepository appUserRepository;
    private final RankingCacheRepository rankingCacheRepository;

    public BookmarkController(BookmarkService bookmarkService, AppUserRepository appUserRepository, RankingCacheRepository rankingCacheRepository) {
        this.bookmarkService = bookmarkService;
        this.appUserRepository = appUserRepository;
        this.rankingCacheRepository = rankingCacheRepository;
    }

    private AppUser getOrCreateUser(OidcUser oidcUser) {
        if (oidcUser == null) {
            throw new IllegalStateException("User not authenticated");
        }
        String cognitoSub = oidcUser.getSubject();
        return appUserRepository.findByCognitoSub(cognitoSub)
                .orElseGet(() -> {
                    AppUser newUser = new AppUser(cognitoSub);
                    return appUserRepository.save(newUser);
                });
    }

    @GetMapping("/")
    public String index(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) List<String> tags,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(defaultValue = "id,asc") String sort,
            @RequestParam(required = false) Boolean showFavorites,
            Model model, @AuthenticationPrincipal OidcUser oidcUser) {

        AppUser appUser = getOrCreateUser(oidcUser);

        Sort sortOrder = Sort.by(Sort.Direction.fromString(sort.split(",")[1]), sort.split(",")[0]);
        Pageable pageable = PageRequest.of(page, size, sortOrder);

        Page<Bookmark> bookmarkPage = bookmarkService.findBookmarks(appUser, keyword, tags, pageable, showFavorites);

        bookmarkPage.getContent().forEach(bookmark -> {
            if (bookmark.getFaviconPath() != null && !bookmark.getFaviconPath().isEmpty()) {
                bookmark.setDisplayFaviconUrl(bookmarkService.getFaviconUrl(bookmark.getFaviconPath()));
            }
        });

        model.addAttribute("bookmarkPage", bookmarkPage);
        model.addAttribute("bookmarks", bookmarkPage.getContent());
        model.addAttribute("keyword", keyword);
        model.addAttribute("tags", tags);
        model.addAttribute("currentPage", page);
        model.addAttribute("pageSize", size);
        model.addAttribute("sort", sort);
        model.addAttribute("totalElements", bookmarkPage.getTotalElements());
        model.addAttribute("numberOfElements", bookmarkPage.getNumberOfElements());
        model.addAttribute("showFavorites", showFavorites);

        // RedirectAttributesから渡されたメッセージをModelに追加
        if (model.containsAttribute("error")) {
            model.addAttribute("error", model.getAttribute("error"));
        }
        if (model.containsAttribute("success")) {
            model.addAttribute("success", model.getAttribute("success"));
        }

        // RedirectAttributesから渡されたbookmarkとtagsInputをModelに追加
        if (model.containsAttribute("bookmark")) {
            model.addAttribute("bookmark", model.getAttribute("bookmark"));
        } else {
            model.addAttribute("bookmark", new Bookmark()); // デフォルトのBookmarkオブジェクト
        }
        
        if (model.containsAttribute("tagsInput")) {
            model.addAttribute("tagsInput", model.getAttribute("tagsInput"));
        } else {
            model.addAttribute("tagsInput", ""); // デフォルトの空文字列
        }

        // editBookmarkId を Model に追加
        if (model.containsAttribute("editBookmarkId")) {
            model.addAttribute("editBookmarkId", model.getAttribute("editBookmarkId"));
        }

        model.addAttribute("allTags", bookmarkService.findAllTags(appUser));
        model.addAttribute("activePage", "home");

        return "index";
    }

    @PostMapping("/add")
    public String addBookmark(@Valid Bookmark bookmark, BindingResult bindingResult, @RequestParam String tagsInput, @AuthenticationPrincipal OidcUser oidcUser, RedirectAttributes redirectAttributes) {
        AppUser appUser = getOrCreateUser(oidcUser);
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "入力内容にエラーがあります。");
            redirectAttributes.addFlashAttribute("bookmark", bookmark); // 入力値を保持
            redirectAttributes.addFlashAttribute("tagsInput", tagsInput); // タグ入力値を保持
            return "redirect:/";
        }
        try {
            bookmarkService.addBookmark(bookmark, tagsInput, appUser);
            redirectAttributes.addFlashAttribute("success", "ブックマークが追加されました。");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            redirectAttributes.addFlashAttribute("bookmark", bookmark); // 入力値を保持
            redirectAttributes.addFlashAttribute("tagsInput", tagsInput); // タグ入力値を保持
        }
        return "redirect:/";
    }

    @PostMapping("/delete")
    public String deleteBookmark(@RequestParam Long id, @AuthenticationPrincipal OidcUser oidcUser) {
        AppUser appUser = getOrCreateUser(oidcUser);
        bookmarkService.deleteBookmark(id, appUser);
        return "redirect:/";
    }

    @GetMapping("/api/bookmarks/{id}")
    @ResponseBody
    public Bookmark getBookmarkById(@PathVariable Long id, @AuthenticationPrincipal OidcUser oidcUser) {
        AppUser appUser = getOrCreateUser(oidcUser);
        return bookmarkService.findBookmarkByIdAndUser(id, appUser)
                .orElseThrow(() -> new IllegalArgumentException("Bookmark not found or not authorized"));
    }

    @PostMapping("/update")
    public String updateBookmark(@RequestParam Long id, @Valid Bookmark updatedBookmark, BindingResult bindingResult, @RequestParam String tagsInput, @AuthenticationPrincipal OidcUser oidcUser, RedirectAttributes redirectAttributes) {
        AppUser appUser = getOrCreateUser(oidcUser);        
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "入力内容にエラーがあります。");
            redirectAttributes.addFlashAttribute("bookmark", updatedBookmark); // 入力値を保持
            redirectAttributes.addFlashAttribute("tagsInput", tagsInput); // タグ入力値を保持
            redirectAttributes.addFlashAttribute("editBookmarkId", id); // 編集対象のIDを渡す
            return "redirect:/";
        }
        try {
            bookmarkService.updateBookmark(id, updatedBookmark, tagsInput, appUser);
            redirectAttributes.addFlashAttribute("success", "ブックマークが更新されました。");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            redirectAttributes.addFlashAttribute("bookmark", updatedBookmark); // 入力値を保持
            redirectAttributes.addFlashAttribute("tagsInput", tagsInput); // タグ入力値を保持
            redirectAttributes.addFlashAttribute("editBookmarkId", id); // 編集対象のIDを渡す
        }
        return "redirect:/";
    }

    @PostMapping("/delete/batch")
    public String deleteSelectedBookmarks(@RequestParam("selectedBookmarks") List<Long> selectedBookmarks, @AuthenticationPrincipal OidcUser oidcUser) {
        AppUser appUser = getOrCreateUser(oidcUser);
        bookmarkService.deleteBookmarksByIds(selectedBookmarks, appUser);
        return "redirect:/";
    }

    @GetMapping("/ranking")
    public String showRanking(Model model, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "100") int size, @RequestParam(defaultValue = "overallScore") String type, @AuthenticationPrincipal OidcUser oidcUser) {
        Pageable pageable = PageRequest.of(page, size);
        List<RankingItem> ranking;

        switch (type) {
            case "totalClickCount":
                ranking = bookmarkService.getTopBookmarksByTotalClickCount(pageable);
                break;
            case "recentClickCount":
                ranking = bookmarkService.getTopBookmarksByRecentClickCount(pageable);
                break;
            case "overallScore":
                ranking = bookmarkService.getTopBookmarksByOverallScore(pageable);
                break;
            case "uniqueUserCount":
            default:
                ranking = bookmarkService.getTopBookmarks(pageable);
                break;
        }

        ranking.forEach(item -> {
            if (item.getFaviconPath() != null && !item.getFaviconPath().isEmpty()) {
                item.setDisplayFaviconUrl(bookmarkService.getFaviconUrl(item.getFaviconPath()));
            }
        });

        if (oidcUser != null) {
            AppUser appUser = getOrCreateUser(oidcUser);
            ranking.forEach(item -> {
                item.setBookmarked(bookmarkService.isBookmarkedByUser(appUser, item.getUrl()));
            });
        }
        
        model.addAttribute("ranking", ranking);
        model.addAttribute("currentPage", page);
        model.addAttribute("pageSize", size);
        model.addAttribute("activePage", "ranking");
        model.addAttribute("type", type);
        model.addAttribute("publicBookmarkCount", rankingCacheRepository.count());

        if (model.containsAttribute("bookmark")) {
            model.addAttribute("bookmark", model.getAttribute("bookmark"));
        } else {
            model.addAttribute("bookmark", new Bookmark()); // デフォルトのBookmarkオブジェクト
        }
        
        if (model.containsAttribute("tagsInput")) {
            model.addAttribute("tagsInput", model.getAttribute("tagsInput"));
        } else {
            model.addAttribute("tagsInput", ""); // デフォルトの空文字列
        }

        return "ranking";
    }

    @GetMapping("/api/suggest-urls")
    @ResponseBody
    public List<String> suggestUrls(@RequestParam String inputUrl, @AuthenticationPrincipal OidcUser oidcUser) {
        AppUser appUser = getOrCreateUser(oidcUser);
        return bookmarkService.getSimilarUrls(appUser, inputUrl);
    }

    @PostMapping("/add-from-ranking")
    public String addBookmarkFromRanking(@Valid Bookmark bookmark, BindingResult bindingResult, @RequestParam(required = false) String tagsInput, @AuthenticationPrincipal OidcUser oidcUser, RedirectAttributes redirectAttributes) {
        AppUser appUser = getOrCreateUser(oidcUser);

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "入力内容にエラーがあります。");
            redirectAttributes.addFlashAttribute("bookmark", bookmark);
            redirectAttributes.addFlashAttribute("tagsInput", tagsInput);
            return "redirect:/ranking";
        }

        try {
            bookmarkService.addBookmark(bookmark, tagsInput, appUser);
            redirectAttributes.addFlashAttribute("success", "ブックマークが追加されました。");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            redirectAttributes.addFlashAttribute("bookmark", bookmark);
            redirectAttributes.addFlashAttribute("tagsInput", tagsInput);
        }
        return "redirect:/ranking";
    }

    @PostMapping("/add-from-broaden")
    public String addBookmarkFromBroaden(@Valid Bookmark bookmark, BindingResult bindingResult, @RequestParam(required = false) String tagsInput, @AuthenticationPrincipal OidcUser oidcUser, RedirectAttributes redirectAttributes) {
        AppUser appUser = getOrCreateUser(oidcUser);

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "入力内容にエラーがあります。");
            redirectAttributes.addFlashAttribute("bookmark", bookmark);
            redirectAttributes.addFlashAttribute("tagsInput", tagsInput);
            redirectAttributes.addAttribute("errorUrl", bookmark.getUrl()); 
            return "redirect:/broaden";
        }

        try {
            bookmarkService.addBookmark(bookmark, tagsInput, appUser);
            redirectAttributes.addFlashAttribute("success", "ブックマークが追加されました。");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            redirectAttributes.addFlashAttribute("bookmark", bookmark);
            redirectAttributes.addFlashAttribute("tagsInput", tagsInput);
        redirectAttributes.addAttribute("errorUrl", bookmark.getUrl()); 
        }
        return "redirect:/broaden";
    }

    @PostMapping("/api/bookmark/click")
    @ResponseBody
    public String recordBookmarkClick(@RequestParam Long bookmarkId, @AuthenticationPrincipal OidcUser oidcUser) {
        if (oidcUser == null) {
            return "Unauthorized";
        }
        AppUser appUser = getOrCreateUser(oidcUser);
        bookmarkService.recordBookmarkClick(bookmarkId, appUser);
        return "OK";
    }

    @PostMapping("/api/bookmark/toggle-favorite")
    @ResponseBody
    public String toggleFavorite(@RequestParam Long bookmarkId, @AuthenticationPrincipal OidcUser oidcUser) {
        if (oidcUser == null) {
            return "Unauthorized";
        }
        AppUser appUser = getOrCreateUser(oidcUser);
        bookmarkService.toggleFavorite(bookmarkId, appUser);
        return "OK";
    }

    @GetMapping("/api/fetch-title")
    @ResponseBody
    public String fetchTitle(@RequestParam String url) {
        return bookmarkService.fetchTitleFromUrl(url);
    }

    @GetMapping("/broaden")
    public String showBroaden(Model model, @AuthenticationPrincipal OidcUser oidcUser, @RequestParam(required = false) String errorUrl) {
        Optional<RankingItem> broadenItemOptional;

        if (errorUrl != null && !errorUrl.isEmpty()) {
            // エラー時のURLが指定されていれば、そのブックマークを取得
            broadenItemOptional = bookmarkService.getPublicBookmarkByUrl(errorUrl);
        } else {
            // それ以外はランダムなブックマークを取得
            broadenItemOptional = bookmarkService.getRandomPublicBookmark();
        }

        broadenItemOptional.ifPresent(item -> {
            if (oidcUser != null) {
                AppUser appUser = getOrCreateUser(oidcUser);
                item.setBookmarked(bookmarkService.isBookmarkedByUser(appUser, item.getUrl()));
            }
            if (item.getFaviconPath() != null && !item.getFaviconPath().isEmpty()) {
                item.setDisplayFaviconUrl(bookmarkService.getFaviconUrl(item.getFaviconPath()));
            }
            model.addAttribute("broadenItem", item); // 単一のアイテムを渡す
        });

        model.addAttribute("activePage", "broaden"); // ナビゲーションバーのハイライト用
        model.addAttribute("publicBookmarkCount", rankingCacheRepository.count()); // 公開ブックマーク数を追加

        if (model.containsAttribute("bookmark")) {
            model.addAttribute("bookmark", model.getAttribute("bookmark"));
        } else {
            model.addAttribute("bookmark", new Bookmark()); // デフォルトのBookmarkオブジェクト
        }
        
        if (model.containsAttribute("tagsInput")) {
            model.addAttribute("tagsInput", model.getAttribute("tagsInput"));
        } else {
            model.addAttribute("tagsInput", ""); // デフォルトの空文字列
        }

        return "broaden";
    }
}
