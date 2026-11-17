package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class SquadReferralRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun fullDualSidedReferralLoop_claimAndVerifiedCoreAction_activatesAndEmitsToReferrer() = runBlocking {
        val uniqueSuffix = UUID.randomUUID().toString().replace("-", "").take(6)
        val aliceEmail = "alice_ref_$uniqueSuffix@test.com"
        val bobEmail = "bob_inv_$uniqueSuffix@test.com"
        val charlieEmail = "charlie_burner_$uniqueSuffix@test.com"
        val aliceHwHash = "hw_sha256_alice_device_$uniqueSuffix"
        val bobHwHash = "hw_sha256_bob_device_$uniqueSuffix"

        // 1. Alice signs in and registers her Squad Guest Pass code (bound to her hardware hash)
        val aliceUid = signInTestUser(aliceEmail)
        val aliceRepo = SquadReferralRepository(firestore)
        val regResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.registerOrUpdateMyReferralCode(
                username = "alice_$uniqueSuffix",
                displayName = "Alice Squad Leader",
                referrerDeviceHash = aliceHwHash
            )
        }
        assertTrue("Alice code registration should succeed: ${regResult.exceptionOrNull()}", regResult.isSuccess)
        val aliceCode = regResult.getOrThrow().code

        // 2. Anti-Fraud: Alice cannot claim her own referral code
        val selfClaimResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.claimGuestPass(
                rawCodeOrLink = aliceCode,
                inviteeUsername = "alice_$uniqueSuffix",
                inviteeDisplayName = "Alice",
                deviceFingerprintHash = aliceHwHash
            )
        }
        assertTrue("Self referral must be rejected", selfClaimResult.isFailure)

        // 2b. Anti-Fraud: URL links are strictly rejected (only GMAX-... code allowed)
        val bobUid = signInTestUser(bobEmail)
        val bobRepo = SquadReferralRepository(firestore)
        val urlAttemptResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.claimGuestPass(
                rawCodeOrLink = "https://gmaxhub.app/pass?ref=$aliceCode",
                inviteeUsername = "bob_$uniqueSuffix",
                inviteeDisplayName = "Bob Duo",
                deviceFingerprintHash = bobHwHash
            )
        }
        assertTrue("URL referral attempt must be rejected", urlAttemptResult.isFailure)

        // 2c. Anti-Fraud: Bob attempting to claim Alice's code from Alice's same physical device is rejected
        val sameDeviceSelfRefResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.claimGuestPass(
                rawCodeOrLink = aliceCode,
                inviteeUsername = "bob_$uniqueSuffix",
                inviteeDisplayName = "Bob Duo",
                deviceFingerprintHash = aliceHwHash
            )
        }
        assertTrue("Same-device self-referral attempt must be rejected", sameDeviceSelfRefResult.isFailure)

        // 3. Bob claims Alice's Guest Pass code from his own device -> status starts as PENDING_ACTIVATION
        val claimResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.claimGuestPass(
                rawCodeOrLink = aliceCode,
                inviteeUsername = "bob_$uniqueSuffix",
                inviteeDisplayName = "Bob Duo",
                deviceFingerprintHash = bobHwHash
            )
        }
        assertTrue("Bob claim should succeed: ${claimResult.exceptionOrNull()}", claimResult.isSuccess)
        val pendingClaim = claimResult.getOrThrow()
        assertEquals(ReferralActivation.STATUS_PENDING, pendingClaim.status)
        assertEquals(aliceUid, pendingClaim.referrerUid)
        assertFalse(pendingClaim.isActivated)

        // 4. Anti-Fraud: Charlie (burner account on Bob's same physical device) is blocked by hardware fingerprint
        signInTestUser(charlieEmail)
        val charlieRepo = SquadReferralRepository(firestore)
        val burnerClaimResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            charlieRepo.claimGuestPass(
                rawCodeOrLink = aliceCode,
                inviteeUsername = "charlie_$uniqueSuffix",
                inviteeDisplayName = "Charlie Burner",
                deviceFingerprintHash = bobHwHash
            )
        }
        assertTrue("Burner account on same hardware ID must be rejected", burnerClaimResult.isFailure)

        // 5. Bob signs back in, plays for 5 minutes (not yet 15m) -> remains PENDING_ACTIVATION
        signInTestUser(bobEmail)
        val partialProgress = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.recordVerifiedCoreActionProgress(
                inviteeUsername = "bob_$uniqueSuffix",
                inviteeDisplayName = "Bob Duo",
                additionalBoostedMinutes = 5L,
                completedHudMatch = false
            )
        }
        assertTrue(partialProgress.isSuccess)
        assertEquals(ReferralActivation.STATUS_PENDING, partialProgress.getOrThrow()?.status)

        // 6. Bob completes 1 full match with HUD active -> transitions to ACTIVATED
        val activatedProgress = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.recordVerifiedCoreActionProgress(
                inviteeUsername = "bob_$uniqueSuffix",
                inviteeDisplayName = "Bob Duo",
                additionalBoostedMinutes = 2L,
                completedHudMatch = true
            )
        }
        assertTrue("Activation update should succeed: ${activatedProgress.exceptionOrNull()}", activatedProgress.isSuccess)
        val activated = activatedProgress.getOrThrow()
        assertNotNull(activated)
        assertEquals(ReferralActivation.STATUS_ACTIVATED, activated?.status)
        assertEquals(ReferralActivation.TRIGGER_1_HUD_MATCH, activated?.activationTrigger)

        // 7. Alice signs in and observes her real-time squad roster -> sees Bob as ACTIVATED
        signInTestUser(aliceEmail)
        val roster = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeReferrerSquadActivations(aliceUid).first { list ->
                list.any { it.inviteeUid == bobUid && it.isActivated }
            }
        }
        assertEquals(1, roster.count { it.isActivated })
    }

    @Test
    fun observeReferrerSquadActivations_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = SquadReferralRepository(firestore)
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeReferrerSquadActivations("any_uid").first()
            }
            fail("Expected PERMISSION_DENIED for unauthenticated caller")
        } catch (e: Exception) {
            val firestoreEx = e.unwrapFirestoreException()
            assertNotNull("Expected nested FirebaseFirestoreException, got $e", firestoreEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3500L
    }
}
