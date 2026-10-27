package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class UserAccountRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun saveAndGetUserProfile_authenticatedOwner_succeeds() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = UserAccountRepository(firestore)

        val profile = UserCloudProfile(
            userId = aliceUid,
            displayName = "Alice",
            email = ALICE_EMAIL,
            dateOfBirth = "2001-08-19",
            mostPlayedGameName = "Genshin Impact",
            mostPlayedGamePkg = "com.miHoYo.GenshinImpact",
            mostPlayedMinutes = 320L,
            themeMode = "DARK_HUB",
            selectedBoostMode = "BEAST"
        )

        val saveResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveOrUpdateUserProfile(profile)
        }
        assertTrue("Save should succeed: ${saveResult.exceptionOrNull()}", saveResult.isSuccess)

        val fetchResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getUserProfile(aliceUid)
        }
        assertTrue(fetchResult.isSuccess)
        val fetched = fetchResult.getOrNull()
        assertNotNull(fetched)
        assertEquals("2001-08-19", fetched?.dateOfBirth)
        assertEquals("BEAST", fetched?.selectedBoostMode)
    }

    @Test
    fun getUserProfile_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = UserAccountRepository(firestore)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.saveOrUpdateUserProfile(UserCloudProfile(userId = aliceUid, displayName = "Alice"))
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = UserAccountRepository(firestore)
        val crossReadResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getUserProfile(aliceUid)
        }
        assertTrue(crossReadResult.isFailure)
        val ex = crossReadResult.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(ex)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, ex?.code)
    }

    @Test
    fun observeUserProfile_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repo = UserAccountRepository(firestore)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.saveOrUpdateUserProfile(UserCloudProfile(userId = aliceUid, displayName = "Alice"))
        }

        auth.signOut()
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeUserProfile(aliceUid).first()
            }
            fail("Expected exception for unauthenticated observation")
        } catch (e: Exception) {
            val firestoreEx = e.unwrapFirestoreException()
            assertNotNull("Expected nested FirebaseFirestoreException, got $e", firestoreEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice@test.com"
        const val BOB_EMAIL = "bob@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
