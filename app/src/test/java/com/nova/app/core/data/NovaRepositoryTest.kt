package com.nova.app.core.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Local state transitions of the repository (no backend runtime registered). */
class NovaRepositoryTest {

    private lateinit var repository: DefaultNovaRepository

    @Before
    fun setUp() {
        repository = DefaultNovaRepository()
    }

    @Test
    fun `completeOnboarding marks onboarding done and leaves first launch`() = runTest {
        repository.completeOnboarding()

        assertTrue(repository.session.value.onboardingCompleted)
        assertFalse(repository.session.value.isFirstLaunch)
    }

    @Test
    fun `completeAuth and completeProfile update the session`() = runTest {
        repository.completeAuth()
        repository.completeProfile()

        assertTrue(repository.session.value.otpVerified)
        assertTrue(repository.session.value.profileCompleted)
    }

    @Test
    fun `toggleTheme flips dark mode back and forth`() = runTest {
        val initial = repository.settings.value.darkMode

        repository.toggleTheme()
        assertNotEquals(initial, repository.settings.value.darkMode)

        repository.toggleTheme()
        assertEquals(initial, repository.settings.value.darkMode)
    }

    @Test
    fun `togglePremium enables premium`() = runTest {
        assertFalse(repository.settings.value.premiumEnabled)

        repository.togglePremium()

        assertTrue(repository.settings.value.premiumEnabled)
    }

    @Test
    fun `deleting a thread without a backend fails and keeps the list`() = runTest {
        val before = repository.messages.value.threads

        val deleted = repository.deleteThreadForMe("thread-1")

        assertFalse(deleted)
        assertEquals(before, repository.messages.value.threads)
    }
}
