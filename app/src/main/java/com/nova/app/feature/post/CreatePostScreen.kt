package com.nova.app.feature.post

import android.Manifest
import android.graphics.Bitmap
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nova.app.core.designsystem.NovaIcons
import com.nova.app.core.backend.BackendCommunityTagSuggestion
import com.nova.app.core.backend.BackendMediaUploadRequest
import com.nova.app.core.backend.BackendRuntimeRegistry
import com.nova.app.core.backend.BackendSearchUser
import com.nova.app.core.model.ChatAttachmentKind
import com.nova.app.core.model.CreatePostDraft
import com.nova.app.core.ui.NovaVideoView
import com.nova.app.core.state.NovaLoadState
import com.nova.app.core.viewmodel.CommunityViewModel
import com.nova.app.ui.theme.BgCardDark
import com.nova.app.ui.theme.PurpleMain
import com.nova.app.ui.theme.PurplePink
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

enum class InputMode {
    None, Keyboard, Gallery, Emoji, Hashtag, Mention
}

private const val MAX_MEDIA_ATTACHMENTS = 10
private const val MAX_VIDEO_BYTES = 50L * 1024L * 1024L

private data class ComposerMedia(
    val uri: Uri,
    val kind: ChatAttachmentKind,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePostScreen(
    communityViewModel: CommunityViewModel,
    onBack: () -> Unit,
    onPublished: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val communityUiState by communityViewModel.uiState.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp

    var composer by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    var selectedMedia by remember { mutableStateOf<List<ComposerMedia>>(emptyList()) }
    var recentPhotos by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var activeMode by rememberSaveable { mutableStateOf(InputMode.Gallery) }
    var isPosting by rememberSaveable { mutableStateOf(false) }
    var composerError by rememberSaveable { mutableStateOf<String?>(null) }
    var tagSuggestions by remember { mutableStateOf<List<BackendCommunityTagSuggestion>>(emptyList()) }
    var mentionSuggestions by remember { mutableStateOf<List<BackendSearchUser>>(emptyList()) }
    val selectedMentionIds = remember { mutableStateListOf<String>() }
    val selectedMediaUris = remember(selectedMedia) { selectedMedia.map { it.uri.toString() }.toSet() }
    val suggestionListHeight = suggestionPanelHeight(screenHeight)
    val mediaPanelHeight = screenHeight * 0.34f

    val publishTopicId = when (val state = communityUiState) {
        is NovaLoadState.Success -> state.data.topics.firstOrNull()?.id ?: "travel"
        else -> "travel"
    }

    LaunchedEffect(Unit) {
        activeMode = InputMode.Gallery
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            recentPhotos = getRecentPhotos(context)
        }
    }

    LaunchedEffect(Unit) {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            recentPhotos = getRecentPhotos(context)
        } else {
            permissionLauncher.launch(permission)
        }
    }

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                val picked = uris.mapNotNull { uri ->
                    val mimeType = context.contentResolver.getType(uri).orEmpty()
                    val kind = detectMediaKind(mimeType)
                    val name = readDisplayName(context, uri)
                    val size = readMediaSize(context, uri)
                    if (kind == ChatAttachmentKind.Video && (size ?: 0L) > MAX_VIDEO_BYTES) {
                        composerError = "Video must be 50 MB or smaller"
                        null
                    } else {
                        ComposerMedia(
                            uri = uri,
                            kind = kind,
                            name = name,
                            mimeType = mimeType.ifBlank { fallbackMimeTypeForUri(uri) },
                            sizeBytes = size,
                        )
                    }
                }
                if (picked.isNotEmpty()) {
                    selectedMedia = appendPickedMedia(selectedMedia, picked)
                    if (selectedMedia.isEmpty()) {
                        composerError = "No valid media selected"
                    } else {
                        composerError = null
                    }
                }
            }
        }
    )

    val cameraCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview(),
        onResult = { bitmap ->
            if (bitmap != null) {
                val capturedUri = persistCapturedBitmap(context, bitmap)
                if (capturedUri != null) {
                    val capturedMedia = ComposerMedia(
                        uri = capturedUri,
                        kind = ChatAttachmentKind.Image,
                        name = "camera-${System.currentTimeMillis()}.jpg",
                        mimeType = "image/jpeg",
                        sizeBytes = readMediaSize(context, capturedUri),
                    )
                    selectedMedia = appendPickedMedia(selectedMedia, listOf(capturedMedia))
                    composerError = null
                } else {
                    composerError = "Unable to save camera photo"
                }
            }
        }
    )

    val tagQuery = remember(composer, activeMode) {
        if (activeMode != InputMode.Hashtag) "" else currentTokenQuery(composer, '#')
    }
    val mentionQuery = remember(composer, activeMode) {
        if (activeMode != InputMode.Mention) "" else currentTokenQuery(composer, '@')
    }

    LaunchedEffect(tagQuery, activeMode) {
        if (activeMode == InputMode.Hashtag && tagQuery.isNotBlank()) {
            tagSuggestions = BackendRuntimeRegistry.runtime?.fetchCommunityTags(tagQuery, 8)
                ?: localTagSuggestions(tagQuery)
        } else {
            tagSuggestions = BackendRuntimeRegistry.runtime?.fetchCommunityTags("", 8)
                ?: localTagSuggestions("")
        }
    }

    LaunchedEffect(mentionQuery, activeMode) {
        if (activeMode == InputMode.Mention && mentionQuery.isNotBlank()) {
            val results = BackendRuntimeRegistry.runtime?.searchUsers(mentionQuery, page = 0, size = 6)
            mentionSuggestions = results?.items?.map {
                BackendSearchUser(
                    userId = it.userId,
                    displayName = it.displayName,
                    age = it.age,
                    avatarUrl = it.avatarUrl,
                    verified = it.verified,
                    distanceKm = it.distanceKm,
                    online = it.online,
                    city = it.city,
                    gender = it.gender,
                    interests = it.interests,
                )
            } ?: localMentionSuggestions()
        } else {
            mentionSuggestions = localMentionSuggestions()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Post", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            if (isPosting) return@Button
                            scope.launch {
                                isPosting = true
                                composerError = null
                                val plainText = composer.text.trim()
                                val tags = extractTags(plainText)
                                val uploadedAssets = uploadComposerMedia(context, selectedMedia)
                                if (selectedMedia.isNotEmpty() && uploadedAssets.isEmpty()) {
                                    composerError = "Upload failed. Please try again."
                                    isPosting = false
                                    return@launch
                                }
                                val uploadedUrls = uploadedAssets.map { it.url }
                                val uploadedThumb = uploadedAssets.firstNotNullOfOrNull { it.previewUrl }
                                    ?: uploadedAssets.firstOrNull()?.previewUrl
                                communityViewModel.publishPost(
                                    CreatePostDraft(
                                        topicId = publishTopicId,
                                        text = plainText,
                                        postType = inferPostType(selectedMedia),
                                        mediaUrl = uploadedUrls.firstOrNull(),
                                        mediaUrls = uploadedUrls,
                                        thumbnailUrl = uploadedThumb,
                                        tags = tags,
                                        mentionedUserIds = selectedMentionIds.distinct(),
                                    )
                                )
                                isPosting = false
                                onPublished()
                                onBack()
                            }
                        },
                        enabled = !isPosting && (composer.text.isNotBlank() || selectedMedia.isNotEmpty()),
                        colors = ButtonDefaults.buttonColors(containerColor = PurpleMain),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        if (isPosting) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Text("Post", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgCardDark)
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                when (activeMode) {
                    InputMode.Hashtag -> {
                        SuggestionPanel(
                            height = suggestionListHeight,
                            title = "Trending hashtags",
                            highlightPrefix = "#",
                            textSuggestions = tagSuggestions.map { suggestion ->
                                val label = "#${suggestion.tag}"
                                val subtitle = "${suggestion.hotness} hot"
                                SuggestionRowItem(label, subtitle)
                            },
                            onSelectText = { label ->
                                val tag = label.removePrefix("#")
                                composer = replaceToken(composer, '#', tag)
                                activeMode = InputMode.Keyboard
                                focusRequester.requestFocus()
                                keyboardController?.show()
                            }
                        )
                    }
                    InputMode.Mention -> {
                        SuggestionPanel(
                            height = suggestionListHeight,
                            title = "Tag friends",
                            highlightPrefix = "@",
                            mentionSuggestions = mentionSuggestions.map { user ->
                                SuggestionRowItem(
                                    label = "@${user.displayName}",
                                    subtitle = if (user.online) "Active now" else user.city.ifBlank { user.interests.take(2).joinToString(" / ") },
                                    avatarUrl = user.avatarUrl,
                                    userId = user.userId,
                                )
                            },
                            onSelectMention = { user ->
                                composer = replaceToken(composer, '@', user.label.removePrefix("@"))
                                if (!selectedMentionIds.contains(user.userId.orEmpty())) {
                                    selectedMentionIds.add(user.userId.orEmpty())
                                }
                                activeMode = InputMode.Keyboard
                                focusRequester.requestFocus()
                                keyboardController?.show()
                            }
                        )
                    }
                    else -> Unit
                }

                PostToolbar(
                    activeMode = activeMode,
                    onInsertSymbol = { symbol ->
                        composer = insertSymbolAtCursor(composer, symbol)
                        activeMode = if (symbol == "#") InputMode.Hashtag else InputMode.Mention
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    },
                    onModeChange = { mode ->
                        when (mode) {
                            InputMode.Keyboard -> {
                                activeMode = InputMode.Keyboard
                                focusRequester.requestFocus()
                                keyboardController?.show()
                            }
                            InputMode.Gallery -> {
                                activeMode = mode
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            }
                            InputMode.Emoji -> {
                                activeMode = mode
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            }
                            InputMode.Hashtag, InputMode.Mention -> {
                                activeMode = mode
                                focusRequester.requestFocus()
                                keyboardController?.show()
                            }
                            InputMode.None -> {
                                activeMode = InputMode.None
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            }
                        }
                    },
                    onFullGalleryClick = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        mediaPickerLauncher.launch(PickVisualMediaRequest(PickVisualMedia.ImageAndVideo))
                    }
                )

                when (activeMode) {
                    InputMode.Gallery -> {
                        GalleryPanel(
                            height = mediaPanelHeight,
                            photos = recentPhotos,
                            selectedUris = selectedMediaUris,
                            onCameraClick = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                cameraCaptureLauncher.launch(null)
                            },
                            onImageSelected = { uri ->
                                val mimeType = context.contentResolver.getType(uri).orEmpty().ifBlank { "image/jpeg" }
                                val picked = ComposerMedia(
                                    uri = uri,
                                    kind = ChatAttachmentKind.Image,
                                    name = readDisplayName(context, uri),
                                    mimeType = mimeType,
                                    sizeBytes = readMediaSize(context, uri),
                                )
                                selectedMedia = appendPickedMedia(selectedMedia, listOf(picked))
                                composerError = null
                            }
                        )
                    }
                    InputMode.Emoji -> {
                        EmojiPanel(
                            height = mediaPanelHeight,
                            onEmojiSelect = { emoji ->
                                composer = insertTextAtCursor(composer, emoji)
                            }
                        )
                    }
                    else -> Unit
                }
            }
        },
        containerColor = BgCardDark
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = composer,
                    onValueChange = { composer = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .focusRequester(focusRequester)
                        .onFocusChanged { state ->
                            if (state.isFocused && activeMode != InputMode.Hashtag && activeMode != InputMode.Mention) {
                                activeMode = InputMode.Keyboard
                            }
                        },
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                    placeholder = { Text("What's on your mind?", color = Color.Gray) },
                    minLines = 2,
                    maxLines = 6,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PurpleMain,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = PurpleMain,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    )
                )

                if (selectedMedia.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    SelectedMediaPreview(
                        media = selectedMedia,
                        onRemove = { index ->
                            selectedMedia = selectedMedia.filterIndexed { i, _ -> i != index }
                            composerError = null
                        }
                    )
                }
            }
        }
    }
}
@Composable
private fun SelectedMediaPreview(
    media: List<ComposerMedia>,
    onRemove: (Int) -> Unit,
) {
    if (media.isEmpty()) return

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        when (media.size) {
            1 -> {
                MediaPreviewTile(
                    item = media[0],
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (media[0].kind == ChatAttachmentKind.Video) 230.dp else 250.dp),
                    onRemove = { onRemove(0) },
                    showTypeBadge = true,
                )
            }

            2 -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    media.take(2).forEachIndexed { index, item ->
                        MediaPreviewTile(
                            item = item,
                            modifier = Modifier
                                .weight(1f)
                                .height(220.dp),
                            onRemove = { onRemove(index) },
                        )
                    }
                }
            }

            3 -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MediaPreviewTile(
                        item = media[0],
                        modifier = Modifier
                            .weight(1.15f)
                            .fillMaxHeight(),
                        onRemove = { onRemove(0) },
                    )
                    Column(
                        modifier = Modifier
                            .weight(0.85f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MediaPreviewTile(
                            item = media[1],
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            onRemove = { onRemove(1) },
                        )
                        MediaPreviewTile(
                            item = media[2],
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            onRemove = { onRemove(2) },
                        )
                    }
                }
            }

            else -> {
                val overflowCount = media.size - 4
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MediaPreviewTile(
                            item = media[0],
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            onRemove = { onRemove(0) },
                        )
                        MediaPreviewTile(
                            item = media[2],
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            onRemove = { onRemove(2) },
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MediaPreviewTile(
                            item = media[1],
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            onRemove = { onRemove(1) },
                        )
                        MediaPreviewTile(
                            item = media[3],
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            onRemove = { onRemove(3) },
                            overlayLabel = if (overflowCount > 0) "+$overflowCount" else null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaPreviewTile(
    item: ComposerMedia,
    modifier: Modifier = Modifier,
    onRemove: () -> Unit,
    showTypeBadge: Boolean = false,
    overlayLabel: String? = null,
) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .border(1.dp, PurpleMain.copy(alpha = 0.45f), shape)
    ) {
        if (item.kind == ChatAttachmentKind.Video) {
            NovaVideoView(
                url = item.uri.toString(),
                modifier = Modifier.fillMaxSize(),
                autoPlay = false,
                showControls = false,
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.88f),
                    modifier = Modifier.size(44.dp)
                )
            }
        } else {
            AsyncImage(
                model = item.uri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        if (showTypeBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (item.kind == ChatAttachmentKind.Video) "Video" else "Photo",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (!overlayLabel.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.34f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = overlayLabel,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp
                )
            }
        }

        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(28.dp)
                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
        ) {
            Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

data class SuggestionRowItem(
    val label: String,
    val subtitle: String,
    val avatarUrl: String? = null,
    val userId: String? = null,
)

@Composable
private fun SuggestionPanel(
    height: androidx.compose.ui.unit.Dp,
    title: String,
    highlightPrefix: String,
    textSuggestions: List<SuggestionRowItem> = emptyList(),
    mentionSuggestions: List<SuggestionRowItem> = emptyList(),
    onSelectText: (String) -> Unit = {},
    onSelectMention: (SuggestionRowItem) -> Unit = {},
) {
    val items = if (highlightPrefix == "#") textSuggestions else mentionSuggestions
    if (items.isEmpty()) return

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
        color = BgCardDark,
        tonalElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = PurplePink, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(10.dp))
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(items) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (highlightPrefix == "#") onSelectText(item.label) else onSelectMention(item)
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.DarkGray),
                            contentAlignment = Alignment.Center
                        ) {
                            if (item.avatarUrl != null) {
                                AsyncImage(
                                    model = item.avatarUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text(
                                    text = if (highlightPrefix == "#") "#" else "@",
                                    color = Color.Gray,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.size(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(item.subtitle, color = Color.Gray, fontSize = 11.sp)
                        }
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                }
            }
        }
    }
}

@Composable
private fun PostToolbar(
    activeMode: InputMode,
    onInsertSymbol: (String) -> Unit,
    onModeChange: (InputMode) -> Unit,
    onFullGalleryClick: () -> Unit
) {
    Surface(
        color = BgCardDark,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                ToolbarIcon(Icons.Default.EmojiEmotions, activeMode == InputMode.Emoji) { onModeChange(InputMode.Emoji) }
                ToolbarIcon(Icons.Default.Image, activeMode == InputMode.Gallery) { onModeChange(InputMode.Gallery) }
                ToolbarIcon(Icons.Default.AlternateEmail, activeMode == InputMode.Mention) { onInsertSymbol("@") }
                ToolbarSymbol("#", activeMode == InputMode.Hashtag) { onInsertSymbol("#") }
            }

            if (activeMode == InputMode.Gallery) {
                TextButton(onClick = onFullGalleryClick) {
                    Text("Photos & videos", color = PurpleMain)
                }
            } else {
                IconButton(onClick = { onModeChange(InputMode.Keyboard) }) {
                    Icon(Icons.Default.Keyboard, contentDescription = "Keyboard", tint = if (activeMode == InputMode.Keyboard) PurpleMain else Color.Gray)
                }
            }
        }
    }
}

@Composable
private fun ToolbarIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, isSelected: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = null, tint = if (isSelected) PurpleMain else Color.Gray)
    }
}

@Composable
private fun ToolbarSymbol(symbol: String, isSelected: Boolean, onClick: () -> Unit) {
    Text(
        text = symbol,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp),
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        color = if (isSelected) PurpleMain else Color.Gray
    )
}

@Composable
private fun GalleryPanel(
    height: androidx.compose.ui.unit.Dp,
    photos: List<Uri>,
    selectedUris: Set<String>,
    onCameraClick: () -> Unit,
    onImageSelected: (Uri) -> Unit,
) {
    val galleryItems = remember(photos) {
        buildList<Uri?> {
            add(null)
            addAll(photos.take(23))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .background(BgCardDark)
    ) {
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(galleryItems.chunked(3)) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEachIndexed { index, uri ->
                        when {
                            index == 0 && uri == null -> {
                                CameraTile(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(100.dp),
                                    onClick = onCameraClick
                                )
                            }
                            uri == null -> {
                                Spacer(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(100.dp)
                                )
                            }
                            else -> {
                                GalleryThumbnailTile(
                                    uri = uri,
                                    selected = selectedUris.contains(uri.toString()),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(100.dp),
                                    onClick = { onImageSelected(uri) }
                                )
                            }
                        }
                    }
                    if (row.size < 3) {
                        repeat(3 - row.size) {
                            Spacer(modifier = Modifier.weight(1f).height(100.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryThumbnailTile(
    uri: Uri,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .border(1.5.dp, if (selected) PurpleMain else Color.Transparent, shape)
            .clickable { onClick() }
    ) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(PurpleMain),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun CameraTile(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, PurpleMain.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .background(Color(0xFF1C1628))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                imageVector = NovaIcons.Camera,
                contentDescription = null,
                tint = PurpleMain,
                modifier = Modifier.size(28.dp)
            )
            Text(
                text = "Camera",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
@Composable
private fun EmojiPanel(height: androidx.compose.ui.unit.Dp, onEmojiSelect: (String) -> Unit) {
    val emojis = listOf("😊", "😂", "🥰", "😍", "✨", "🙌", "🔥", "📸", "🌈", "☕", "🍕", "🎉")
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .background(BgCardDark)
    ) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(emojis.chunked(6)) { row ->
                Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                    row.forEach { emoji ->
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clickable { onEmojiSelect(emoji) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(emoji, fontSize = 24.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun getRecentPhotos(context: Context): List<Uri> {
    val uris = mutableListOf<Uri>()
    val projection = arrayOf(MediaStore.Images.Media._ID)
    val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

    context.contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        projection,
        null,
        null,
        sortOrder
    )?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        while (cursor.moveToNext() && uris.size < 24) {
            val id = cursor.getLong(idColumn)
            val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            uris.add(contentUri)
        }
    }
    return uris
}

private fun currentTokenQuery(value: TextFieldValue, prefix: Char): String {
    val cursor = value.selection.end.coerceIn(0, value.text.length)
    val prefixIndex = value.text.lastIndexOf(prefix, startIndex = max(0, cursor - 1))
    if (prefixIndex < 0) return ""
    val before = value.text.substring(0, prefixIndex)
    if (prefixIndex > 0 && !before.last().isWhitespace()) return ""
    val rawQuery = value.text.substring(prefixIndex + 1, cursor)
    return rawQuery.takeWhile { !it.isWhitespace() }.trim()
}

private fun inferPostType(media: List<ComposerMedia>): String {
    if (media.isEmpty()) return "TEXT"
    val hasVideo = media.any { it.kind == ChatAttachmentKind.Video }
    val hasImage = media.any { it.kind == ChatAttachmentKind.Image }
    return when {
        hasVideo && hasImage -> "MIXED"
        hasVideo -> "VIDEO"
        else -> "IMAGE"
    }
}

private fun appendPickedMedia(
    current: List<ComposerMedia>,
    picked: List<ComposerMedia>,
): List<ComposerMedia> {
    if (picked.isEmpty()) return current
    return (current + picked)
        .distinctBy { it.uri.toString() }
        .take(MAX_MEDIA_ATTACHMENTS)
}

private suspend fun uploadComposerMedia(
    context: Context,
    media: List<ComposerMedia>,
): List<com.nova.app.core.backend.BackendMediaAsset> {
    val runtime = BackendRuntimeRegistry.runtime ?: return emptyList()
    val uploaded = mutableListOf<com.nova.app.core.backend.BackendMediaAsset>()
    for ((index, item) in media.withIndex()) {
        if (item.kind == ChatAttachmentKind.Video && (item.sizeBytes ?: 0L) > MAX_VIDEO_BYTES) {
            return emptyList()
        }
        val result = runtime.uploadMedia(
            BackendMediaUploadRequest(
                uri = item.uri,
                fileName = buildMediaFileName(item, index),
                title = item.name.ifBlank { "Community media" },
                mimeType = item.mimeType.ifBlank { fallbackMimeTypeForUri(item.uri) },
                kind = item.kind,
                previewUrl = null,
            )
        ) ?: continue
        uploaded += result
    }
    return uploaded
}

private fun buildMediaFileName(item: ComposerMedia, index: Int): String {
    val fallbackBase = "community-${System.currentTimeMillis()}-$index"
    val rawBase = item.name.substringBeforeLast('.').ifBlank { fallbackBase }
    val safeBase = rawBase.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    val extension = extensionForMimeType(item.mimeType) ?: extensionFromName(item.name)
        ?: when (item.kind) {
            ChatAttachmentKind.Video -> "mp4"
            ChatAttachmentKind.Audio -> "m4a"
            ChatAttachmentKind.Image -> "jpg"
            else -> "bin"
        }
    return "$safeBase.$extension"
}

private fun extensionForMimeType(mimeType: String): String? {
    val normalized = mimeType.lowercase()
    return when {
        normalized == "image/jpeg" || normalized == "image/jpg" -> "jpg"
        normalized == "image/png" -> "png"
        normalized == "image/webp" -> "webp"
        normalized == "video/mp4" -> "mp4"
        normalized == "video/quicktime" -> "mov"
        normalized == "video/x-matroska" -> "mkv"
        normalized == "video/webm" -> "webm"
        else -> MimeTypeMap.getSingleton().getExtensionFromMimeType(normalized)
    }?.takeIf { it.isNotBlank() }
}

private fun extensionFromName(name: String): String? {
    return name.substringAfterLast('.', "").takeIf { it.isNotBlank() }
}

private fun detectMediaKind(mimeType: String): ChatAttachmentKind {
    val normalized = mimeType.lowercase()
    return when {
        normalized.startsWith("image/") -> ChatAttachmentKind.Image
        normalized.startsWith("video/") -> ChatAttachmentKind.Video
        normalized.startsWith("audio/") -> ChatAttachmentKind.Audio
        else -> ChatAttachmentKind.File
    }
}

private fun fallbackMimeTypeForUri(uri: Uri): String {
    val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString()).orEmpty()
    if (extension.isNotBlank()) {
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())?.let { return it }
    }
    val name = uri.toString().substringAfterLast('.').lowercase()
    return when (name) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "mp4" -> "video/mp4"
        "mov" -> "video/quicktime"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "aac" -> "audio/aac"
        "wav" -> "audio/wav"
        "ogg" -> "audio/ogg"
        else -> "application/octet-stream"
    }
}

private fun readDisplayName(context: Context, uri: Uri): String {
    val rawName = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                cursor.getString(index).orEmpty()
            } else {
                ""
            }
        }
    }.getOrNull().orEmpty()
    return rawName.ifBlank {
        uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "media"
    }
}

private fun persistCapturedBitmap(context: Context, bitmap: Bitmap): Uri? {
    return runCatching {
        val file = File.createTempFile("nova-camera-", ".jpg", context.cacheDir)
        FileOutputStream(file).use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)
        }
        Uri.fromFile(file)
    }.getOrNull()
}

private fun readMediaSize(context: Context, uri: Uri): Long? {
    return runCatching {
        if (uri.scheme == "file") {
            uri.path?.let { path ->
                val file = File(path)
                if (file.exists()) file.length() else null
            }
        } else {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) {
                    cursor.getLong(index)
                } else {
                    null
                }
            }
        }
    }.getOrNull()
}

private fun insertSymbolAtCursor(value: TextFieldValue, symbol: String): TextFieldValue {
    val cursor = value.selection.end.coerceIn(0, value.text.length)
    if (cursor > 0 && value.text.getOrNull(cursor - 1)?.toString() == symbol) {
        return value
    }
    val newText = buildString {
        append(value.text.substring(0, value.selection.start))
        append(symbol)
        if (value.selection.start == value.selection.end && cursor < value.text.length) {
            append(value.text.substring(cursor))
        } else {
            append(value.text.substring(value.selection.end))
        }
    }
    val newCursor = value.selection.start + symbol.length
    return TextFieldValue(newText, TextRange(newCursor))
}

private fun replaceToken(value: TextFieldValue, prefix: Char, replacement: String): TextFieldValue {
    val cursor = value.selection.end.coerceIn(0, value.text.length)
    val prefixIndex = value.text.lastIndexOf(prefix, startIndex = max(0, cursor - 1))
    if (prefixIndex < 0) return value
    val newText = buildString {
        append(value.text.substring(0, prefixIndex))
        append(prefix)
        append(replacement)
        append(" ")
        if (cursor < value.text.length) {
            append(value.text.substring(cursor))
        }
    }
    val newCursor = prefixIndex + replacement.length + 2
    return TextFieldValue(newText, TextRange(newCursor.coerceAtMost(newText.length)))
}

private fun insertTextAtCursor(value: TextFieldValue, insert: String): TextFieldValue {
    val cursor = value.selection.end.coerceIn(0, value.text.length)
    val newText = buildString {
        append(value.text.substring(0, value.selection.start))
        append(insert)
        append(value.text.substring(value.selection.end))
    }
    val newCursor = value.selection.start + insert.length
    return TextFieldValue(newText, TextRange(newCursor.coerceAtMost(newText.length)))
}

private fun extractTags(text: String): List<String> {
    val regex = Regex("#([\\p{L}0-9_]+)")
    return regex.findAll(text)
        .map { it.groupValues[1].lowercase() }
        .distinct()
        .toList()
}

private fun localTagSuggestions(query: String): List<BackendCommunityTagSuggestion> {
    val hotTags = listOf(
        BackendCommunityTagSuggestion("travel", 92, 312, exactMatch = false, canCreate = true),
        BackendCommunityTagSuggestion("photography", 88, 254, exactMatch = false, canCreate = true),
        BackendCommunityTagSuggestion("weekend", 78, 198, exactMatch = false, canCreate = true),
        BackendCommunityTagSuggestion("product", 74, 162, exactMatch = false, canCreate = true),
        BackendCommunityTagSuggestion("compose", 69, 141, exactMatch = false, canCreate = true),
        BackendCommunityTagSuggestion("vibes", 55, 118, exactMatch = false, canCreate = true),
    )
    val normalized = query.trim().lowercase()
    return hotTags
        .filter { normalized.isBlank() || it.tag.contains(normalized) }
        .sortedWith(
            compareByDescending<BackendCommunityTagSuggestion> { it.hotness }
                .thenBy { if (normalized.isBlank()) 0 else if (it.tag.startsWith(normalized)) 0 else 1 }
        )
}

private fun localMentionSuggestions(): List<BackendSearchUser> {
    return listOf(
        BackendSearchUser("u-seraphina", "Seraphina Vale", 27, fallbackAvatarUrl("Seraphina Vale"), verified = true, online = true, city = "Lagos", gender = "Female", interests = listOf("Product", "Messaging")),
        BackendSearchUser("u-elena", "Elena Markov", 25, fallbackAvatarUrl("Elena Markov"), verified = true, online = false, city = "Amsterdam", gender = "Female", interests = listOf("Community", "Events")),
        BackendSearchUser("u-chloe", "Chloe Rivera", 24, fallbackAvatarUrl("Chloe Rivera"), verified = true, online = true, city = "Barcelona", gender = "Female", interests = listOf("Photography", "Travel")),
        BackendSearchUser("u-marcus", "Marcus Reed", 29, fallbackAvatarUrl("Marcus Reed"), verified = false, online = false, city = "Berlin", gender = "Male", interests = listOf("Android", "Build systems")),
    )
}

private fun fallbackAvatarUrl(name: String): String {
    val safeName = if (name.isBlank()) "Nova User" else name.trim()
    val encoded = java.net.URLEncoder.encode(safeName, Charsets.UTF_8)
    return "https://ui-avatars.com/api/?name=$encoded&background=6C5CE7&color=FFFFFF&size=512"
}

private fun suggestionPanelHeight(screenHeight: androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.Dp {
    return maxOf(220f, screenHeight.value * 0.25f).dp
}
