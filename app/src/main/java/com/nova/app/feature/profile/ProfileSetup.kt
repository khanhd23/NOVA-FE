package com.nova.app.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nova.app.core.model.ProfileUiState
import com.nova.app.core.ui.NovaButton
import com.nova.app.core.ui.NovaChip
import com.nova.app.core.ui.NovaTextField
import com.nova.app.core.ui.NovaTopBar
import com.nova.app.core.model.SampleMedia
import com.nova.app.core.viewmodel.ProfileViewModel
import com.nova.app.ui.theme.PurpleMain
import com.nova.app.ui.theme.PurplePink
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ProfileSetupScreen(
    profileUiState: ProfileUiState,
    isEditing: Boolean,
    profileViewModel: ProfileViewModel,
    onBack: () -> Unit,
    onComplete: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val interestOptions = remember {
        listOf("Travel", "Music", "Photography", "Gaming", "Art", "Sports", "Cooking", "Nature", "Movies", "Tech")
    }
    val avatarOptions = remember { avatarLibrary() }
    val featuredOptions = remember { featuredLibrary() }
    val featuredPhotos = remember { mutableStateListOf("", "", "") }
    val selectedInterests = remember { mutableStateListOf<String>() }

    var displayName by rememberSaveable { mutableStateOf(profileUiState.user.name) }
    var bio by rememberSaveable { mutableStateOf(profileUiState.bio) }
    var selectedAvatarUrl by rememberSaveable { mutableStateOf(profileUiState.user.photoUrl.takeIf { it.isNotBlank() }) }
    var activePicker by remember { mutableStateOf<ImagePickerTarget?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var dirty by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(profileUiState.user.name, profileUiState.bio, profileUiState.featuredPhotos, profileUiState.interests) {
        if (!dirty) {
            displayName = profileUiState.user.name.ifBlank { "Nova User" }
            bio = profileUiState.bio
            selectedAvatarUrl = profileUiState.user.photoUrl.takeIf { it.isNotBlank() }
            featuredPhotos.clear()
            featuredPhotos.addAll(profileUiState.featuredPhotos.take(3))
            while (featuredPhotos.size < 3) {
                featuredPhotos.add("")
            }
            selectedInterests.clear()
            selectedInterests.addAll(profileUiState.interests)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
            NovaTopBar(
                title = if (isEditing) "Edit Profile" else "Profile Setup",
                subtitle = if (isEditing) "Update avatar, featured photos, interests, and bio." else "Profile media first, then the rest of your identity.",
                onBack = onBack
            )

            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                Text(
                    text = if (isEditing) "Update the parts people actually see." else "Finish your profile and move into Home.",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Avatar, featured photos, name, interests, and bio only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                AvatarPickerCard(
                    displayName = displayName,
                    avatarUrl = selectedAvatarUrl ?: fallbackAvatarUrl(displayName),
                    onClick = {
                        dirty = true
                        activePicker = ImagePickerTarget.Avatar
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Featured photos",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    repeat(3) { index ->
                        FeaturedSlot(
                            photoUrl = featuredPhotos.getOrNull(index).orEmpty(),
                            onClick = {
                                dirty = true
                                activePicker = ImagePickerTarget.Featured(index)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                NovaTextField(
                    label = "Display name",
                    value = displayName,
                    onValueChange = {
                        displayName = it
                        dirty = true
                    },
                    placeholder = "How people should call you"
                )

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "Interests",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    interestOptions.forEach { interest ->
                        NovaChip(
                            text = interest,
                            selected = selectedInterests.contains(interest),
                            onClick = {
                                dirty = true
                                if (selectedInterests.contains(interest)) {
                                    selectedInterests.remove(interest)
                                } else {
                                    selectedInterests.add(interest)
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                NovaTextField(
                    label = "Bio",
                    value = bio,
                    onValueChange = {
                        bio = it
                        dirty = true
                    },
                    placeholder = "Tell people what makes you interesting",
                    modifier = Modifier.height(120.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "No date of birth or gender fields. Keep the profile lightweight.",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                    fontSize = 12.sp
                )

                saveError?.let {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = it, color = Color(0xFFFF6B6B), fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(120.dp))
            }
        }

        NovaButton(
            text = if (isSaving) "Saving..." else if (isEditing) "Save changes" else "Continue to Home",
            onClick = {
                if (!isSaving) {
                    val resolvedAvatar = selectedAvatarUrl
                        ?.takeIf { it.isNotBlank() }
                        ?: profileUiState.user.photoUrl.takeIf { it.isNotBlank() }
                        ?: fallbackAvatarUrl(displayName)
                    val resolvedInterests = selectedInterests.toList().ifEmpty {
                        profileUiState.interests
                    }
                    val resolvedFeaturedPhotos = featuredPhotos.filter { it.isNotBlank() }.ifEmpty {
                        profileUiState.featuredPhotos
                    }.take(3)

                    scope.launch {
                        isSaving = true
                        saveError = null
                        runCatching {
                            profileViewModel.saveProfile(
                                displayName = displayName.trim().ifBlank { profileUiState.user.name.ifBlank { "Nova User" } },
                                bio = bio.trim(),
                                avatarUrl = resolvedAvatar,
                                featuredPhotos = resolvedFeaturedPhotos,
                                interests = resolvedInterests,
                            )
                        }.onSuccess {
                            onComplete()
                        }.onFailure {
                            saveError = "Could not save profile right now."
                        }
                        isSaving = false
                    }
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp)
        )

        if (activePicker != null) {
            val pickerTarget = activePicker!!
            ModalBottomSheet(
                onDismissRequest = { activePicker = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = MaterialTheme.colorScheme.background
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when (pickerTarget) {
                                ImagePickerTarget.Avatar -> "Choose avatar"
                                is ImagePickerTarget.Featured -> "Choose featured photo"
                            },
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        TextButton(onClick = { activePicker = null }) {
                            Text("Done", color = PurpleMain)
                        }
                    }

                    if (pickerTarget == ImagePickerTarget.Avatar) {
                        TextButton(
                            onClick = {
                                selectedAvatarUrl = null
                                dirty = true
                                activePicker = null
                            },
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Text("Use default avatar", color = PurpleMain)
                        }
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.heightIn(max = 420.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = if (pickerTarget == ImagePickerTarget.Avatar) avatarOptions else featuredOptions,
                            key = { it }
                        ) { imageUrl ->
                            Box(
                                modifier = Modifier
                                    .aspectRatio(0.8f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable {
                                        dirty = true
                                        when (pickerTarget) {
                                            ImagePickerTarget.Avatar -> selectedAvatarUrl = imageUrl
                                            is ImagePickerTarget.Featured -> {
                                                featuredPhotos[pickerTarget.index] = imageUrl
                                            }
                                        }
                                        activePicker = null
                                    }
                            ) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AvatarPickerCard(
    displayName: String,
    avatarUrl: String,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(contentAlignment = Alignment.BottomEnd, modifier = Modifier.size(132.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(2.dp, Brush.linearGradient(listOf(PurpleMain, PurplePink)), CircleShape)
                    .clickable(onClick = onClick)
            ) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = displayName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.2f))
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Tap to change",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(PurpleMain)
                    .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Change avatar",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun FeaturedSlot(
    photoUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .aspectRatio(0.82f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (photoUrl.isBlank()) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add photo",
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                modifier = Modifier.size(32.dp)
            )
        } else {
            AsyncImage(
                model = photoUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

private sealed interface ImagePickerTarget {
    data object Avatar : ImagePickerTarget
    data class Featured(val index: Int) : ImagePickerTarget
}

private fun avatarLibrary(): List<String> = listOf(
    SampleMedia.portrait1,
    SampleMedia.portrait2,
    SampleMedia.portrait3,
    SampleMedia.portrait4,
    SampleMedia.portrait5,
    SampleMedia.portrait6,
    SampleMedia.portrait7,
    SampleMedia.portrait8,
    SampleMedia.portrait9,
    SampleMedia.portrait10,
)

private fun featuredLibrary(): List<String> = listOf(
    SampleMedia.portrait1,
    SampleMedia.portrait2,
    SampleMedia.portrait3,
    SampleMedia.portrait4,
    SampleMedia.portrait5,
    SampleMedia.portrait6,
    SampleMedia.portrait7,
    SampleMedia.portrait8,
    SampleMedia.portrait9,
    SampleMedia.portrait10,
    SampleMedia.landscape1,
    SampleMedia.landscape2,
    SampleMedia.landscape3,
    SampleMedia.landscape4,
)

private fun fallbackAvatarUrl(displayName: String): String {
    val safeName = if (displayName.isBlank()) "Nova User" else displayName.trim()
    val encoded = URLEncoder.encode(safeName, StandardCharsets.UTF_8)
    return "https://ui-avatars.com/api/?name=$encoded&background=6C5CE7&color=FFFFFF&size=512"
}
