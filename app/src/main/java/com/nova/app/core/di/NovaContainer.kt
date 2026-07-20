package com.nova.app.core.di

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nova.app.core.backend.BackendRuntime
import com.nova.app.core.backend.DefaultBackendRuntime
import com.nova.app.core.data.FakeNovaRepository
import com.nova.app.core.data.NovaRepository
import com.nova.app.core.domain.CompleteAuthUseCase
import com.nova.app.core.domain.CompleteOnboardingUseCase
import com.nova.app.core.domain.CompleteProfileUseCase
import com.nova.app.core.domain.JoinEventUseCase
import com.nova.app.core.domain.LikeCandidateUseCase
import com.nova.app.core.domain.LogoutUseCase
import com.nova.app.core.domain.ObserveChatUseCase
import com.nova.app.core.domain.ObserveCommunityUseCase
import com.nova.app.core.domain.ObserveDiscoverUseCase
import com.nova.app.core.domain.ObserveHomeUseCase
import com.nova.app.core.domain.ObserveMessagesUseCase
import com.nova.app.core.domain.ObserveProfileUseCase
import com.nova.app.core.domain.ObserveSessionUseCase
import com.nova.app.core.domain.ObserveSettingsUseCase
import com.nova.app.core.domain.RefreshProfileUseCase
import com.nova.app.core.domain.SaveCandidateUseCase
import com.nova.app.core.domain.SendMessageUseCase
import com.nova.app.core.domain.SkipCandidateUseCase
import com.nova.app.core.domain.SuperLikeCandidateUseCase
import com.nova.app.core.domain.ToggleIncognitoUseCase
import com.nova.app.core.domain.TogglePremiumUseCase
import com.nova.app.core.domain.ToggleThemeUseCase
import com.nova.app.core.domain.ToggleTopicUseCase
import com.nova.app.core.domain.ToggleTravelModeUseCase
import com.nova.app.core.domain.UpdateLanguageUseCase
import com.nova.app.core.domain.UpdateProfileUseCase
import com.nova.app.core.viewmodel.CallViewModel
import com.nova.app.core.viewmodel.ChatViewModel
import com.nova.app.core.viewmodel.CommunityViewModel
import com.nova.app.core.viewmodel.DiscoverViewModel
import com.nova.app.core.viewmodel.FlowViewModel
import com.nova.app.core.viewmodel.HomeViewModel
import com.nova.app.core.viewmodel.LaunchViewModel
import com.nova.app.core.viewmodel.MessagesViewModel
import com.nova.app.core.viewmodel.NotificationsViewModel
import com.nova.app.core.viewmodel.ProfileViewModel
import com.nova.app.core.viewmodel.ProfileConnectionsViewModel
import com.nova.app.core.viewmodel.SearchViewModel

class NovaContainer {
    val repository: NovaRepository = FakeNovaRepository()
    val backendRuntime: BackendRuntime = DefaultBackendRuntime()

    val observeSessionUseCase = ObserveSessionUseCase(repository)
    val observeSettingsUseCase = ObserveSettingsUseCase(repository)
    val observeHomeUseCase = ObserveHomeUseCase(repository)
    val observeDiscoverUseCase = ObserveDiscoverUseCase(repository)
    val observeMessagesUseCase = ObserveMessagesUseCase(repository)
    val observeChatUseCase = ObserveChatUseCase(repository)
    val observeCommunityUseCase = ObserveCommunityUseCase(repository)
    val observeProfileUseCase = ObserveProfileUseCase(repository)

    val completeOnboardingUseCase = CompleteOnboardingUseCase(repository)
    val completeAuthUseCase = CompleteAuthUseCase(repository)
    val completeProfileUseCase = CompleteProfileUseCase(repository)
    val toggleThemeUseCase = ToggleThemeUseCase(repository)
    val togglePremiumUseCase = TogglePremiumUseCase(repository)
    val updateLanguageUseCase = UpdateLanguageUseCase(repository)
    val toggleIncognitoUseCase = ToggleIncognitoUseCase(repository)
    val toggleTravelModeUseCase = ToggleTravelModeUseCase(repository)
    val logoutUseCase = LogoutUseCase(repository)
    val refreshProfileUseCase = RefreshProfileUseCase(repository)
    val updateProfileUseCase = UpdateProfileUseCase(repository)
    val likeCandidateUseCase = LikeCandidateUseCase(repository)
    val superLikeCandidateUseCase = SuperLikeCandidateUseCase(repository)
    val skipCandidateUseCase = SkipCandidateUseCase(repository)
    val saveCandidateUseCase = SaveCandidateUseCase(repository)
    val sendMessageUseCase = SendMessageUseCase(repository)
    val toggleTopicUseCase = ToggleTopicUseCase(repository)
    val joinEventUseCase = JoinEventUseCase(repository)

    val viewModelFactory = NovaViewModelFactory(
        repository = repository,
        observeSessionUseCase = observeSessionUseCase,
        observeSettingsUseCase = observeSettingsUseCase,
        observeHomeUseCase = observeHomeUseCase,
        observeDiscoverUseCase = observeDiscoverUseCase,
        observeMessagesUseCase = observeMessagesUseCase,
        observeChatUseCase = observeChatUseCase,
        observeCommunityUseCase = observeCommunityUseCase,
        observeProfileUseCase = observeProfileUseCase,
        refreshProfileUseCase = refreshProfileUseCase,
        updateProfileUseCase = updateProfileUseCase,
        completeOnboardingUseCase = completeOnboardingUseCase,
        completeAuthUseCase = completeAuthUseCase,
        completeProfileUseCase = completeProfileUseCase,
        toggleThemeUseCase = toggleThemeUseCase,
        togglePremiumUseCase = togglePremiumUseCase,
        updateLanguageUseCase = updateLanguageUseCase,
        toggleIncognitoUseCase = toggleIncognitoUseCase,
        toggleTravelModeUseCase = toggleTravelModeUseCase,
        logoutUseCase = logoutUseCase,
        likeCandidateUseCase = likeCandidateUseCase,
        superLikeCandidateUseCase = superLikeCandidateUseCase,
        skipCandidateUseCase = skipCandidateUseCase,
        saveCandidateUseCase = saveCandidateUseCase,
        sendMessageUseCase = sendMessageUseCase,
        toggleTopicUseCase = toggleTopicUseCase,
        joinEventUseCase = joinEventUseCase,
        backendRuntime = backendRuntime,
    )
}

@Composable
fun rememberNovaContainer(): NovaContainer = remember { NovaContainer() }

class NovaViewModelFactory(
    private val repository: NovaRepository,
    private val observeSessionUseCase: ObserveSessionUseCase,
    private val observeSettingsUseCase: ObserveSettingsUseCase,
    private val observeHomeUseCase: ObserveHomeUseCase,
    private val observeDiscoverUseCase: ObserveDiscoverUseCase,
    private val observeMessagesUseCase: ObserveMessagesUseCase,
    private val observeChatUseCase: ObserveChatUseCase,
    private val observeCommunityUseCase: ObserveCommunityUseCase,
    private val observeProfileUseCase: ObserveProfileUseCase,
    private val refreshProfileUseCase: RefreshProfileUseCase,
    private val updateProfileUseCase: UpdateProfileUseCase,
    private val completeOnboardingUseCase: CompleteOnboardingUseCase,
    private val completeAuthUseCase: CompleteAuthUseCase,
    private val completeProfileUseCase: CompleteProfileUseCase,
    private val toggleThemeUseCase: ToggleThemeUseCase,
    private val togglePremiumUseCase: TogglePremiumUseCase,
    private val updateLanguageUseCase: UpdateLanguageUseCase,
    private val toggleIncognitoUseCase: ToggleIncognitoUseCase,
    private val toggleTravelModeUseCase: ToggleTravelModeUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val likeCandidateUseCase: LikeCandidateUseCase,
    private val superLikeCandidateUseCase: SuperLikeCandidateUseCase,
    private val skipCandidateUseCase: SkipCandidateUseCase,
    private val saveCandidateUseCase: SaveCandidateUseCase,
    private val sendMessageUseCase: SendMessageUseCase,
    private val toggleTopicUseCase: ToggleTopicUseCase,
    private val joinEventUseCase: JoinEventUseCase,
    private val backendRuntime: BackendRuntime,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(LaunchViewModel::class.java) ->
                LaunchViewModel(observeSessionUseCase)

            modelClass.isAssignableFrom(CallViewModel::class.java) ->
                CallViewModel(backendRuntime)

            modelClass.isAssignableFrom(FlowViewModel::class.java) ->
                FlowViewModel(
                    observeSession = observeSessionUseCase,
                    observeSettings = observeSettingsUseCase,
                    completeOnboardingUseCase = completeOnboardingUseCase,
                    completeAuthUseCase = completeAuthUseCase,
                    completeProfileUseCase = completeProfileUseCase,
                    toggleThemeUseCase = toggleThemeUseCase,
                    togglePremiumUseCase = togglePremiumUseCase,
                    updateLanguageUseCase = updateLanguageUseCase,
                    toggleIncognitoUseCase = toggleIncognitoUseCase,
                    toggleTravelModeUseCase = toggleTravelModeUseCase,
                    logoutUseCase = logoutUseCase,
                    backendRuntime = backendRuntime,
                )

            modelClass.isAssignableFrom(HomeViewModel::class.java) ->
                HomeViewModel(observeHomeUseCase)

            modelClass.isAssignableFrom(DiscoverViewModel::class.java) ->
                DiscoverViewModel(
                    observeDiscover = observeDiscoverUseCase,
                    likeCandidateUseCase = likeCandidateUseCase,
                    superLikeCandidateUseCase = superLikeCandidateUseCase,
                    skipCandidateUseCase = skipCandidateUseCase,
                    saveCandidateUseCase = saveCandidateUseCase,
                )

            modelClass.isAssignableFrom(MessagesViewModel::class.java) ->
                MessagesViewModel(observeMessagesUseCase)

            modelClass.isAssignableFrom(ChatViewModel::class.java) ->
                ChatViewModel(
                    observeChat = observeChatUseCase,
                    sendMessage = sendMessageUseCase,
                    repository = repository,
                )

            modelClass.isAssignableFrom(CommunityViewModel::class.java) ->
                CommunityViewModel(
                    observeCommunity = observeCommunityUseCase,
                    repository = repository,
                    toggleTopicUseCase = toggleTopicUseCase,
                    joinEventUseCase = joinEventUseCase,
                )

            modelClass.isAssignableFrom(ProfileViewModel::class.java) ->
                ProfileViewModel(
                    observeProfile = observeProfileUseCase,
                    repository = repository,
                    refreshProfileUseCase = refreshProfileUseCase,
                    updateProfileUseCase = updateProfileUseCase,
                    toggleThemeUseCase = toggleThemeUseCase,
                    togglePremiumUseCase = togglePremiumUseCase,
                    toggleIncognitoUseCase = toggleIncognitoUseCase,
                    toggleTravelModeUseCase = toggleTravelModeUseCase,
                    updateLanguageUseCase = updateLanguageUseCase,
                )

            modelClass.isAssignableFrom(ProfileConnectionsViewModel::class.java) ->
                ProfileConnectionsViewModel(backendRuntime)

            modelClass.isAssignableFrom(SearchViewModel::class.java) ->
                SearchViewModel(backendRuntime)

            modelClass.isAssignableFrom(NotificationsViewModel::class.java) ->
                NotificationsViewModel(backendRuntime)

            else -> error("Unknown ViewModel class: ${modelClass.name}")
        } as T
    }
}
