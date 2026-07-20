package com.nova.app.core.domain

import com.nova.app.core.data.NovaRepository
import com.nova.app.core.model.AppSettings
import com.nova.app.core.model.ChatUiState
import com.nova.app.core.model.CommunityUiState
import com.nova.app.core.model.DiscoverUiState
import com.nova.app.core.model.HomeUiState
import com.nova.app.core.model.MessagesUiState
import com.nova.app.core.model.ProfileUiState
import com.nova.app.core.model.SessionState
import com.nova.app.core.model.ChatAttachmentDraft
import com.nova.app.core.backend.BackendProfileUpdateRequest
import kotlinx.coroutines.flow.StateFlow

class ObserveSessionUseCase(private val repository: NovaRepository) {
    operator fun invoke(): StateFlow<SessionState> = repository.session
}

class ObserveSettingsUseCase(private val repository: NovaRepository) {
    operator fun invoke(): StateFlow<AppSettings> = repository.settings
}

class ObserveHomeUseCase(private val repository: NovaRepository) {
    operator fun invoke(): StateFlow<HomeUiState> = repository.home
}

class ObserveDiscoverUseCase(private val repository: NovaRepository) {
    operator fun invoke(): StateFlow<DiscoverUiState> = repository.discover
}

class ObserveMessagesUseCase(private val repository: NovaRepository) {
    operator fun invoke(): StateFlow<MessagesUiState> = repository.messages
}

class ObserveChatUseCase(private val repository: NovaRepository) {
    operator fun invoke(): StateFlow<ChatUiState> = repository.chat
}

class ObserveCommunityUseCase(private val repository: NovaRepository) {
    operator fun invoke(): StateFlow<CommunityUiState> = repository.community
}

class ObserveProfileUseCase(private val repository: NovaRepository) {
    operator fun invoke(): StateFlow<ProfileUiState> = repository.profile
}

class CompleteOnboardingUseCase(private val repository: NovaRepository) {
    suspend operator fun invoke() = repository.completeOnboarding()
}

class CompleteAuthUseCase(private val repository: NovaRepository) {
    suspend operator fun invoke() = repository.completeAuth()
}

class CompleteProfileUseCase(private val repository: NovaRepository) {
    suspend operator fun invoke() = repository.completeProfile()
}

class ToggleThemeUseCase(private val repository: NovaRepository) {
    suspend operator fun invoke() = repository.toggleTheme()
}

class TogglePremiumUseCase(private val repository: NovaRepository) {
    suspend operator fun invoke() = repository.togglePremium()
}

class UpdateLanguageUseCase(private val repository: NovaRepository) {
    suspend operator fun invoke(language: String) = repository.updateLanguage(language)
}

class ToggleIncognitoUseCase(private val repository: NovaRepository) {
    suspend operator fun invoke() = repository.toggleIncognito()
}

class ToggleTravelModeUseCase(private val repository: NovaRepository) {
    suspend operator fun invoke() = repository.toggleTravelMode()
}

class RefreshProfileUseCase(private val repository: NovaRepository) {
    suspend operator fun invoke() = repository.refreshProfile()
}

class UpdateProfileUseCase(private val repository: NovaRepository) {
    suspend operator fun invoke(request: BackendProfileUpdateRequest) = repository.updateProfile(request)
}

class LikeCandidateUseCase(private val repository: NovaRepository) {
    suspend operator fun invoke() = repository.likeCandidate()
}

class SuperLikeCandidateUseCase(private val repository: NovaRepository) {
    suspend operator fun invoke() = repository.superLikeCandidate()
}

class SkipCandidateUseCase(private val repository: NovaRepository) {
    suspend operator fun invoke() = repository.skipCandidate()
}

class SaveCandidateUseCase(private val repository: NovaRepository) {
    suspend operator fun invoke() = repository.saveCandidate()
}

class SendMessageUseCase(private val repository: NovaRepository) {
    suspend operator fun invoke(text: String, attachment: ChatAttachmentDraft? = null) = repository.sendMessage(text, attachment)
}

class ToggleTopicUseCase(private val repository: NovaRepository) {
    suspend operator fun invoke(topicId: String) = repository.toggleTopic(topicId)
}

class JoinEventUseCase(private val repository: NovaRepository) {
    suspend operator fun invoke(eventId: String) = repository.joinEvent(eventId)
}

class LogoutUseCase(private val repository: NovaRepository) {
    suspend operator fun invoke() = repository.logout()
}
