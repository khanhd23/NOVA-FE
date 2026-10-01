package com.nova.app.feature.discover

import com.nova.app.core.designsystem.NovaColors

import com.nova.app.core.designsystem.NovaBrand

import com.nova.app.core.i18n.interestLabel

import com.nova.app.core.i18n.localizedMessage

import androidx.compose.ui.res.stringResource
import com.nova.app.R

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nova.app.core.model.DiscoverUiState
import com.nova.app.core.model.DiscoveryCandidate
import com.nova.app.core.state.NovaLoadState
import com.nova.app.core.ui.NovaChip
import com.nova.app.core.ui.NovaTopLoadingBar
import com.nova.app.core.ui.NovaTopBar
import com.nova.app.ui.theme.NOVATheme
import com.nova.app.ui.theme.PurpleMain
import com.nova.app.ui.theme.PurplePink
import com.nova.app.ui.theme.SuccessGreen
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    uiState: NovaLoadState<DiscoverUiState>,
    onApplyFilters: (String, Int, Int) -> Unit,
    onSkip: () -> Unit,
    onLike: (DiscoveryCandidate) -> Unit,
    onPoke: () -> Unit,
    onClearMessage: () -> Unit,
) {
    val state = (uiState as? NovaLoadState.Success)?.data
    var showFilters by remember { mutableStateOf(false) }
    var selectedGender by remember(state?.selectedGender) { mutableStateOf(state?.selectedGender ?: "Both") }
    var ageRange by remember(state?.minAge, state?.maxAge) {
        mutableStateOf((state?.minAge ?: 16).toFloat()..(state?.maxAge ?: 70).toFloat())
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val sheetState = rememberModalBottomSheetState()

    LaunchedEffect(state?.pokeMessage) {
        val message = state?.pokeMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onClearMessage()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                NovaTopBar(
                    title = stringResource(R.string.discover_title),
                    actions = {
                        IconButton(onClick = { showFilters = true }) {
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = stringResource(R.string.discover_filter),
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                )
                NovaTopLoadingBar(visible = uiState is NovaLoadState.Loading || state?.loading == true)

                DiscoverBody(
                    state = state,
                    loading = uiState is NovaLoadState.Loading || state?.loading == true,
                    onSkip = onSkip,
                    onLike = onLike,
                    onPoke = onPoke,
                )
            }

            if (showFilters) {
                ModalBottomSheet(
                    onDismissRequest = { showFilters = false },
                    sheetState = sheetState,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    FilterContent(
                        selectedGender = selectedGender,
                        ageRange = ageRange,
                        onGenderChange = { selectedGender = it },
                        onAgeRangeChange = { ageRange = it },
                        onApply = {
                            onApplyFilters(
                                selectedGender,
                                ageRange.start.toInt(),
                                ageRange.endInclusive.toInt(),
                            )
                            showFilters = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.DiscoverBody(
    state: DiscoverUiState?,
    loading: Boolean,
    onSkip: () -> Unit,
    onLike: (DiscoveryCandidate) -> Unit,
    onPoke: () -> Unit,
) {
    val candidate = state?.queue?.getOrNull(state.activeIndex)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .padding(horizontal = 16.dp)
    ) {
        when {
            loading && candidate == null -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = PurplePink,
            )
            candidate == null -> EmptyDiscoverState(
                message = state?.error?.let { localizedMessage(it) },
                modifier = Modifier.align(Alignment.Center),
            )
            else -> SwipeCard(
                candidate = candidate,
                onSwipeAway = onSkip,
            )
        }
    }

    ActionButtons(
        enabled = candidate != null,
        onSkip = onSkip,
        onLike = { candidate?.let(onLike) },
        onPoke = onPoke,
    )
    Spacer(modifier = Modifier.height(100.dp))
}

@Composable
fun FilterContent(
    selectedGender: String,
    ageRange: ClosedFloatingPointRange<Float>,
    onGenderChange: (String) -> Unit,
    onAgeRangeChange: (ClosedFloatingPointRange<Float>) -> Unit,
    onApply: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .padding(bottom = 32.dp)
    ) {
        Text(stringResource(R.string.discover_filters), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

        Spacer(modifier = Modifier.height(24.dp))

        Text(stringResource(R.string.setup_gender), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NovaChip(text = stringResource(R.string.gender_male), selected = selectedGender == "Male", onClick = { onGenderChange("Male") })
            NovaChip(text = stringResource(R.string.gender_female), selected = selectedGender == "Female", onClick = { onGenderChange("Female") })
            NovaChip(text = stringResource(R.string.gender_both), selected = selectedGender == "Both", onClick = { onGenderChange("Both") })
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.discover_age_range), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(R.string.discover_age_range_value, ageRange.start.toInt(), ageRange.endInclusive.toInt()), style = MaterialTheme.typography.bodyMedium, color = PurplePink)
        }

        RangeSlider(
            value = ageRange,
            onValueChange = onAgeRangeChange,
            valueRange = 16f..70f,
            modifier = Modifier.padding(top = 8.dp),
            colors = SliderDefaults.colors(
                thumbColor = PurplePink,
                activeTrackColor = PurplePink,
                inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.24f)
            )
        )

        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = onApply,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NovaBrand.Start),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(stringResource(R.string.discover_apply_filters), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SwipeCard(
    candidate: DiscoveryCandidate,
    onSwipeAway: () -> Unit,
) {
    var dragAmount by remember(candidate.candidateId, candidate.user.id) { mutableFloatStateOf(0f) }
    val heroUrl = candidate.gallery.firstOrNull().orEmpty().ifBlank { candidate.user.photoUrl }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(32.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(candidate.candidateId, candidate.user.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (abs(dragAmount) > 120f) {
                            onSwipeAway()
                        }
                        dragAmount = 0f
                    },
                    onHorizontalDrag = { _, dragDelta ->
                        dragAmount += dragDelta
                    }
                )
            }
    ) {
        AsyncImage(
            model = heroUrl,
            contentDescription = stringResource(R.string.discover_avatar_of, candidate.user.name),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.86f)),
                        startY = 300f
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${candidate.user.name}, ${candidate.user.age}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Default.Person, contentDescription = null, tint = NovaColors.current.female, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                if (candidate.user.online) {
                    Box(modifier = Modifier.clip(CircleShape).background(SuccessGreen).size(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = candidate.bio,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.86f),
                maxLines = 2,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NovaChip(text = stringResource(R.string.discover_ai_match, candidate.compatibility.toString()), selected = true)
                if (candidate.user.city.isNotBlank()) {
                    NovaChip(text = candidate.user.city, selected = true)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                candidate.commonInterests.take(3).forEach { interest ->
                    NovaChip(text = interestLabel(interest), selected = true)
                }
            }
        }
    }
}

@Composable
fun ActionButtons(
    enabled: Boolean,
    onSkip: () -> Unit,
    onLike: () -> Unit,
    onPoke: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionButton(Icons.Default.Close, MaterialTheme.colorScheme.onBackground, MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), 64.dp, enabled, onSkip)
        ActionButton(Icons.Default.Favorite, MaterialTheme.colorScheme.onBackground, Brush.linearGradient(NovaBrand.gradient), 80.dp, enabled, onLike)
        ActionButton(Icons.Default.TouchApp, PurpleMain, MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), 64.dp, enabled, onPoke)
    }
}

@Composable
fun ActionButton(icon: ImageVector, iconColor: Color, background: Any, size: Dp, enabled: Boolean, onClick: () -> Unit) {
    val modifier = Modifier
        .size(size)
        .clip(CircleShape)
        .then(
            if (background is Brush) Modifier.background(background)
            else Modifier.background(background as Color)
        )
        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)

    IconButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Icon(icon, contentDescription = null, tint = iconColor.copy(alpha = if (enabled) 1f else 0.35f), modifier = Modifier.size(size / 2))
    }
}

@Composable
private fun EmptyDiscoverState(message: String?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.discover_empty_title), color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
        Text(message ?: stringResource(R.string.discover_empty_desc), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.68f))
    }
}

@Preview
@Composable
fun DiscoverScreenPreview() {
    NOVATheme {
        DiscoverScreen(
            uiState = NovaLoadState.Success(DiscoverUiState(queue = emptyList(), activeIndex = 0)),
            onApplyFilters = { _, _, _ -> },
            onSkip = {},
            onLike = { _ -> },
            onPoke = {},
            onClearMessage = {},
        )
    }
}

@Preview
@Composable
fun FilterContentPreview() {
    NOVATheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            FilterContent(
                selectedGender = "Female",
                ageRange = 20f..30f,
                onGenderChange = {},
                onAgeRangeChange = {},
                onApply = {}
            )
        }
    }
}

