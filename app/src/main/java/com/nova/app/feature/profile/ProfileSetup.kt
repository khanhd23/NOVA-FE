package com.nova.app.feature.profile

import com.nova.app.core.designsystem.NovaBrand

import androidx.compose.ui.res.stringResource
import com.nova.app.R
import android.content.res.Resources
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import com.nova.app.core.i18n.interestLabel
import java.time.format.FormatStyle

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Male
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nova.app.core.model.ProfileUiState
import com.nova.app.core.ui.NovaButton
import com.nova.app.core.viewmodel.ProfileViewModel
import com.nova.app.ui.theme.PurpleMain
import com.nova.app.ui.theme.PurplePink
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.Period
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private const val MAX_FEATURED_PHOTOS = 5
private const val MIN_INTERESTS = 3
private const val MAX_INTERESTS = 8
private const val MIN_NAME_LENGTH = 2
private const val MAX_NAME_LENGTH = 30
private const val MAX_BIO_LENGTH = 300
private const val MIN_AGE = 16
private const val MAX_AGE = 100
private const val SETUP_STEP_COUNT = 3
private const val DEFAULT_PROFILE_BIO = "I'm new here."

private val INTEREST_OPTIONS = listOf(
    "Travel", "Music", "Photography", "Gaming", "Art", "Sports", "Cooking", "Nature",
    "Movies", "Tech", "Fitness", "Reading", "Coffee", "Dancing", "Pets", "Fashion", "Yoga", "Anime",
)


@Composable
fun ProfileSetupScreen(
    profileUiState: ProfileUiState,
    isEditing: Boolean,
    profileViewModel: ProfileViewModel,
    onBack: () -> Unit,
    onComplete: () -> Unit,
) {
    val context = LocalContext.current
    val res = LocalResources.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    val featuredPhotos = rememberSaveable(saver = stringStateListSaver()) { mutableStateListOf() }
    val selectedInterests = rememberSaveable(saver = stringStateListSaver()) { mutableStateListOf() }

    var step by rememberSaveable { mutableIntStateOf(0) }
    var displayName by rememberSaveable { mutableStateOf(profileUiState.user.name) }
    var birthDateEpochDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedGender by rememberSaveable {
        mutableStateOf(profileUiState.user.gender.takeIf { it == "Male" || it == "Female" }.orEmpty())
    }
    var bio by rememberSaveable { mutableStateOf(profileUiState.bio.takeUnless { it == DEFAULT_PROFILE_BIO }.orEmpty()) }
    var avatarUrl by rememberSaveable { mutableStateOf(profileUiState.user.photoUrl.takeIf { it.isNotBlank() }) }
    var dirty by rememberSaveable { mutableStateOf(false) }
    var showErrors by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var uploadingTarget by remember { mutableStateOf<UploadTarget?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }

    val birthDate = birthDateEpochDay?.let(LocalDate::ofEpochDay)
    val nameError = validateName(res, displayName)
    val birthDateError = if (isEditing) null else validateBirthDate(res, birthDate)
    val genderError = if (!isEditing && selectedGender.isBlank()) res.getString(R.string.setup_err_gender) else null
    val interestsError = if (selectedInterests.size < MIN_INTERESTS) res.getString(R.string.setup_err_min_interests, MIN_INTERESTS) else null
    val bioError = if (bio.length > MAX_BIO_LENGTH) res.getString(R.string.setup_err_bio_length, MAX_BIO_LENGTH) else null
    val isBusy = isSaving || uploadingTarget != null

    fun markDirty() {
        dirty = true
        saveError = null
    }

    // Prefill from the server profile until the user starts editing.
    LaunchedEffect(profileUiState.user, profileUiState.bio, profileUiState.featuredPhotos, profileUiState.interests) {
        if (dirty) return@LaunchedEffect
        displayName = profileUiState.user.name
        selectedGender = profileUiState.user.gender.takeIf { it == "Male" || it == "Female" }.orEmpty()
        bio = profileUiState.bio.takeUnless { it == DEFAULT_PROFILE_BIO }.orEmpty()
        avatarUrl = profileUiState.user.photoUrl.takeIf { it.isNotBlank() }
        featuredPhotos.setAll(profileUiState.featuredPhotos.filter { it.isNotBlank() }.distinct().take(MAX_FEATURED_PHOTOS))
        selectedInterests.setAll(profileUiState.interests.distinct())
    }

    suspend fun upload(uri: Uri, title: String): String? = profileViewModel.uploadProfileImage(
        uri = uri,
        fileName = fileNameForUri(context, uri),
        mimeType = context.contentResolver.getType(uri) ?: "image/jpeg",
        title = title,
    )?.takeIf { it.isNotBlank() }

    val avatarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            uploadingTarget = UploadTarget.Avatar
            saveError = null
            val url = upload(uri, res.getString(R.string.setup_upload_title_avatar))
            if (url == null) {
                saveError = res.getString(R.string.setup_err_upload_one)
            } else {
                avatarUrl = url
                markDirty()
            }
            uploadingTarget = null
        }
    }

    val photosLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = MAX_FEATURED_PHOTOS)
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            uploadingTarget = UploadTarget.Photos
            saveError = null
            var failed = 0
            for (uri in uris.take(MAX_FEATURED_PHOTOS - featuredPhotos.size)) {
                val url = upload(uri, res.getString(R.string.setup_upload_title_featured))
                when {
                    url == null -> failed++
                    url !in featuredPhotos && featuredPhotos.size < MAX_FEATURED_PHOTOS -> featuredPhotos += url
                }
            }
            if (failed > 0) saveError = res.getString(R.string.setup_err_upload_many, failed)
            dirty = true
            uploadingTarget = null
        }
    }

    fun pickAvatar() {
        if (isBusy) return
        avatarLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    fun pickPhotos() {
        if (isBusy || featuredPhotos.size >= MAX_FEATURED_PHOTOS) return
        photosLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    fun toggleInterest(interest: String) {
        markDirty()
        when {
            interest in selectedInterests -> selectedInterests.remove(interest)
            selectedInterests.size < MAX_INTERESTS -> selectedInterests.add(interest)
            else -> saveError = res.getString(R.string.setup_err_max_interests, MAX_INTERESTS)
        }
    }

    fun stepIsValid(index: Int): Boolean = when (index) {
        0 -> nameError == null && birthDateError == null && genderError == null
        1 -> true
        else -> interestsError == null && bioError == null
    }

    fun save() {
        val valid = if (isEditing) nameError == null && bioError == null else (0 until SETUP_STEP_COUNT).all(::stepIsValid)
        if (!valid) {
            showErrors = true
            return
        }
        scope.launch {
            isSaving = true
            saveError = null
            runCatching {
                profileViewModel.saveProfile(
                    displayName = displayName.trim(),
                    bio = bio.trim().ifBlank { if (isEditing) "" else DEFAULT_PROFILE_BIO },
                    age = if (isEditing) null else birthDate?.let(::ageOn),
                    gender = if (isEditing) null else selectedGender,
                    avatarUrl = avatarUrl ?: "",
                    featuredPhotos = featuredPhotos.toList(),
                    interests = selectedInterests.toList(),
                )
            }.onSuccess {
                onComplete()
            }.onFailure {
                saveError = res.getString(R.string.setup_err_save)
            }
            isSaving = false
        }
    }

    fun onPrimaryClick() {
        focusManager.clearFocus()
        if (isBusy) return
        if (isEditing || step == SETUP_STEP_COUNT - 1) {
            save()
            return
        }
        if (!stepIsValid(step)) {
            showErrors = true
            return
        }
        showErrors = false
        saveError = null
        step += 1
    }

    fun goBack() {
        focusManager.clearFocus()
        if (!isEditing && step > 0) {
            showErrors = false
            saveError = null
            step -= 1
        } else {
            onBack()
        }
    }

    BackHandler(enabled = !isEditing && step > 0) { goBack() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(PurpleMain.copy(alpha = 0.18f), Color.Transparent)
                    )
                )
        )

        Column(modifier = Modifier.fillMaxSize()) {
            SetupHeader(
                isEditing = isEditing,
                step = step,
                onBack = ::goBack,
            )

            AnimatedContent(
                targetState = if (isEditing) -1 else step,
                transitionSpec = {
                    val forward = targetState > initialState
                    (slideInHorizontally { if (forward) it / 3 else -it / 3 } + fadeIn())
                        .togetherWith(slideOutHorizontally { if (forward) -it / 3 else it / 3 } + fadeOut())
                },
                modifier = Modifier.weight(1f),
                label = "profile-setup-step",
            ) { current ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 24.dp)
                ) {
                    when (current) {
                        -1 -> {
                            AvatarPicker(
                                displayName = displayName,
                                avatarUrl = avatarUrl,
                                uploading = uploadingTarget == UploadTarget.Avatar,
                                onPick = ::pickAvatar,
                                onReset = { avatarUrl = null; markDirty() },
                            )
                            SectionSpacer()
                            NameField(displayName, if (showErrors) nameError else null) { displayName = it; markDirty() }
                            SectionSpacer()
                            SectionTitle(stringResource(R.string.setup_featured_photos), stringResource(R.string.common_counter, featuredPhotos.size, MAX_FEATURED_PHOTOS))
                            PhotoGrid(
                                photos = featuredPhotos,
                                uploading = uploadingTarget == UploadTarget.Photos,
                                onAdd = ::pickPhotos,
                                onRemove = { featuredPhotos.remove(it); markDirty() },
                            )
                            SectionSpacer()
                            InterestsPicker(selectedInterests, null, ::toggleInterest)
                            SectionSpacer()
                            BioField(bio, if (showErrors) bioError else null) { bio = it; markDirty() }
                        }
                        0 -> {
                            StepIntro(stringResource(R.string.setup_step1_title), stringResource(R.string.setup_step1_subtitle))
                            NameField(displayName, if (showErrors) nameError else null) { displayName = it; markDirty() }
                            SectionSpacer()
                            BirthDateField(
                                birthDate = birthDate,
                                error = if (showErrors) birthDateError else null,
                                onClick = { focusManager.clearFocus(); showDatePicker = true },
                            )
                            SectionSpacer()
                            SectionTitle(stringResource(R.string.setup_gender))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                GenderCard("Male", stringResource(R.string.gender_male), Icons.Default.Male, selectedGender == "Male", Modifier.weight(1f)) {
                                    selectedGender = it; markDirty()
                                }
                                GenderCard("Female", stringResource(R.string.gender_female), Icons.Default.Female, selectedGender == "Female", Modifier.weight(1f)) {
                                    selectedGender = it; markDirty()
                                }
                            }
                            FieldError(if (showErrors) genderError else null)
                        }
                        1 -> {
                            StepIntro(stringResource(R.string.setup_step2_title), stringResource(R.string.setup_step2_subtitle, MAX_FEATURED_PHOTOS))
                            AvatarPicker(
                                displayName = displayName,
                                avatarUrl = avatarUrl,
                                uploading = uploadingTarget == UploadTarget.Avatar,
                                onPick = ::pickAvatar,
                                onReset = { avatarUrl = null; markDirty() },
                            )
                            SectionSpacer()
                            SectionTitle(stringResource(R.string.setup_featured_photos), stringResource(R.string.common_counter, featuredPhotos.size, MAX_FEATURED_PHOTOS))
                            PhotoGrid(
                                photos = featuredPhotos,
                                uploading = uploadingTarget == UploadTarget.Photos,
                                onAdd = ::pickPhotos,
                                onRemove = { featuredPhotos.remove(it); markDirty() },
                            )
                            SectionSpacer()
                            TipCard(stringResource(R.string.setup_photo_tip))
                        }
                        else -> {
                            StepIntro(stringResource(R.string.setup_step3_title), stringResource(R.string.setup_step3_subtitle, MIN_INTERESTS))
                            InterestsPicker(selectedInterests, if (showErrors) interestsError else null, ::toggleInterest)
                            SectionSpacer()
                            BioField(bio, if (showErrors) bioError else null) { bio = it; markDirty() }
                        }
                    }
                }
            }

            BottomActions(
                primaryText = when {
                    isSaving -> stringResource(R.string.common_saving)
                    uploadingTarget != null -> stringResource(R.string.common_uploading)
                    isEditing -> stringResource(R.string.common_save_changes)
                    step < SETUP_STEP_COUNT - 1 -> stringResource(R.string.common_continue)
                    else -> stringResource(R.string.setup_finish)
                },
                primaryEnabled = !isBusy,
                onPrimary = ::onPrimaryClick,
                secondaryText = if (!isEditing && step == 1 && featuredPhotos.isEmpty() && avatarUrl == null) stringResource(R.string.setup_skip) else null,
                onSecondary = ::onPrimaryClick,
                error = saveError,
            )
        }
    }

    if (showDatePicker) {
        BirthDatePickerDialog(
            initial = birthDate,
            onDismiss = { showDatePicker = false },
            onConfirm = {
                birthDateEpochDay = it.toEpochDay()
                markDirty()
                showDatePicker = false
            },
        )
    }
}

// region Layout pieces

@Composable
private fun SetupHeader(isEditing: Boolean, step: Int, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            if (isEditing || step > 0) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = MaterialTheme.colorScheme.onBackground)
                }
            } else {
                Spacer(modifier = Modifier.size(40.dp))
            }
            Text(
                text = if (isEditing) stringResource(R.string.setup_edit_profile) else stringResource(R.string.setup_step_of, step + 1, SETUP_STEP_COUNT),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            )
            if (!isEditing && step == 0) {
                TextButton(onClick = onBack) {
                    Text(stringResource(R.string.setup_switch_account), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                }
            }
        }
        if (!isEditing) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            ) {
                repeat(SETUP_STEP_COUNT) { index ->
                    val fill by animateFloatAsState(if (index <= step) 1f else 0f, label = "step-fill")
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fill)
                                .clip(CircleShape)
                                .background(Brush.horizontalGradient(NovaBrand.gradient))
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun StepIntro(title: String, subtitle: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 16.dp)
    )
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
        modifier = Modifier.padding(top = 6.dp, bottom = 28.dp)
    )
}

@Composable
private fun SectionTitle(title: String, trailing: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f))
        }
    }
}

@Composable
private fun SectionSpacer() = Spacer(modifier = Modifier.height(24.dp))

@Composable
private fun FieldError(error: String?) {
    AnimatedVisibility(visible = error != null) {
        Text(
            text = error.orEmpty(),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 6.dp, start = 4.dp)
        )
    }
}

@Composable
private fun BottomActions(
    primaryText: String,
    primaryEnabled: Boolean,
    onPrimary: () -> Unit,
    secondaryText: String?,
    onSecondary: () -> Unit,
    error: String?,
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        shadowElevation = 12.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            AnimatedVisibility(visible = error != null) {
                Row(
                    modifier = Modifier
                        .padding(bottom = 12.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(error.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            NovaButton(text = primaryText, onClick = onPrimary, enabled = primaryEnabled)
            if (secondaryText != null) {
                TextButton(
                    onClick = onSecondary,
                    enabled = primaryEnabled,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    Text(secondaryText, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                }
            }
        }
    }
}

@Composable
private fun TipCard(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(PurpleMain.copy(alpha = 0.1f))
            .border(1.dp, PurpleMain.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Lightbulb, contentDescription = null, tint = PurpleMain, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f))
    }
}

// endregion

// region Fields

@Composable
private fun setupFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = PurpleMain,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    errorBorderColor = MaterialTheme.colorScheme.error,
    cursorColor = PurpleMain,
    focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
    unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
    errorContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
    focusedTextColor = MaterialTheme.colorScheme.onBackground,
    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
)

@Composable
private fun NameField(value: String, error: String?, onValueChange: (String) -> Unit) {
    SectionTitle(stringResource(R.string.setup_display_name), stringResource(R.string.common_counter, value.trim().length, MAX_NAME_LENGTH))
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(MAX_NAME_LENGTH)) },
        placeholder = { Text(stringResource(R.string.setup_display_name_hint)) },
        singleLine = true,
        isError = error != null,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        shape = RoundedCornerShape(16.dp),
        colors = setupFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
    FieldError(error)
}

@Composable
private fun BirthDateField(birthDate: LocalDate?, error: String?, onClick: () -> Unit) {
    SectionTitle(stringResource(R.string.setup_birthday))
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .border(
                1.dp,
                if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant,
                shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = PurpleMain, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = birthDate?.format(formatter) ?: stringResource(R.string.setup_birthday_hint),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = if (birthDate == null) 0.5f else 1f),
            modifier = Modifier.weight(1f)
        )
        if (birthDate != null) {
            Text(
                text = stringResource(R.string.setup_age_years, ageOn(birthDate)),
                style = MaterialTheme.typography.labelLarge,
                color = PurpleMain,
            )
        }
    }
    if (error != null) {
        FieldError(error)
    } else {
        Text(
            text = stringResource(R.string.setup_birthday_note),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            modifier = Modifier.padding(top = 6.dp, start = 4.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirthDatePickerDialog(
    initial: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val today = remember { LocalDate.now() }
    val latest = remember { today.minusYears(MIN_AGE.toLong()) }
    val earliest = remember { today.minusYears(MAX_AGE.toLong()) }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = (initial ?: latest).toUtcMillis(),
        initialDisplayedMonthMillis = (initial ?: latest).toUtcMillis(),
        yearRange = earliest.year..latest.year,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val date = utcMillisToDate(utcTimeMillis)
                return !date.isAfter(latest) && !date.isBefore(earliest)
            }

            override fun isSelectableYear(year: Int): Boolean = year in earliest.year..latest.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let { onConfirm(utcMillisToDate(it)) } },
                enabled = state.selectedDateMillis != null,
            ) { Text(stringResource(R.string.common_ok), color = PurpleMain) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    ) {
        DatePicker(
            state = state,
            title = { Text(stringResource(R.string.setup_birthday_dialog), modifier = Modifier.padding(start = 24.dp, top = 16.dp)) },
            colors = DatePickerDefaults.colors(
                selectedDayContainerColor = NovaBrand.Start,
                selectedYearContainerColor = NovaBrand.Start,
                todayDateBorderColor = PurpleMain,
                todayContentColor = PurpleMain,
            ),
        )
    }
}

@Composable
private fun BioField(value: String, error: String?, onValueChange: (String) -> Unit) {
    SectionTitle(stringResource(R.string.setup_bio_optional), stringResource(R.string.common_counter, value.length, MAX_BIO_LENGTH))
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(MAX_BIO_LENGTH)) },
        placeholder = { Text(stringResource(R.string.setup_bio_hint)) },
        minLines = 4,
        maxLines = 8,
        isError = error != null,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        shape = RoundedCornerShape(16.dp),
        colors = setupFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
    FieldError(error)
}

@Composable
private fun GenderCard(
    label: String,
    text: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: (String) -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val borderColor by animateColorAsState(
        if (selected) PurpleMain else MaterialTheme.colorScheme.outlineVariant,
        label = "gender-border"
    )
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (selected) PurpleMain.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .border(if (selected) 2.dp else 1.dp, borderColor, shape)
            .clickable { onClick(label) }
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (selected) Brush.linearGradient(NovaBrand.gradient)
                    else Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceVariant))
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InterestsPicker(
    selected: List<String>,
    error: String?,
    onToggle: (String) -> Unit,
) {
    SectionTitle(stringResource(R.string.setup_interests), stringResource(R.string.setup_selected_count, selected.size))
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        (INTEREST_OPTIONS + selected.filterNot { it in INTEREST_OPTIONS }).forEach { interest ->
            InterestChip(interest, interest in selected) { onToggle(interest) }
        }
    }
    FieldError(error)
}

@Composable
private fun InterestChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "chip-scale")
    Row(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(
                if (selected) Brush.linearGradient(NovaBrand.gradient)
                else Brush.linearGradient(listOf(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)))
            )
            .border(1.dp, if (selected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = interestLabel(text),
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// endregion

// region Photos

@Composable
private fun AvatarPicker(
    displayName: String,
    avatarUrl: String?,
    uploading: Boolean,
    onPick: () -> Unit,
    onReset: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(contentAlignment = Alignment.BottomEnd, modifier = Modifier.size(136.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(3.dp, Brush.linearGradient(NovaBrand.gradient), CircleShape)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(enabled = !uploading, onClick = onPick),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = avatarUrl ?: fallbackAvatarUrl(displayName),
                    contentDescription = stringResource(R.string.setup_profile_photo),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                if (uploading) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                    }
                }
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(NovaBrand.gradient))
                    .border(3.dp, MaterialTheme.colorScheme.background, CircleShape)
                    .clickable(enabled = !uploading, onClick = onPick),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = stringResource(R.string.setup_change_profile_photo), tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
        Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onPick, enabled = !uploading) {
                Text(if (avatarUrl == null) stringResource(R.string.setup_add_profile_photo) else stringResource(R.string.setup_change_photo), color = PurpleMain)
            }
            if (avatarUrl != null) {
                TextButton(onClick = onReset, enabled = !uploading) {
                    Text(stringResource(R.string.common_remove), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                }
            }
        }
    }
}

@Composable
private fun PhotoGrid(
    photos: List<String>,
    uploading: Boolean,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
) {
    // Layout: one large slot + two stacked on the right, then a row of two.
    val gap = 10.dp
    @Composable
    fun Slot(index: Int, modifier: Modifier) {
        PhotoSlot(
            photoUrl = photos.getOrNull(index),
            isNextEmpty = index == photos.size,
            uploading = uploading && index == photos.size,
            onAdd = onAdd,
            onRemove = onRemove,
            modifier = modifier,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.height(IntrinsicSize.Min)) {
            Slot(0, Modifier.weight(2f).aspectRatio(0.8f))
            Column(verticalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.weight(1f).fillMaxHeight()) {
                Slot(1, Modifier.fillMaxWidth().weight(1f))
                Slot(2, Modifier.fillMaxWidth().weight(1f))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            Slot(3, Modifier.weight(1f).aspectRatio(1.3f))
            Slot(4, Modifier.weight(1f).aspectRatio(1.3f))
        }
    }
}

@Composable
private fun PhotoSlot(
    photoUrl: String?,
    isNextEmpty: Boolean,
    uploading: Boolean,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .border(
                width = 1.dp,
                color = if (isNextEmpty) PurpleMain.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant,
                shape = shape
            )
            .clickable(enabled = photoUrl == null && !uploading, onClick = onAdd),
        contentAlignment = Alignment.Center,
    ) {
        when {
            uploading -> CircularProgressIndicator(color = PurpleMain, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
            photoUrl == null -> Icon(
                Icons.Default.AddAPhoto,
                contentDescription = stringResource(R.string.setup_add_photo),
                tint = if (isNextEmpty) PurpleMain else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f),
                modifier = Modifier.size(26.dp)
            )
            else -> {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable { onRemove(photoUrl) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.setup_remove_photo), tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// endregion

// region Helpers

private enum class UploadTarget { Avatar, Photos }

private fun stringStateListSaver() = listSaver<SnapshotStateList<String>, String>(
    save = { it.toList() },
    restore = { it.toMutableStateList() },
)

private fun SnapshotStateList<String>.setAll(items: List<String>) {
    clear()
    addAll(items)
}

private fun validateName(res: Resources, name: String): String? {
    val trimmed = name.trim()
    return when {
        trimmed.isEmpty() -> res.getString(R.string.setup_err_name_empty)
        trimmed.length < MIN_NAME_LENGTH -> res.getString(R.string.setup_err_name_short, MIN_NAME_LENGTH)
        trimmed.length > MAX_NAME_LENGTH -> res.getString(R.string.setup_err_name_long, MAX_NAME_LENGTH)
        else -> null
    }
}

private fun validateBirthDate(res: Resources, date: LocalDate?): String? {
    if (date == null) return res.getString(R.string.setup_err_birthday_empty)
    val age = ageOn(date)
    return when {
        date.isAfter(LocalDate.now()) -> res.getString(R.string.setup_err_birthday_future)
        age < MIN_AGE -> res.getString(R.string.setup_err_too_young, MIN_AGE)
        age > MAX_AGE -> res.getString(R.string.setup_err_birthday_invalid)
        else -> null
    }
}

private fun ageOn(birthDate: LocalDate, today: LocalDate = LocalDate.now()): Int =
    Period.between(birthDate, today).years.coerceAtLeast(0)

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun utcMillisToDate(millis: Long): LocalDate =
    java.time.Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

private fun fileNameForUri(context: Context, uri: Uri): String {
    val resolver = context.contentResolver
    val nameFromProvider = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    }
    return nameFromProvider?.takeIf { it.isNotBlank() } ?: "profile-photo-${System.currentTimeMillis()}.jpg"
}

private fun fallbackAvatarUrl(displayName: String): String {
    val safeName = displayName.trim().ifBlank { "Nova User" }
    val encoded = URLEncoder.encode(safeName, StandardCharsets.UTF_8)
    return "https://ui-avatars.com/api/?name=$encoded&background=6C5CE7&color=FFFFFF&size=512"
}

// endregion
