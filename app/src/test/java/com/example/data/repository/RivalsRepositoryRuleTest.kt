package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class RivalsRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun fullRivalsSquadFlow_createEditCodeJoinUsernameInviteTauntAndDelete_succeedsAndEnforcesRules() = runBlocking {
        val suffix = UUID.randomUUID().toString().replace("-", "").take(6).lowercase()
        val leaderEmail = "leader_riv_$suffix@test.com"
        val friendCodeEmail = "friend_code_$suffix@test.com"
        val friendSearchEmail = "friend_search_$suffix@test.com"

        // 1. Sign in Leader and claim username so leaderboard can resolve scores
        val leaderUid = signInTestUser(leaderEmail)
        val leaderAccountRepo = UserAccountRepository(firestore)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            leaderAccountRepo.saveOrUpdateUserProfile(
                UserCloudProfile(
                    id = leaderUid,
                    userId = leaderUid,
                    displayName = "Alpha Leader",
                    username = "alpha_$suffix",
                    email = leaderEmail,
                    gamerscore = 4200L,
                    totalPlayTimeMinutes = 240L
                )
            )
        }

        // 2. Sign in Friend 2 (who will be invited via username search) to register their username
        val friendSearchUid = signInTestUser(friendSearchEmail)
        val friendSearchAccountRepo = UserAccountRepository(firestore)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            friendSearchAccountRepo.saveOrUpdateUserProfile(
                UserCloudProfile(
                    id = friendSearchUid,
                    userId = friendSearchUid,
                    displayName = "Charlie Sniper",
                    username = "charlie_$suffix",
                    email = friendSearchEmail,
                    gamerscore = 5100L,
                    totalPlayTimeMinutes = 310L
                )
            )
        }

        // 3. Sign back in as Leader and create a Rivals Squad (with 150-char max bio)
        signInTestUser(leaderEmail)
        val leaderRivalsRepo = RivalsRepository(firestore)
        val exact150Bio = "R".repeat(150)
        val createRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            leaderRivalsRepo.createSquad(
                squadName = "Apex Rivals $suffix",
                bio = exact150Bio,
                leaderUsername = "alpha_$suffix",
                leaderDisplayName = "Alpha Leader"
            )
        }
        assertTrue("Squad creation should succeed: ${createRes.exceptionOrNull()}", createRes.isSuccess)
        val squad = createRes.getOrThrow()
        assertEquals(150, squad.bio.length)
        assertEquals(listOf(leaderUid), squad.memberUids)
        assertTrue(squad.squadCode.startsWith("RIVALS-"))

        // 4. Single-Squad Exclusivity: Leader already in a squad cannot create another squad
        val duplicateCreateRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            leaderRivalsRepo.createSquad(
                squadName = "Second Squad",
                bio = "Not allowed",
                leaderUsername = "alpha_$suffix",
                leaderDisplayName = "Alpha Leader"
            )
        }
        assertTrue("User already in a squad cannot create another squad", duplicateCreateRes.isFailure)

        // 5. Squad Code Auto-Join: Friend 1 signs in and enters the Squad Code -> joins automatically
        val friendCodeUid = signInTestUser(friendCodeEmail)
        val friendCodeAccountRepo = UserAccountRepository(firestore)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            friendCodeAccountRepo.saveOrUpdateUserProfile(
                UserCloudProfile(
                    id = friendCodeUid,
                    userId = friendCodeUid,
                    displayName = "Bravo Rusher",
                    username = "bravo_$suffix",
                    email = friendCodeEmail,
                    gamerscore = 3950L,
                    totalPlayTimeMinutes = 190L
                )
            )
        }
        val friendCodeRivalsRepo = RivalsRepository(firestore)
        val codeJoinRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            friendCodeRivalsRepo.joinSquadByCode(squad.squadCode)
        }
        assertTrue("Joining by Squad Code should succeed automatically: ${codeJoinRes.exceptionOrNull()}", codeJoinRes.isSuccess)
        val joinedSquad = codeJoinRes.getOrThrow()
        assertTrue(joinedSquad.memberUids.contains(friendCodeUid))
        assertEquals(2, joinedSquad.memberUids.size)

        // 6. Non-leader cannot edit Squad Name/Bio or delete Squad
        val nonLeaderEditRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            friendCodeRivalsRepo.updateSquadDetails(
                squadId = squad.id,
                newName = "Hacked Name",
                newBio = "Hacked Bio"
            )
        }
        assertTrue("Non-leader must not be able to edit squad name or bio", nonLeaderEditRes.isFailure)

        // 7. Leader edits Squad Name & Bio in real time and invites Friend 2 by searching username
        signInTestUser(leaderEmail)
        val leaderEditRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            leaderRivalsRepo.updateSquadDetails(
                squadId = squad.id,
                newName = "Pro Rivals $suffix",
                newBio = "Competing for 1st place crown!"
            )
        }
        assertTrue("Leader should be able to edit squad details: ${leaderEditRes.exceptionOrNull()}", leaderEditRes.isSuccess)
        assertEquals("Pro Rivals $suffix", leaderEditRes.getOrThrow().name)

        val inviteRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            leaderRivalsRepo.invitePlayerByUsername(
                squadId = squad.id,
                targetUsernameOrQuery = "charlie_$suffix",
                inviterUsername = "alpha_$suffix",
                inviterDisplayName = "Alpha Leader"
            )
        }
        assertTrue("Username invite should succeed: ${inviteRes.exceptionOrNull()}", inviteRes.isSuccess)
        val createdInvite = inviteRes.getOrThrow()
        assertEquals(RivalsSquadInvite.STATUS_PENDING, createdInvite.status)
        assertEquals(friendSearchUid, createdInvite.inviteeUid)

        // 8. Friend 2 signs in, sees pending invite, and accepts it to join the squad
        signInTestUser(friendSearchEmail)
        val friendSearchRivalsRepo = RivalsRepository(firestore)
        val pendingInvites = withTimeout(FLOW_TIMEOUT_MS) {
            friendSearchRivalsRepo.observeIncomingPendingInvites(friendSearchUid).first { it.isNotEmpty() }
        }
        assertEquals(1, pendingInvites.size)

        val acceptRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            friendSearchRivalsRepo.acceptSquadInvite(createdInvite.id)
        }
        assertTrue("Accepting squad invite should succeed: ${acceptRes.exceptionOrNull()}", acceptRes.isSuccess)
        assertEquals(3, acceptRes.getOrThrow().memberUids.size)

        // 9. Observe 5-Rank Squad Leaderboard -> Charlie (5100) is 1st with Crown, Alpha (4200) is 2nd, Bravo (3950) is 3rd
        val leaderboard = withTimeout(FLOW_TIMEOUT_MS) {
            friendSearchRivalsRepo.observeSquadMembersLeaderboard(
                memberUids = acceptRes.getOrThrow().memberUids,
                leaderUid = leaderUid
            ).first { it.size == 3 }
        }
        assertEquals(3, leaderboard.size)
        assertEquals(friendSearchUid, leaderboard[0].userId)
        assertEquals(1, leaderboard[0].rankPosition)
        assertEquals("1st", leaderboard[0].rankLabel)
        assertTrue("1st place player in squad dashboard must have crown", leaderboard[0].hasFirstPlaceCrown)
        assertFalse("2nd place player must not have crown", leaderboard[1].hasFirstPlaceCrown)
        assertFalse("3rd place player must not have crown", leaderboard[2].hasFirstPlaceCrown)

        // 10. One-Click Pre-Set Taunt: Charlie sends a preset taunt to Alpha after passing
        val passEventKey = "${squad.id}_${friendSearchUid}_passed_${leaderUid}_${RivalsRepository.currentUtcSeasonMonthKey()}"
        val tauntRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            friendSearchRivalsRepo.sendPresetTaunt(
                squadId = squad.id,
                senderUsername = "charlie_$suffix",
                senderDisplayName = "Charlie Sniper",
                recipientUid = leaderUid,
                recipientUsername = "alpha_$suffix",
                tauntText = "Catch me if you can",
                passEventKey = passEventKey
            )
        }
        assertTrue("Sending valid preset taunt should succeed: ${tauntRes.exceptionOrNull()}", tauntRes.isSuccess)

        // Custom text taunt must be rejected
        val invalidTauntRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            friendSearchRivalsRepo.sendPresetTaunt(
                squadId = squad.id,
                senderUsername = "charlie_$suffix",
                senderDisplayName = "Charlie Sniper",
                recipientUid = leaderUid,
                recipientUsername = "alpha_$suffix",
                tauntText = "Custom text not in preset list",
                passEventKey = "${passEventKey}_custom"
            )
        }
        assertTrue("Custom taunt text must be rejected", invalidTauntRes.isFailure)

        // 11. Friend 1 leaves the squad -> squad drops to 2 members
        signInTestUser(friendCodeEmail)
        val leaveRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            friendCodeRivalsRepo.leaveSquad(squad.id)
        }
        assertTrue("Leaving squad should succeed: ${leaveRes.exceptionOrNull()}", leaveRes.isSuccess)

        // 12. Leader deletes the squad -> squad and squad code are deleted from Firebase Firestore
        signInTestUser(leaderEmail)
        val deleteRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            leaderRivalsRepo.deleteSquad(squad.id)
        }
        assertTrue("Leader deleting squad should succeed: ${deleteRes.exceptionOrNull()}", deleteRes.isSuccess)
        val activeAfterDelete = withTimeout(DEFAULT_TIMEOUT_MS) {
            leaderRivalsRepo.getMyActiveSquad(leaderUid).getOrThrow()
        }
        assertNull("Squad should no longer exist in Firestore after leader deletion", activeAfterDelete)
    }

    @Test
    fun observeMyActiveSquad_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = RivalsRepository(firestore)
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeMyActiveSquad("unauth_uid").first()
            }
            fail("Expected PERMISSION_DENIED for unauthenticated caller")
        } catch (e: Exception) {
            val firestoreEx = e.unwrapFirestoreException()
            assertNotNull("Expected nested FirebaseFirestoreException, got $e", firestoreEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 6000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
