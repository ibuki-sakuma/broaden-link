document.addEventListener('DOMContentLoaded', function () {
    // ナビゲーションバーの高さに合わせてbodyのpadding-topを調整
    const navbar = document.querySelector('.navbar');
    if (navbar) {
        document.body.style.paddingTop = navbar.offsetHeight + 'px';
    }

    // --- タイトル自動取得ロジック ---
    const setupTitleFetching = (modal) => {
        const urlInput = modal.querySelector('input[name="url"]');
        const titleInput = modal.querySelector('input[name="title"]');
        const fetchButton = modal.querySelector('.btn-outline-secondary');

        // イベントリスナーが既に設定されているかチェック
        if (fetchButton.dataset.listenerAdded === 'true') {
            return; // 既に設定済みなら何もしない
        }

        const fetchTitle = async () => {
            const url = urlInput.value;
            if (!url || !url.startsWith('http')) {
                return;
            }
            fetchButton.disabled = true;
            const originalIcon = fetchButton.innerHTML;
            fetchButton.innerHTML = '<span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span>';
            try {
                const response = await fetch(`/api/fetch-title?url=${encodeURIComponent(url)}`);
                if (response.ok) {
                    const title = await response.text();
                    if (title) {
                        titleInput.value = title;
                    }
                }
            } catch (error) {
                // エラー発生時、特別な処理は行わない
            } finally {
                fetchButton.disabled = false;
                fetchButton.innerHTML = originalIcon;
            }
        };

        fetchButton.addEventListener('click', fetchTitle);
        fetchButton.dataset.listenerAdded = 'true'; // リスナー設定済みフラグをセット
    };
    // --- タイトル自動取得ロジックここまで ---

    // --- タグ入力コンポーネントロジック ---
    const setupTagInput = (modalElement, inputId, containerId, hiddenInputId) => {
        const tagInput = modalElement.querySelector(`#${inputId}`);
        const tagsContainer = modalElement.querySelector(`#${containerId}`);
        const hiddenTagsInput = modalElement.querySelector(`#${hiddenInputId}`);

        if (!tagInput) {
            return;
        }
        if (!tagsContainer) {
            return;
        }
        if (!hiddenTagsInput) {
            return;
        }

        const renderTags = () => {
            tagsContainer.innerHTML = '';
            tags.forEach(tag => {
                const tagElement = document.createElement('a');
                tagElement.classList.add('badge', 'rounded-pill', 'bg-secondary-subtle', 'text-dark', 'ms-1', 'mb-1', 'text-decoration-none', 'bookmark-tag-small');
                tagElement.innerHTML = `${tag}  <span class="tag-remove-btn" data-tag="${tag}" aria-label="Remove tag"><i class="bi bi-x"></i></span>`;
                tagsContainer.appendChild(tagElement);
            });
            hiddenTagsInput.value = tags.join(',');
        };

        // 初期化時に既存のタグを読み込む
        let tags = hiddenTagsInput.value ? hiddenTagsInput.value.split(',').map(tag => tag.trim()).filter(tag => tag !== '') : [];
        renderTags();

        tagInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' || e.key === ',') {
                e.preventDefault(); // ここでデフォルトのEnterキー挙動を阻止
                const newTag = tagInput.value.trim();
                if (newTag && !tags.includes(newTag)) {
                    tags.push(newTag);
                    tagInput.value = '';
                    renderTags();
                } else if (newTag) {
                    // 重複している場合は何もしない
                }
            }
        });

        tagsContainer.addEventListener('click', (e) => {
            const removeButton = e.target.closest('.tag-remove-btn');
            if (removeButton) {
                const tagToRemove = removeButton.dataset.tag;
                tags = tags.filter(tag => tag !== tagToRemove);
                renderTags();
            }
        });

        // モーダルが閉じられたときにタグをクリア
        modalElement.addEventListener('hidden.bs.modal', () => {
            tags = [];
            renderTags();
        });
    };
    // --- タグ入力コンポーネントロジックここまで ---

    const urlInput = document.getElementById('url');
    const urlSuggestionsDiv = document.getElementById('urlSuggestions');
    let debounceTimer;

    if (urlInput && urlSuggestionsDiv) {
        const fetchUrlSuggestions = () => {
            const inputUrl = urlInput.value;
            if (inputUrl.length > 2) { // ある程度の長さが入力されてから検索
                fetch(`/api/suggest-urls?inputUrl=${encodeURIComponent(inputUrl)}`)
                    .then(response => response.json())
                    .then(suggestions => {
                        urlSuggestionsDiv.innerHTML = ''; // Clear previous suggestions
                        if (suggestions.length > 0) {
                            suggestions.forEach(suggestion => {
                                const suggestionItem = document.createElement('a');
                                suggestionItem.href = '#';
                                suggestionItem.classList.add('list-group-item', 'list-group-item-action');
                                suggestionItem.textContent = suggestion;
                                suggestionItem.addEventListener('click', function (e) {
                                    e.preventDefault();
                                    urlInput.value = suggestion;
                                    urlSuggestionsDiv.innerHTML = ''; // Clear suggestions after selection
                                });
                                urlSuggestionsDiv.appendChild(suggestionItem);
                            });
                        } else {
                            urlSuggestionsDiv.innerHTML = '<div class="list-group-item">No suggestions</div>';
                        }
                    })
                    .catch(error => {
                        urlSuggestionsDiv.innerHTML = '<div class="list-group-item text-danger">Error fetching suggestions</div>';
                    });
            } else {
                urlSuggestionsDiv.innerHTML = ''; // Clear suggestions if input is too short
            }
        };

        urlInput.addEventListener('input', function () {
            clearTimeout(debounceTimer);
            debounceTimer = setTimeout(fetchUrlSuggestions, 300); // 300msのデバウンス
        });

        urlInput.addEventListener('focus', function () {
            // フォーカス時に既に値がある場合はサジェストを表示
            if (urlInput.value.length > 2) {
                fetchUrlSuggestions();
            }
        });

        // 入力フィールドからフォーカスが外れたら候補を非表示にする
        urlInput.addEventListener('blur', function() {
            setTimeout(() => {
                urlSuggestionsDiv.innerHTML = '';
            }, 200); // 少し遅延させてクリックイベントが発火するのを待つ
        });
    }

    // ブックマークアイテムのクリックイベント
    document.querySelectorAll('.js-bookmark-item').forEach(item => {
        item.addEventListener('click', function (event) {
            const editModeToggle = document.getElementById('editModeToggle');
            const isEditMode = editModeToggle && editModeToggle.checked;

            // 編集モードが有効な場合
            if (isEditMode) {
                // クリックされた要素がチェックボックス、編集ボタン、URLリンク、タグリンクの場合は何もしない
                if (event.target.classList.contains('bookmark-checkbox') ||
                    event.target.closest('.edit-bookmark-button') || // 編集ボタン
                    (event.target.tagName === 'A' && event.target.classList.contains('text-muted')) || // URLリンク
                    event.target.closest('.badge')) { // タグリンク
                    return;
                }

                const checkbox = this.querySelector('.bookmark-checkbox');
                if (checkbox) {
                    checkbox.checked = !checkbox.checked;
                    checkbox.dispatchEvent(new Event('change')); // changeイベントを手動で発火
                }
                return; // 編集モード時はURLを開かない
            }

            // 編集モードが無効な場合（既存のURLを開くロジック）
            // チェックボックスがクリックされた場合は何もしない
            if (event.target.classList.contains('bookmark-checkbox')) {
                return;
            }
            // リンクがクリックされた場合は何もしない
            if (event.target.closest('a')) {
                return;
            }
            // フォームのボタンがクリックされた場合は何もしない
            if (event.target.closest('button')) {
                return;
            }

            const url = this.dataset.url;
            const bookmarkId = this.dataset.bookmarkId; // bookmarkIdを取得

            // CSRF ヘッダー名を取得 (通常は 'X-CSRF-TOKEN')
            const csrfHeaderNameMeta = document.querySelector('meta[name="_csrf_header"]');
            const csrfHeaderName = csrfHeaderNameMeta ? csrfHeaderNameMeta.content : 'X-CSRF-TOKEN';

            // CSRF トークン本体を取得
            const csrfTokenMeta = document.querySelector('meta[name="_csrf_token"]'); // HTMLの name="`_csrf_token`" に合わせる
            const csrfToken = csrfTokenMeta ? csrfTokenMeta.content : '';       // content 属性から取得

            if (url) {
                window.open(url, '_blank');

                // クリックログを送信
                if (bookmarkId) {
                    fetch(`/api/bookmark/click?bookmarkId=${bookmarkId}`, {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/json',
                            [csrfHeaderName]: csrfToken                       
                        }
                    })
                    .then(response => {
                        if (!response.ok) {
                            // クリックログの送信に失敗
                        }
                    })
                    .catch(error => {
                        // クリックログの送信に失敗
                    });
                }
            }
        });
    });

    // 編集モードのトグル
    const editModeToggle = document.getElementById('editModeToggle');
    const bookmarkCheckboxes = document.querySelectorAll('.bookmark-checkbox-container');
    const editModeControls = document.getElementById('edit-mode-controls');
    const bookmarkEditButtons = document.querySelectorAll('.bookmark-edit-button-container');
    const batchDeleteButton = document.getElementById('batchDeleteButton'); // 追加

    // 各ブックマークのチェックボックスにイベントリスナーを追加
    document.querySelectorAll('.bookmark-checkbox').forEach(checkbox => {
        checkbox.addEventListener('change', function() {
            const bookmarkItem = this.closest('.list-group-item');
            if (bookmarkItem) {
                bookmarkItem.classList.toggle('bookmark-item-checked', this.checked);
            }
            updateBatchDeleteButtonState(); // 追加
        });
    });

    // 一括削除ボタンの状態を更新する関数
    function updateBatchDeleteButtonState() {
        const anyCheckboxChecked = Array.from(document.querySelectorAll('.bookmark-checkbox')).some(checkbox => checkbox.checked);
        if (batchDeleteButton) {
            batchDeleteButton.disabled = !anyCheckboxChecked;
        }
    }

    if (editModeToggle) {
        editModeToggle.addEventListener('change', function() {
            const isEditMode = this.checked;

            // 各ブックマークのチェックボックスの表示/非表示
            bookmarkCheckboxes.forEach(container => {
                container.classList.toggle('d-none', !isEditMode);
            });

            // 削除ボタンの表示/非表示
            if (editModeControls) {
                editModeControls.classList.toggle('d-none', !isEditMode);
            }

            // 編集ボタンの表示/非表示
            bookmarkEditButtons.forEach(container => {
                container.classList.toggle('d-none', !isEditMode);
            });

            // 編集モード切り替え時に一括削除ボタンの状態を更新
            updateBatchDeleteButtonState(); // 追加

            // 編集モードがオフになったら全てのチェックボックスのチェックを外す
            if (!isEditMode) {
                document.querySelectorAll('.bookmark-checkbox').forEach(checkbox => {
                    checkbox.checked = false;
                    checkbox.dispatchEvent(new Event('change')); // changeイベントを手動で発火
                });
            };
        });
    }

    // 初期ロード時にもボタンの状態を更新
    updateBatchDeleteButtonState(); // 追加

    // タグボタンのクリックイベント
    document.querySelectorAll('.js-tag-button').forEach(button => {
        button.addEventListener('click', function(event) {
            event.preventDefault();
            const tagName = this.dataset.tagName;
            const currentUrl = new URL(window.location.href);
            const currentTags = currentUrl.searchParams.getAll('tags');

            if (currentTags.includes(tagName)) {
                // 既に選択されているタグなら削除
                currentUrl.searchParams.delete('tags');
                currentTags.filter(t => t !== tagName).forEach(t => currentUrl.searchParams.append('tags', t));
            } else {
                // 選択されていないタグなら追加
                currentUrl.searchParams.append('tags', tagName);
            }

            // keywordパラメータを保持
            const keywordParam = currentUrl.searchParams.get('keyword');
            if (keywordParam) {
                currentUrl.searchParams.set('keyword', keywordParam);
            }

            // pageパラメータをリセット
            currentUrl.searchParams.set('page', '0');

            window.location.href = currentUrl.toString();
        });
    });

    // ソートプルダウンの変更イベント
    const sortOrderSelect = document.getElementById('sortOrder');
    if (sortOrderSelect) {
        sortOrderSelect.addEventListener('change', function() {
            const currentUrl = new URL(window.location.href);
            const selectedSortValue = this.value; // 例: "title,asc"

            // sortパラメータを更新
            currentUrl.searchParams.set('sort', selectedSortValue);
            
            // pageパラメータをリセット
            currentUrl.searchParams.set('page', '0');

            window.location.href = currentUrl.toString(); // URLを更新してページをリロード
        });
    }

    // 追加モーダル
    const addBookmarkModal = document.getElementById('addBookmarkModal');
    if (addBookmarkModal) {
        addBookmarkModal.addEventListener('show.bs.modal', () => {
            setupTitleFetching(addBookmarkModal);
            // タグ入力UIの初期化
            setupTagInput(addBookmarkModal, 'addTagInput', 'addTagsContainer', 'tagsInput');
        });
    }

    // 編集モーダル表示時のデータセット
    const editBookmarkModal = document.getElementById('editBookmarkModal');
    if (editBookmarkModal) {
        editBookmarkModal.addEventListener('show.bs.modal', async function (event) {
            // タイトル取得ロジックをセットアップ
            setupTitleFetching(editBookmarkModal);

            if (event.relatedTarget) { // ボタンクリックで開かれた場合
                const button = event.relatedTarget;
                bookmarkId = button.getAttribute('data-bookmark-id');

                try {
                    const response = await fetch(`/api/bookmarks/${bookmarkId}`);
                    if (!response.ok) {
                        throw new Error(`HTTP error! status: ${response.status}`);
                    }
                    const bookmark = await response.json();

                    this.querySelector('#editBookmarkId').value = bookmark.id;
                    this.querySelector('#editTitle').value = bookmark.title;
                    this.querySelector('#editUrl').value = bookmark.url;
                    const tagsInput = bookmark.tags.map(tag => tag.name).join(', ');
                    this.querySelector('#editTagsInput').value = tagsInput;
                } catch (error) {
                    //  エラー発生時、特別な処理は行わない
                }
            }

            // タグ入力UIの初期化は常に実行
            setupTagInput(editBookmarkModal, 'editTagInput', 'editTagsContainer', 'editTagsInput');
        });
    }

    // 削除確認モーダル表示時のデータセット
    const deleteConfirmationModal = document.getElementById('deleteConfirmationModal');
    if (deleteConfirmationModal) {
        deleteConfirmationModal.addEventListener('show.bs.modal', function (event) {
            const selectedCheckboxes = document.querySelectorAll('.bookmark-checkbox:checked');
            const selectedIds = Array.from(selectedCheckboxes).map(cb => cb.value);

            // 既存の隠しフィールドをクリア
            const batchDeleteForm = document.getElementById('batchDeleteForm');
            batchDeleteForm.querySelectorAll('input[name="selectedBookmarks"]').forEach(input => input.remove());

            // 選択されたIDごとに新しい隠しフィールドを追加
            selectedIds.forEach(id => {
                const input = document.createElement('input');
                input.type = 'hidden';
                input.name = 'selectedBookmarks';
                input.value = id;
                batchDeleteForm.appendChild(input);
            });

            const selectedCount = selectedIds.length;
            const selectedCountElement = document.getElementById('selectedBookmarkCount');
            if (selectedCountElement) {
                selectedCountElement.textContent = selectedCount.toString();
            }
        });

        const modalDeleteButton = deleteConfirmationModal.querySelector('.btn-danger');
        if (modalDeleteButton) {
            modalDeleteButton.addEventListener('click', function() {
                document.getElementById('batchDeleteForm').submit();
            });
        }
    }

    // ランキングページからのブックマーク追加モーダル表示時のデータセット
    const addBookmarkFromRankingModal = document.getElementById('addBookmarkFromRankingModal');
    if (addBookmarkFromRankingModal) {
        addBookmarkFromRankingModal.addEventListener('show.bs.modal', function (event) {
            // タイトル取得ロジックをセットアップ
            setupTitleFetching(addBookmarkFromRankingModal);

            // ボタンクリックで開かれた場合のみ、データをセットする
            if (event.relatedTarget) {   
                // 既存のデータセットロジック
                const button = event.relatedTarget;
                const url = button.getAttribute('data-url');
                const title = button.getAttribute('data-title');

                addBookmarkFromRankingModal.querySelector('#modalTitle').value = title;
                addBookmarkFromRankingModal.querySelector('#modalUrl').value = url;
                addBookmarkFromRankingModal.querySelector('#modalTagsInput').value = ''; // 新規追加なのでタグはクリア
            }

            // タグ入力UIの初期化は常に実行
            setupTagInput(addBookmarkFromRankingModal, 'modalTagInput', 'modalTagsContainer', 'modalTagsInput');
        });
    }

    // Broadenページからのブックマーク追加モーダル表示時のデータセット
    const addBookmarkFromBroadenModal = document.getElementById('addBookmarkFromBroadenModal');
    if (addBookmarkFromBroadenModal) {
        addBookmarkFromBroadenModal.addEventListener('show.bs.modal', function (event) {
            // タイトル取得ロジックをセットアップ
            setupTitleFetching(addBookmarkFromBroadenModal);

            // ボタンクリックで開かれた場合のみ、データをセットする
            if (event.relatedTarget) {   
                const button = event.relatedTarget;
                const url = button.getAttribute('data-url');
                const title = button.getAttribute('data-title');

                addBookmarkFromBroadenModal.querySelector('#modalTitle').value = title;
                addBookmarkFromBroadenModal.querySelector('#modalUrl').value = url;
                addBookmarkFromBroadenModal.querySelector('#modalTagsInput').value = ''; // 新規追加なのでタグはクリア
            }

            // タグ入力UIの初期化は常に実行
            setupTagInput(addBookmarkFromBroadenModal, 'modalTagInput', 'modalTagsContainer', 'modalTagsInput');
        });
    }

    // お気に入りトグル処理
    document.querySelectorAll('.js-toggle-favorite').forEach(button => {
        button.addEventListener('click', function (event) {
            event.preventDefault();
            event.stopPropagation();

            const bookmarkId = this.dataset.bookmarkId;
            const isFavorite = this.dataset.isFavorite === 'true';
            const icon = this.querySelector('i');

            const csrfHeaderName = document.querySelector('meta[name="_csrf_header"]').content;
            const csrfToken = document.querySelector('meta[name="_csrf_token"]').content;

            fetch('/api/bookmark/toggle-favorite', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                    [csrfHeaderName]: csrfToken
                },
                body: `bookmarkId=${bookmarkId}`
            })
            .then(response => {
                if (response.ok) {
                    this.dataset.isFavorite = !isFavorite;
                    icon.classList.toggle('bi-star');
                    icon.classList.toggle('bi-star-fill');
                    icon.classList.toggle('text-warning');
                    icon.classList.toggle('text-secondary');
                } else {
                    // 空のブロック
                }
            })
            .catch(error => {});
        });
    });

    // タブ切り替え時のURL更新
    const allBookmarksTab = document.getElementById('all-bookmarks-tab');
    const favoritesTab = document.getElementById('favorites-tab');

    if (allBookmarksTab) {
        allBookmarksTab.addEventListener('click', function (event) {
            const currentUrl = new URL(window.location.href);
            currentUrl.searchParams.delete('showFavorites');
            currentUrl.searchParams.set('page', '0');
            window.location.href = currentUrl.toString();
        });
    }

    if (favoritesTab) {
        favoritesTab.addEventListener('click', function (event) {
            const currentUrl = new URL(window.location.href);
            currentUrl.searchParams.set('showFavorites', 'true');
            currentUrl.searchParams.set('page', '0');
            window.location.href = currentUrl.toString();
        });
    }

    // --- フォーム送信時のボタン無効化とモーダル操作ロックロジック ---
    const handleFormSubmit = (form, submitButton, modalElement) => {
        form.addEventListener('submit', function(e) {

            // HTML5バリデーションが通らない場合は処理を中断
            if (!form.checkValidity()) {
                e.stopPropagation(); // イベントの伝播を停止
                return;
            }

            // ボタンを無効化し、スピナーを表示
            if (submitButton) {
                submitButton.disabled = true;
                submitButton.originalHTML = submitButton.innerHTML; // 元のHTMLを保存
                submitButton.innerHTML = '<span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span>';
            }

            // オーバーレイを表示
            const overlay = modalElement.querySelector('.modal-processing-overlay');
            if (overlay) {
                overlay.style.display = 'flex';
            }

            // モーダルの背景クリックとエスケープキーを無効化
            const modalInstance = bootstrap.Modal.getInstance(modalElement);
            if (modalInstance) {
                modalInstance._config.backdrop = 'static'; // 背景クリックで閉じない
                modalInstance._config.keyboard = false;    // Escキーで閉じない
            }
        });

        // モーダルが閉じられたときに状態をリセット
        modalElement.addEventListener('hidden.bs.modal', function () {
            const errorAlert = modalElement.querySelector('.alert-danger');
            if (errorAlert) {
                errorAlert.remove();
            }
        });
    };

    // 各モーダルにフォーム送信時のボタン無効化とモーダル操作ロックを適用
    const addBookmarkForm = document.getElementById('addBookmarkForm');
    const addBookmarkSubmitButton = addBookmarkModal ? addBookmarkModal.querySelector('button[type="submit"][form="addBookmarkForm"]') : null;
    if (addBookmarkForm && addBookmarkSubmitButton) {
        handleFormSubmit(addBookmarkForm, addBookmarkSubmitButton, addBookmarkModal);
    }

    const editBookmarkForm = document.getElementById('editBookmarkForm');
    const editBookmarkSubmitButton = editBookmarkModal ? editBookmarkModal.querySelector('button[type="submit"][form="editBookmarkForm"]') : null;
    if (editBookmarkForm && editBookmarkSubmitButton) {
        handleFormSubmit(editBookmarkForm, editBookmarkSubmitButton, editBookmarkModal);
    }

    const batchDeleteForm = document.getElementById('batchDeleteForm');
    const batchDeleteSubmitButton = deleteConfirmationModal ? deleteConfirmationModal.querySelector('button[type="submit"][form="batchDeleteForm"]') : null;
    if (batchDeleteForm && batchDeleteSubmitButton) {
        handleFormSubmit(batchDeleteForm, batchDeleteSubmitButton, deleteConfirmationModal);
    }

    const addFromRankingForm = document.getElementById('addFromRankingForm');
    const addFromRankingSubmitButton = addBookmarkFromRankingModal ? addBookmarkFromRankingModal.querySelector('button[type="submit"][form="addFromRankingForm"]') : null;
    if (addFromRankingForm && addFromRankingSubmitButton) {
        handleFormSubmit(addFromRankingForm, addFromRankingSubmitButton, addBookmarkFromRankingModal);
    }

    const addFromBroadenForm = document.getElementById('addFromBroadenForm');
    const addFromBroadenSubmitButton = addBookmarkFromBroadenModal ? addBookmarkFromBroadenModal.querySelector('button[type="submit"][form="addFromBroadenForm"]') : null;
    if (addFromBroadenForm && addFromBroadenSubmitButton) {
        handleFormSubmit(addFromBroadenForm, addFromBroadenSubmitButton, addBookmarkFromBroadenModal);
    }

    // パスワード表示/非表示トグル設定関数
    const setupPasswordVisibilityToggle = (modalElement) => {
        modalElement.querySelectorAll('.toggle-password-visibility').forEach(button => {
            button.addEventListener('click', function() {
                const targetId = this.dataset.target;
                const passwordInput = document.getElementById(targetId);
                const icon = this.querySelector('i');

                if (passwordInput.type === 'password') {
                    passwordInput.type = 'text';
                    icon.classList.remove('bi-eye-fill');
                    icon.classList.add('bi-eye-slash-fill');
                } else {
                    passwordInput.type = 'password';
                    icon.classList.remove('bi-eye-slash-fill');
                    icon.classList.add('bi-eye-fill');
                }
            });
        });
    };

    // アカウント削除モーダルの処理
    const deleteAccountModal = document.getElementById('deleteAccountModal');
    if (deleteAccountModal) {
        const confirmCheckbox = deleteAccountModal.querySelector('#confirmAccountDeletion');
        const deleteButton = deleteAccountModal.querySelector('#deleteAccountConfirmButton');

        const toggleDeleteButtonState = () => {
            if (deleteButton) { // deleteButtonが存在することを確認
                deleteButton.disabled = !confirmCheckbox.checked;
            }
        };

        if (confirmCheckbox) { // confirmCheckboxが存在することを確認
            confirmCheckbox.addEventListener('change', toggleDeleteButtonState);
        }

        // モーダルが表示されるたびにチェックボックスをリセットし、ボタンの状態を更新
        deleteAccountModal.addEventListener('show.bs.modal', function () {
            if (confirmCheckbox) { // confirmCheckboxが存在することを確認
                confirmCheckbox.checked = false;
            }
            toggleDeleteButtonState();

            // エラーメッセージをクリア (もしあれば)
            const errorAlert = deleteAccountModal.querySelector('.alert-danger');
            if (errorAlert) {
                errorAlert.remove();
            }
        });

        const deleteAccountConfirmButton = document.getElementById('deleteAccountConfirmButton');
        if (deleteAccountConfirmButton) {
            deleteAccountConfirmButton.addEventListener('click', function() {
                const formAction = this.dataset.formAction;
                const csrfToken = document.querySelector('meta[name="_csrf_token"]').content;
                const csrfHeader = document.querySelector('meta[name="_csrf_header"]').content;

                // ボタンを無効化し、スピナーを表示
                this.disabled = true;
                this.originalHTML = this.innerHTML; // 元のHTMLを保存
                this.innerHTML = '<span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span>';

                // オーバーレイを表示
                const overlay = deleteAccountModal.querySelector('.modal-processing-overlay');
                if (overlay) {
                    overlay.style.display = 'flex';
                }

                // モーダルの背景クリックとエスケープキーを無効化
                const modalInstance = bootstrap.Modal.getInstance(deleteAccountModal);
                if (modalInstance) {
                    modalInstance._config.backdrop = 'static'; // 背景クリックで閉じない
                    modalInstance._config.keyboard = false;    // Escキーで閉じない
                }

                fetch(formAction, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/x-www-form-urlencoded',
                        [csrfHeader]: csrfToken
                    },
                    redirect: 'manual' // リダイレクトを自動追跡しないように設定
                })
                .then(response => {
                    // サーバーがリダイレクトを返した場合
                    if (response.type === 'opaqueredirect' || response.status === 302) { // 302はリダイレクトのステータスコード
                        window.location.href = '/account_deleted'; // 手動でリダイレクト
                    } else if (response.ok) {
                        // 成功時の処理（通常はここには来ないはず）
                        window.location.href = '/account_deleted';
                    } else {
                        // エラー時の処理
                        alert('アカウント削除に失敗しました。');
                        // エラー時はモーダルの状態をリセット
                        if (this.originalHTML) {
                            this.disabled = false;
                            this.innerHTML = this.originalHTML;
                            delete this.originalHTML;
                        }
                        if (overlay) {
                            overlay.style.display = 'none';
                        }
                        if (modalInstance) {
                            modalInstance._config.backdrop = true;
                            modalInstance._config.keyboard = true;
                        }
                    }
                })
                .catch(error => {
                    // エラー時はモーダルの状態をリセット
                    if (this.originalHTML) {
                        this.disabled = false;
                        this.innerHTML = this.originalHTML;
                        delete this.originalHTML;
                    }
                    if (overlay) {
                        overlay.style.display = 'none';
                    }
                    if (modalInstance) {
                        modalInstance._config.backdrop = true;
                        modalInstance._config.keyboard = true;
                    }
                });
            });
        }
    }

    // パスワード変更モーダルの処理
    const changePasswordModal = document.getElementById('changePasswordModal');
    if (changePasswordModal) {
        const changePasswordForm = document.getElementById('changePasswordForm');
        const submitButton = changePasswordModal.querySelector('button[type="submit"][form="changePasswordForm"]');

        // モーダル表示時の初期化
        changePasswordModal.addEventListener('show.bs.modal', function () {
            // エラーメッセージをクリア
            const errorAlert = changePasswordModal.querySelector('.alert-danger');
            if (errorAlert) {
                errorAlert.remove();
            }
            // フォームの入力値をクリア
            changePasswordForm.reset();
            changePasswordForm.classList.remove('was-validated'); // バリデーションスタイルをリセット

            // パスワード表示/非表示トグルのイベントリスナーを設定
            setupPasswordVisibilityToggle(changePasswordModal);
        });

        // フォーム送信処理
        if (submitButton) {
            submitButton.addEventListener('click', async function(e) {
                e.preventDefault(); // デフォルトのフォーム送信を防止

                // HTML5バリデーションが通らない場合は処理を中断
                if (!changePasswordForm.checkValidity()) {
                    e.stopPropagation(); // イベントの伝播を停止
                    changePasswordForm.classList.add('was-validated'); // バリデーションスタイルを適用
                    return;
                }

                // エラーメッセージをクリア
                const errorAlerts = changePasswordModal.querySelectorAll('.alert-danger');
                errorAlerts.forEach(alert => alert.remove());

                // パスワード一致チェック
                const newPassword = changePasswordForm.querySelector('#newPassword').value;
                const confirmPassword = changePasswordForm.querySelector('#confirmPassword').value;
                if (newPassword !== confirmPassword) {
                    const modalBody = changePasswordModal.querySelector('.modal-body');
                    if (modalBody) {
                        const errorDiv = document.createElement('div');
                        errorDiv.classList.add('alert', 'alert-danger', 'py-1');
                        errorDiv.setAttribute('role', 'alert');
                        errorDiv.innerHTML = '<p class="my-0">新しいパスワードと確認用パスワードが一致しません。</p>';
                        modalBody.prepend(errorDiv);
                    }
                    return; // フォーム送信を中断
                }

                // ボタンを無効化し、スピナーを表示
                this.disabled = true;
                this.originalHTML = this.innerHTML; // 元のHTMLを保存
                this.innerHTML = '<span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span>';

                // オーバーレイを表示
                const overlay = changePasswordModal.querySelector('.modal-processing-overlay');
                if (overlay) {
                    overlay.style.display = 'flex';
                }

                // モーダルの背景クリックとエスケープキーを無効化
                const modalInstance = bootstrap.Modal.getInstance(changePasswordModal);
                if (modalInstance) {
                    modalInstance._config.backdrop = 'static'; // 背景クリックで閉じない
                    modalInstance._config.keyboard = false;    // Escキーで閉じない
                }
                
                const formData = new URLSearchParams(new FormData(changePasswordForm)).toString();
                const csrfHeaderName = document.querySelector('meta[name="_csrf_header"]').content;
                const csrfToken = document.querySelector('meta[name="_csrf_token"]').content;

                try {
                    const response = await fetch(changePasswordForm.action, {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/x-www-form-urlencoded',
                            [csrfHeaderName]: csrfToken
                        },
                        body: formData
                    });

                    const responseHtml = await response.text();
                    const parser = new DOMParser();
                    const doc = parser.parseFromString(responseHtml, 'text/html');

                    // エラーの取得
                    const responseErrorDiv = doc.querySelector('.alert-danger');
                    const responseSuccessDiv = doc.querySelector('.alert-success');

                    // 書き込み対象の読込み
                    const modalBody = changePasswordModal.querySelector('.modal-body');

                    if (modalBody) {
                        const messageDiv = responseErrorDiv || responseSuccessDiv;
                        if (messageDiv) {
                            modalBody.prepend(messageDiv);
                        }
                    }

                    // 成功メッセージが表示された場合、モーダルを自動で閉じる
                    if (responseSuccessDiv) {
                        setTimeout(() => {
                            modalInstance.hide();
                        }, 2000); // 2秒後にモーダルを閉じる
                    }

                } catch (error) {
                    const currentModalBody = changePasswordModal.querySelector('.modal-body');
                    if (currentModalBody) {
                        const errorDiv = document.createElement('div');
                        errorDiv.classList.add('alert', 'alert-danger', 'py-1');
                        errorDiv.setAttribute('role', 'alert');
                        errorDiv.innerHTML = '<p class="my-0">ネットワークエラーが発生しました。</p>';
                        currentModalBody.prepend(errorDiv);
                    }
                } finally {
                    // ボタンとオーバーレイの状態をリセット
                    if (this.originalHTML) {
                        this.disabled = false;
                        this.innerHTML = this.originalHTML;
                        delete this.originalHTML;
                    }
                    const overlay = changePasswordModal.querySelector('.modal-processing-overlay');
                    if (overlay) {
                        overlay.style.display = 'none';
                    }
                    const modalInstance = bootstrap.Modal.getInstance(changePasswordModal);
                    if (modalInstance) {
                        modalInstance._config.backdrop = true;
                        modalInstance._config.keyboard = true;
                    }
                }
            });
        }
    }
});