package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.util.AppStrings
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

@IgnoreExtraProperties
data class RivalsSquad(
    @DocumentId val id: String = "",
    val squadId: String = "",
    val name: String = "",
    val bio: String = "",
    val squadCode: String = "",
    val leaderUid: String = "",
    val leaderUsername: String = "",
    val memberUids: List<String> = emptyList(),
    val seasonMonthKey: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class RivalsSquadCodeEntry(
    @DocumentId val id: String = "",
    val squadCode: String = "",
    val squadId: String = "",
    val leaderUid: String = "",
    val squadName: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class RivalsSquadInvite(
    @DocumentId val id: String = "",
    val inviteId: String = "",
    val squadId: String = "",
    val squadName: String = "",
    val inviterUid: String = "",
    val inviterUsername: String = "",
    val inviteeUid: String = "",
    val inviteeUsername: String = "",
    val status: String = STATUS_PENDING,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_ACCEPTED = "ACCEPTED"
        const val STATUS_DECLINED = "DECLINED"
    }
}

@IgnoreExtraProperties
data class RivalsTaunt(
    @DocumentId val id: String = "",
    val tauntId: String = "",
    val squadId: String = "",
    val senderUid: String = "",
    val senderName: String = "",
    val receiverUid: String = "",
    val receiverName: String = "",
    val tauntText: String = "",
    val passEventKey: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    val senderDisplayName: String get() = senderName
    val senderUsername: String get() = senderName
    val recipientUid: String get() = receiverUid
}

data class RivalsSquadMemberRank(
    val uid: String,
    val username: String,
    val displayName: String,
    val photoUrl: String,
    val countryCode: String,
    val gamerscore: Long,
    val totalPlayTimeMinutes: Long,
    val isLeader: Boolean,
    val rankNumber: Int,
    val rankPlacementLabel: String,
    val hasFirstPlaceCrown: Boolean
) {
    val userId: String get() = uid
    val rankPosition: Int get() = rankNumber
    val rankLabel: String get() = rankPlacementLabel
    val isSquadLeader: Boolean get() = isLeader
}

data class RivalsPendingPassTauntOpportunity(
    val passEventKey: String,
    val squadId: String,
    val passedMemberUid: String = "",
    val passedMemberUsername: String = "",
    val passedMemberDisplayName: String = "",
    val newMyRankLabel: String = "1st",
    val tauntAlreadySent: Boolean = false
) {
    val overtakenFriendUid: String get() = passedMemberUid
    val overtakenFriendName: String get() = passedMemberDisplayName.ifBlank { passedMemberUsername }
}

data class RivalsUiState(
    val isAuthenticated: Boolean = false,
    val currentUserId: String = "",
    val currentUsername: String = "",
    val currentDisplayName: String = "",
    val activeSquad: RivalsSquad? = null,
    val leaderboard: List<RivalsSquadMemberRank> = emptyList(),
    val incomingInvites: List<RivalsSquadInvite> = emptyList(),
    val outgoingInvites: List<RivalsSquadInvite> = emptyList(),
    val recentIncomingTaunts: List<RivalsTaunt> = emptyList(),
    val pendingPassTauntOpportunities: List<RivalsPendingPassTauntOpportunity> = emptyList(),
    val searchCandidates: List<UsernameClaim> = emptyList(),
    val isSearchingPlayers: Boolean = false,
    val boosterActivatedCount: Int = 0,
    val recordingCompletedCount: Int = 0,
    val overlayActivatedCount: Int = 0,
    val currentSeasonMonthKey: String = "",
    val nextResetEpochMs: Long = 0L,
    val formattedResetCountdown: String = "",
    val showInviteSquadPopup: Boolean = false,
    val inviteSquadPopupReason: String = "",
    val statusMessage: String? = null,
    val isErrorStatus: Boolean = false,
    val isSubmitting: Boolean = false
) {
    val isInSquad: Boolean
        get() = activeSquad != null

    val isSquadLeader: Boolean
        get() = activeSquad != null && activeSquad.leaderUid == currentUserId && currentUserId.isNotBlank()
}

class RivalsRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth: FirebaseAuth
        get() = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException(
                AppStrings.trText(
                    en = "Sign in with Google first to use Rivals Squads.",
                    es = "Inicia sesión con Google primero para usar Escuadrones Rivals."
                )
            )
    }

    companion object {
        const val MAX_SQUAD_MEMBERS = 5
        const val MAX_SQUAD_NAME_LENGTH = 40
        const val MAX_SQUAD_BIO_LENGTH = 150
        const val THREAT_POINTS_THRESHOLD = 500L
        const val THREE_HOUR_SESSION_MINUTES = 180L
        const val EXACT_12_HOURS_MS = 12L * 60L * 60L * 1000L

        val RANK_PLACEMENTS: List<String> = listOf("1st", "2nd", "3rd", "4th", "5th")
        val SQUAD_RANK_LABELS: List<String> get() = RANK_PLACEMENTS

        val PRESET_TAUNTS: List<String> = listOf(
            "Better luck next time",
            "Catch me if you can",
            "Your too slow",
            "ez",
            "Noobs",
            "GG",
            "Im Just Better"
        )

        private val STRICT_SQUAD_CODE_REGEX = Regex("^RIVALS-[A-Z0-9_\\-]{3,25}$")
        private val EMBEDDED_SQUAD_CODE_REGEX = Regex("\\b(RIVALS-[A-Za-z0-9_\\-]{3,25})\\b")

        fun rankLabelForPosition(rank1Based: Int): String {
            return RANK_PLACEMENTS.getOrElse((rank1Based - 1).coerceIn(0, 4)) { "${rank1Based}th" }
        }

        fun generateSquadCode(squadId: String, squadName: String): String {
            val cleanName = squadName.trim()
                .uppercase(Locale.ROOT)
                .replace(Regex("[^A-Z0-9]"), "")
                .take(6)
                .ifEmpty { "SQUAD" }
            val cleanSuffix = squadId.uppercase(Locale.ROOT)
                .replace(Regex("[^A-Z0-9]"), "")
                .takeLast(5)
                .padStart(5, 'X')
            return "RIVALS-${cleanName}-${cleanSuffix}".take(24)
        }

        fun normalizeSquadCode(rawInput: String): String? {
            val trimmed = rawInput.trim()
            if (trimmed.isBlank()) return null

            val tokenMatch = EMBEDDED_SQUAD_CODE_REGEX.find(trimmed)
            if (tokenMatch != null) {
                val candidate = tokenMatch.groupValues[1].uppercase(Locale.ROOT)
                if (STRICT_SQUAD_CODE_REGEX.matches(candidate)) {
                    return candidate
                }
            }

            val directClean = trimmed.uppercase(Locale.ROOT).replace(Regex("[^A-Z0-9_\\-]"), "")
            return if (STRICT_SQUAD_CODE_REGEX.matches(directClean)) directClean else null
        }

        /**
         * Returns the active UTC month key ("YYYY-MM") so all users worldwide share the exact same monthly reset boundary.
         */
        fun currentUtcSeasonMonthKey(nowUtcMillis: Long = System.currentTimeMillis()): String {
            val sdf = SimpleDateFormat("yyyy-MM", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            return sdf.format(Date(nowUtcMillis))
        }

        /**
         * Computes the exact global UTC epoch milliseconds for 00:00:00.000 on the 1st of next month.
         */
        fun nextMonthlyResetUtcMillis(nowUtcMillis: Long = System.currentTimeMillis()): Long {
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US).apply {
                timeInMillis = nowUtcMillis
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.MONTH, 1)
            }
            return cal.timeInMillis
        }

        fun nextUtcMonthResetEpochMs(nowUtcMillis: Long = System.currentTimeMillis()): Long =
            nextMonthlyResetUtcMillis(nowUtcMillis)

        /**
         * Computes the exact UTC epoch milliseconds when the "12 hours before monthly reset" notification should fire.
         */
        fun twelveHoursBeforeNextResetUtcMillis(nowUtcMillis: Long = System.currentTimeMillis()): Long {
            return nextMonthlyResetUtcMillis(nowUtcMillis) - EXACT_12_HOURS_MS
        }

        fun nextMonthlyWrap12HoursBeforeResetEpochMs(nowUtcMillis: Long = System.currentTimeMillis()): Long =
            twelveHoursBeforeNextResetUtcMillis(nowUtcMillis)

        /**
         * Formats the exact time remaining until the next monthly UTC reset.
         */
        fun formatMonthlyResetCountdown(nowUtcMillis: Long = System.currentTimeMillis()): String {
            val remainingMs = (nextMonthlyResetUtcMillis(nowUtcMillis) - nowUtcMillis).coerceAtLeast(0L)
            val days = remainingMs / (24L * 3600_000L)
            val hours = (remainingMs % (24L * 3600_000L)) / 3600_000L
            val minutes = (remainingMs % 3600_000L) / 60_000L
            return when {
                days > 0L -> "${days}d ${hours}h"
                hours > 0L -> "${hours}h ${minutes}m"
                else -> "${minutes.coerceAtLeast(1L)}m"
            }
        }

        fun formatRemainingResetCountdown(nowUtcMillis: Long = System.currentTimeMillis()): String =
            formatMonthlyResetCountdown(nowUtcMillis)

        /**
         * Formats the 4 Rivals push notification messages and the One-Click Taunt push notification.
         */
        fun formatThreatNotification(friendName: String, pointsAway: Long, rankPlacement: String): String {
            val safeFriend = friendName.trim().ifBlank { "Rival" }
            val safePts = pointsAway.coerceAtLeast(1L)
            val safeRank = rankPlacement.ifBlank { "1st" }
            return "Watch out! $safeFriend is only $safePts points away from stealing your $safeRank rank. Launch a game to defend your spot."
        }

        fun buildThreatNotificationText(friendName: String, pointsAway: Long, rankPlacement: String): String =
            formatThreatNotification(friendName, pointsAway, rankPlacement)

        fun formatDethroningNotification(friendName: String): String {
            val safeFriend = friendName.trim().ifBlank { "Rival" }
            return "You’ve been dethroned! $safeFriend just passed you. Tap here to reclaim your crown."
        }

        fun buildDethroningNotificationText(friendName: String): String =
            formatDethroningNotification(friendName)

        fun formatActivityNudgeNotification(friendName: String): String {
            val safeFriend = friendName.trim().ifBlank { "Rival" }
            return "$safeFriend just finished a 3 hour gaming session and is pulling ahead. Time to catch up?"
        }

        fun buildActivityNudgeNotificationText(friendName: String): String =
            formatActivityNudgeNotification(friendName)

        fun formatMonthlyWrapNotification(rankPlacement: String): String {
            val safeRank = rankPlacement.ifBlank { "1st" }
            return "You finished $safeRank on your friends list this month. Next month’s leaderboard resets in 12 hours!"
        }

        fun buildMonthlyWrapNotificationText(rankPlacement: String): String =
            formatMonthlyWrapNotification(rankPlacement)

        fun formatTauntPushNotification(friendName: String, presetTaunt: String): String {
            val safeFriend = friendName.trim().ifBlank { "Rival" }
            val safeTaunt = if (PRESET_TAUNTS.contains(presetTaunt)) presetTaunt else PRESET_TAUNTS.first()
            return "$safeFriend said $safeTaunt after passing you in the Squad leaderboard"
        }

        fun buildTauntReceivedNotificationText(friendName: String, preTaunt: String): String =
            formatTauntPushNotification(friendName, preTaunt)

        /**
         * Sorts up to 5 squad members by gamerscore descending and assigns ranks 1st..5th,
         * placing the Golden Crown exclusively on the 1st-place player.
         */
        fun buildRankedSquadLeaderboard(
            claims: List<UsernameClaim>,
            memberUids: List<String>,
            leaderUid: String
        ): List<RivalsSquadMemberRank> {
            val boundedUids = memberUids.distinct().take(MAX_SQUAD_MEMBERS)
            val claimsByUid = claims.associateBy { it.userId }
            val unsorted = boundedUids.map { uid ->
                val claim = claimsByUid[uid]
                val handle = claim?.username?.takeIf { it.isNotBlank() } ?: "player_${uid.takeLast(4)}"
                val display = claim?.displayName?.takeIf { it.isNotBlank() } ?: handle
                RivalsSquadMemberRank(
                    uid = uid,
                    username = handle,
                    displayName = display,
                    photoUrl = claim?.photoUrl.orEmpty(),
                    countryCode = claim?.countryCode?.ifBlank { "US" } ?: "US",
                    gamerscore = (claim?.gamerscore ?: 0L).coerceAtLeast(0L),
                    totalPlayTimeMinutes = (claim?.totalPlayTimeMinutes ?: 0L).coerceAtLeast(0L),
                    isLeader = uid == leaderUid,
                    rankNumber = 1,
                    rankPlacementLabel = "1st",
                    hasFirstPlaceCrown = false
                )
            }
            return unsorted
                .sortedWith(
                    compareByDescending<RivalsSquadMemberRank> { it.gamerscore }
                        .thenByDescending { it.totalPlayTimeMinutes }
                        .thenBy { if (it.isLeader) 0 else 1 }
                        .thenBy { it.username.lowercase(Locale.ROOT) }
                )
                .take(MAX_SQUAD_MEMBERS)
                .mapIndexed { index, member ->
                    val rankNum = index + 1
                    member.copy(
                        rankNumber = rankNum,
                        rankPlacementLabel = rankLabelForPosition(rankNum),
                        hasFirstPlaceCrown = (rankNum == 1)
                    )
                }
        }
    }

    suspend fun getActiveSquadForUser(userId: String = requireUserId()): Result<RivalsSquad?> {
        val path = "rivals_squads"
        return try {
            val snap = db.collection(path)
                .whereArrayContains("memberUids", userId)
                .limit(1)
                .get()
                .await()
            val doc = snap.documents.firstOrNull()
            if (doc != null && doc.exists()) {
                Result.success(
                    doc.toObject(
                        RivalsSquad::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                )
            } else {
                Result.success(null)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    suspend fun getMyActiveSquad(userId: String = requireUserId()): Result<RivalsSquad?> =
        getActiveSquadForUser(userId)

    fun observeMyActiveSquad(userId: String): Flow<RivalsSquad?> =
        observeActiveSquadForUser(userId)

    fun observeActiveSquadForUser(userId: String): Flow<RivalsSquad?> {
        val path = "rivals_squads"
        return db.collection(path)
            .whereArrayContains("memberUids", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.firstOrNull()?.toObject(
                    RivalsSquad::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                }
                throw error
            }
    }

    fun observeSquadMembersLeaderboard(
        memberUids: List<String>,
        leaderUid: String
    ): Flow<List<RivalsSquadMemberRank>> {
        val boundedUids = memberUids.distinct().filter { it.isNotBlank() }.take(MAX_SQUAD_MEMBERS)
        if (boundedUids.isEmpty()) return flowOf(emptyList())
        val path = "usernames"
        return db.collection(path)
            .whereIn("userId", boundedUids)
            .snapshots()
            .map { snapshot ->
                val claims = snapshot.toObjects(
                    UsernameClaim::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                buildRankedSquadLeaderboard(claims, boundedUids, leaderUid)
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser != null) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                }
                throw error
            }
    }

    suspend fun createSquad(
        squadName: String,
        bio: String,
        leaderUsername: String,
        leaderDisplayName: String = leaderUsername,
        nowUtcMillis: Long = System.currentTimeMillis()
    ): Result<RivalsSquad> = createSquadInternal(
        rawName = squadName,
        rawBio = bio,
        leaderUsername = leaderUsername.ifBlank { leaderDisplayName },
        nowUtcMillis = nowUtcMillis
    )

    private suspend fun createSquadInternal(
        rawName: String,
        rawBio: String,
        leaderUsername: String,
        nowUtcMillis: Long = System.currentTimeMillis()
    ): Result<RivalsSquad> {
        val uid = requireUserId()
        val trimmedName = rawName.trim()
        val trimmedBio = rawBio.trim()

        if (trimmedName.isEmpty() || trimmedName.length > MAX_SQUAD_NAME_LENGTH) {
            return Result.failure(
                IllegalArgumentException(
                    AppStrings.trText(
                        en = "Squad name must be between 1 and 40 characters.",
                        es = "El nombre del escuadrón debe tener entre 1 y 40 caracteres."
                    )
                )
            )
        }
        if (trimmedBio.length > MAX_SQUAD_BIO_LENGTH) {
            return Result.failure(
                IllegalArgumentException(
                    AppStrings.trText(
                        en = "Squad bio cannot exceed 150 characters.",
                        es = "La biografía del escuadrón no puede superar los 150 caracteres."
                    )
                )
            )
        }

        // Enforce single-squad exclusivity: users already in a squad cannot create a new squad
        val existingSquad = getActiveSquadForUser(uid).getOrElse { return Result.failure(it) }
        if (existingSquad != null) {
            return Result.failure(
                IllegalStateException(
                    AppStrings.trText(
                        en = "You are already in a Squad (${existingSquad.name}). Leave your current squad before creating a new one.",
                        es = "Ya estás en un Escuadrón (${existingSquad.name}). Sal de tu escuadrón actual antes de crear uno nuevo."
                    )
                )
            )
        }

        val squadId = "squad_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val squadCode = generateSquadCode(squadId, trimmedName)
        val safeLeaderUser = leaderUsername.trim().removePrefix("@").ifBlank { "SquadLeader" }.take(40)
        val seasonKey = currentUtcSeasonMonthKey(nowUtcMillis)

        val squadRef = db.collection("rivals_squads").document(squadId)
        val codeRef = db.collection("rivals_squad_codes").document(squadCode)

        val squadPayload = mapOf(
            "squadId" to squadId,
            "name" to trimmedName,
            "bio" to trimmedBio,
            "squadCode" to squadCode,
            "leaderUid" to uid,
            "leaderUsername" to safeLeaderUser,
            "memberUids" to listOf(uid),
            "seasonMonthKey" to seasonKey,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        val codePayload = mapOf(
            "squadCode" to squadCode,
            "squadId" to squadId,
            "leaderUid" to uid,
            "squadName" to trimmedName,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        return try {
            squadRef.set(squadPayload).await()
            codeRef.set(codePayload).await()
            Result.success(
                RivalsSquad(
                    id = squadId,
                    squadId = squadId,
                    name = trimmedName,
                    bio = trimmedBio,
                    squadCode = squadCode,
                    leaderUid = uid,
                    leaderUsername = safeLeaderUser,
                    memberUids = listOf(uid),
                    seasonMonthKey = seasonKey
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, squadRef.path)
            Result.failure(e)
        }
    }

    /**
     * Allows ONLY the Squad Leader/Creator to change the squad name and 150-character bio in real time.
     */
    suspend fun updateSquadDetails(
        squadId: String,
        newName: String,
        newBio: String
    ): Result<RivalsSquad> = updateSquadNameAndBio(squadId, newName, newBio)

    suspend fun updateSquadNameAndBio(
        squadId: String,
        newNameRaw: String,
        newBioRaw: String
    ): Result<RivalsSquad> {
        val uid = requireUserId()
        val trimmedName = newNameRaw.trim()
        val trimmedBio = newBioRaw.trim()

        if (trimmedName.isEmpty() || trimmedName.length > MAX_SQUAD_NAME_LENGTH) {
            return Result.failure(
                IllegalArgumentException(
                    AppStrings.trText(
                        en = "Squad name must be between 1 and 40 characters.",
                        es = "El nombre del escuadrón debe tener entre 1 y 40 caracteres."
                    )
                )
            )
        }
        if (trimmedBio.length > MAX_SQUAD_BIO_LENGTH) {
            return Result.failure(
                IllegalArgumentException(
                    AppStrings.trText(
                        en = "Squad bio cannot exceed 150 characters.",
                        es = "La biografía del escuadrón no puede superar los 150 caracteres."
                    )
                )
            )
        }

        val squadRef = db.collection("rivals_squads").document(squadId)
        return try {
            val snap = squadRef.get().await()
            if (!snap.exists()) {
                return Result.failure(
                    IllegalStateException(
                        AppStrings.trText(
                            en = "Squad no longer exists.",
                            es = "El escuadrón ya no existe."
                        )
                    )
                )
            }
            val current = snap.toObject(
                RivalsSquad::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return Result.failure(IllegalStateException("Invalid squad data."))

            if (current.leaderUid != uid) {
                return Result.failure(
                    IllegalAccessException(
                        AppStrings.trText(
                            en = "Only the Squad Leader can edit the squad name or bio.",
                            es = "Solo el Líder del Escuadrón puede editar el nombre o la biografía."
                        )
                    )
                )
            }

            squadRef.update(
                mapOf(
                    "name" to trimmedName,
                    "bio" to trimmedBio,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            if (current.squadCode.isNotBlank()) {
                try {
                    db.collection("rivals_squad_codes").document(current.squadCode).update(
                        mapOf(
                            "squadName" to trimmedName,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } catch (_: Exception) {}
            }

            Result.success(
                current.copy(
                    id = squadId,
                    name = trimmedName,
                    bio = trimmedBio
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, squadRef.path)
            Result.failure(e)
        }
    }

    /**
     * Deletes the squad and its squad code from the Firebase Firestore database (Squad Leader only).
     */
    suspend fun deleteSquad(squadId: String): Result<Unit> {
        val uid = requireUserId()
        val squadRef = db.collection("rivals_squads").document(squadId)
        return try {
            val snap = squadRef.get().await()
            if (!snap.exists()) {
                return Result.success(Unit)
            }
            val current = snap.toObject(
                RivalsSquad::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return Result.success(Unit)

            if (current.leaderUid != uid) {
                return Result.failure(
                    IllegalAccessException(
                        AppStrings.trText(
                            en = "Only the Squad Leader can delete the squad.",
                            es = "Solo el Líder del Escuadrón puede eliminar el escuadrón."
                        )
                    )
                )
            }

            // Delete Squad Code lookup document from Firebase database
            if (current.squadCode.isNotBlank()) {
                try {
                    db.collection("rivals_squad_codes").document(current.squadCode).delete().await()
                } catch (_: Exception) {}
            }

            // Delete pending invites sent by this leader for this squad
            try {
                val pendingInvites = db.collection("rivals_squad_invites")
                    .whereEqualTo("inviterUid", uid)
                    .get()
                    .await()
                for (doc in pendingInvites.documents) {
                    if (doc.getString("squadId") == squadId) {
                        doc.reference.delete().await()
                    }
                }
            } catch (_: Exception) {}

            // Delete the squad document itself from Firebase database
            squadRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, squadRef.path)
            Result.failure(e)
        }
    }

    /**
     * Joins a squad automatically using a Squad Code (no approval required, unlike username search).
     * Enforces that the user is not already in a squad and that the squad has fewer than 5 members.
     */
    suspend fun joinSquadByCode(rawCode: String): Result<RivalsSquad> {
        val uid = requireUserId()
        val normalizedCode = normalizeSquadCode(rawCode)
            ?: return Result.failure(
                IllegalArgumentException(
                    AppStrings.trText(
                        en = "Invalid Squad Code format. Enter a code like RIVALS-SQUAD-12345.",
                        es = "Formato de Código de Escuadrón inválido. Ingresa un código como RIVALS-SQUAD-12345."
                    )
                )
            )

        // Ensure user is not already in any squad
        val existingSquad = getActiveSquadForUser(uid).getOrElse { return Result.failure(it) }
        if (existingSquad != null) {
            return Result.failure(
                IllegalStateException(
                    AppStrings.trText(
                        en = "You are already in a Squad (${existingSquad.name}). Leave your current squad first.",
                        es = "Ya estás en un Escuadrón (${existingSquad.name}). Sal de tu escuadrón actual primero."
                    )
                )
            )
        }

        val codeRef = db.collection("rivals_squad_codes").document(normalizedCode)
        return try {
            val codeSnap = codeRef.get().await()
            if (!codeSnap.exists()) {
                return Result.failure(
                    IllegalArgumentException(
                        AppStrings.trText(
                            en = "Squad Code $normalizedCode was not found.",
                            es = "No se encontró el Código de Escuadrón $normalizedCode."
                        )
                    )
                )
            }
            val targetSquadId = codeSnap.getString("squadId").orEmpty()
            if (targetSquadId.isBlank()) {
                return Result.failure(IllegalStateException("Squad Code does not point to a valid squad."))
            }

            val squadRef = db.collection("rivals_squads").document(targetSquadId)
            val squadSnap = squadRef.get().await()
            if (!squadSnap.exists()) {
                return Result.failure(
                    IllegalStateException(
                        AppStrings.trText(
                            en = "This Squad has been deleted by its leader.",
                            es = "Este Escuadrón fue eliminado por su líder."
                        )
                    )
                )
            }
            val squad = squadSnap.toObject(
                RivalsSquad::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return Result.failure(IllegalStateException("Invalid squad document."))

            if (squad.memberUids.contains(uid)) {
                return Result.success(squad)
            }
            if (squad.memberUids.size >= MAX_SQUAD_MEMBERS) {
                return Result.failure(
                    IllegalStateException(
                        AppStrings.trText(
                            en = "This Squad is full (maximum 5 players).",
                            es = "Este Escuadrón está lleno (máximo 5 jugadores)."
                        )
                    )
                )
            }

            val updatedMembers = (squad.memberUids + uid).distinct().take(MAX_SQUAD_MEMBERS)
            squadRef.update(
                mapOf(
                    "memberUids" to updatedMembers,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(
                squad.copy(
                    id = targetSquadId,
                    memberUids = updatedMembers
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, codeRef.path)
            Result.failure(e)
        }
    }

    /**
     * Leaves the current squad. If the user is a member, removes their UID from memberUids.
     * If the user is the Squad Leader, deletes the squad from Firestore.
     */
    suspend fun leaveSquad(squadId: String): Result<Unit> {
        val uid = requireUserId()
        val squadRef = db.collection("rivals_squads").document(squadId)
        return try {
            val snap = squadRef.get().await()
            if (!snap.exists()) return Result.success(Unit)
            val squad = snap.toObject(
                RivalsSquad::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return Result.success(Unit)

            if (squad.leaderUid == uid) {
                return deleteSquad(squadId)
            }

            val updatedMembers = squad.memberUids.filter { it != uid }
            squadRef.update(
                mapOf(
                    "memberUids" to updatedMembers,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, squadRef.path)
            Result.failure(e)
        }
    }

    /**
     * Squad Leader invites a player by searching their username in the Firestore `/usernames` collection.
     * Unlike Squad Code, username-searched players MUST accept the request before joining the squad.
     */
    suspend fun invitePlayerByUsername(
        squadId: String,
        targetUsernameOrQuery: String,
        inviterUsername: String,
        @Suppress("UNUSED_PARAMETER") inviterDisplayName: String = inviterUsername
    ): Result<RivalsSquadInvite> = invitePlayerByUsernameInternal(
        squadId = squadId,
        targetUsernameRaw = targetUsernameOrQuery,
        inviterUsername = inviterUsername
    )

    private suspend fun invitePlayerByUsernameInternal(
        squadId: String,
        targetUsernameRaw: String,
        inviterUsername: String
    ): Result<RivalsSquadInvite> {
        val uid = requireUserId()
        val cleanTargetLower = targetUsernameRaw.trim().removePrefix("@").lowercase(Locale.ROOT)
        if (cleanTargetLower.length < 3) {
            return Result.failure(
                IllegalArgumentException(
                    AppStrings.trText(
                        en = "Enter a valid player username (at least 3 characters).",
                        es = "Ingresa un nombre de usuario válido (al menos 3 caracteres)."
                    )
                )
            )
        }

        val squadRef = db.collection("rivals_squads").document(squadId)
        return try {
            val squadSnap = squadRef.get().await()
            if (!squadSnap.exists()) {
                return Result.failure(IllegalStateException("Squad not found."))
            }
            val squad = squadSnap.toObject(
                RivalsSquad::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return Result.failure(IllegalStateException("Invalid squad."))

            if (squad.leaderUid != uid) {
                return Result.failure(
                    IllegalAccessException(
                        AppStrings.trText(
                            en = "Only the Squad Leader can invite players by username.",
                            es = "Solo el Líder del Escuadrón puede invitar jugadores por nombre de usuario."
                        )
                    )
                )
            }
            if (squad.memberUids.size >= MAX_SQUAD_MEMBERS) {
                return Result.failure(
                    IllegalStateException(
                        AppStrings.trText(
                            en = "Your Squad already has the maximum of 5 players.",
                            es = "Tu Escuadrón ya tiene el máximo de 5 jugadores."
                        )
                    )
                )
            }

            // Look up player in `/usernames/{cleanTargetLower}`
            val usernameRef = db.collection("usernames").document(cleanTargetLower)
            val userSnap = usernameRef.get().await()
            if (!userSnap.exists()) {
                return Result.failure(
                    IllegalArgumentException(
                        AppStrings.trText(
                            en = "Player @$cleanTargetLower was not found in the database.",
                            es = "No se encontró al jugador @$cleanTargetLower en la base de datos."
                        )
                    )
                )
            }
            val claim = userSnap.toObject(
                UsernameClaim::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return Result.failure(IllegalArgumentException("Invalid player profile."))

            if (claim.userId == uid || squad.memberUids.contains(claim.userId)) {
                return Result.failure(
                    IllegalStateException(
                        AppStrings.trText(
                            en = "@${claim.username} is already in your Squad.",
                            es = "@${claim.username} ya está en tu Escuadrón."
                        )
                    )
                )
            }

            // Verify target player is not already in another squad
            val targetExistingSquad = getActiveSquadForUser(claim.userId).getOrNull()
            if (targetExistingSquad != null) {
                return Result.failure(
                    IllegalStateException(
                        AppStrings.trText(
                            en = "@${claim.username} is already in another Squad and cannot join unless they leave their current squad.",
                            es = "@${claim.username} ya está en otro Escuadrón y no puede unirse a menos que salga de su escuadrón actual."
                        )
                    )
                )
            }

            val inviteId = "${squadId}_${claim.userId}".replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120)
            val inviteRef = db.collection("rivals_squad_invites").document(inviteId)
            val safeInviterName = inviterUsername.trim().removePrefix("@").ifBlank { squad.leaderUsername.ifBlank { "Leader" } }.take(40)
            val safeInviteeName = claim.username.trim().removePrefix("@").ifBlank { cleanTargetLower }.take(40)

            val payload = mapOf(
                "inviteId" to inviteId,
                "squadId" to squadId,
                "squadName" to squad.name.take(40),
                "inviterUid" to uid,
                "inviterUsername" to safeInviterName,
                "inviteeUid" to claim.userId,
                "inviteeUsername" to safeInviteeName,
                "status" to RivalsSquadInvite.STATUS_PENDING,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            inviteRef.set(payload).await()

            Result.success(
                RivalsSquadInvite(
                    id = inviteId,
                    inviteId = inviteId,
                    squadId = squadId,
                    squadName = squad.name,
                    inviterUid = uid,
                    inviterUsername = safeInviterName,
                    inviteeUid = claim.userId,
                    inviteeUsername = safeInviteeName,
                    status = RivalsSquadInvite.STATUS_PENDING
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, squadRef.path)
            Result.failure(e)
        }
    }

    fun observeIncomingInvites(inviteeUid: String): Flow<List<RivalsSquadInvite>> {
        val path = "rivals_squad_invites"
        return db.collection(path)
            .whereEqualTo("inviteeUid", inviteeUid)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    RivalsSquadInvite::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).filter { it.status == RivalsSquadInvite.STATUS_PENDING }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == inviteeUid) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                }
                throw error
            }
    }

    fun observeIncomingPendingInvites(inviteeUid: String): Flow<List<RivalsSquadInvite>> =
        observeIncomingInvites(inviteeUid)

    suspend fun acceptSquadInvite(inviteId: String): Result<RivalsSquad> {
        val res = respondToSquadInvite(inviteId, accept = true)
        return res.mapCatching { squad ->
            squad ?: throw IllegalStateException("Squad not found after accepting invite.")
        }
    }

    suspend fun declineSquadInvite(inviteId: String): Result<Unit> {
        return respondToSquadInvite(inviteId, accept = false).map { Unit }
    }

    /**
     * Accepts or declines a Squad invitation sent via username search.
     */
    suspend fun respondToSquadInvite(inviteId: String, accept: Boolean): Result<RivalsSquad?> {
        val uid = requireUserId()
        val inviteRef = db.collection("rivals_squad_invites").document(inviteId)
        return try {
            val inviteSnap = inviteRef.get().await()
            if (!inviteSnap.exists()) {
                return Result.failure(IllegalStateException("Invitation no longer exists."))
            }
            val invite = inviteSnap.toObject(
                RivalsSquadInvite::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return Result.failure(IllegalStateException("Invalid invitation."))

            if (invite.inviteeUid != uid) {
                return Result.failure(IllegalAccessException("This invitation belongs to another player."))
            }
            if (invite.status != RivalsSquadInvite.STATUS_PENDING) {
                return Result.failure(IllegalStateException("This invitation was already resolved."))
            }

            if (!accept) {
                inviteRef.update(
                    mapOf(
                        "status" to RivalsSquadInvite.STATUS_DECLINED,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                return Result.success(null)
            }

            // Ensure invitee is not already in a squad
            val existingSquad = getActiveSquadForUser(uid).getOrElse { return Result.failure(it) }
            if (existingSquad != null) {
                return Result.failure(
                    IllegalStateException(
                        AppStrings.trText(
                            en = "You are already in a Squad (${existingSquad.name}). Leave your current squad before accepting another invite.",
                            es = "Ya estás en un Escuadrón (${existingSquad.name}). Sal de tu escuadrón actual antes de aceptar otra invitación."
                        )
                    )
                )
            }

            val squadRef = db.collection("rivals_squads").document(invite.squadId)
            val squadSnap = squadRef.get().await()
            if (!squadSnap.exists()) {
                inviteRef.update(
                    mapOf(
                        "status" to RivalsSquadInvite.STATUS_DECLINED,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                return Result.failure(
                    IllegalStateException(
                        AppStrings.trText(
                            en = "That Squad was deleted by its leader.",
                            es = "Ese Escuadrón fue eliminado por su líder."
                        )
                    )
                )
            }
            val squad = squadSnap.toObject(
                RivalsSquad::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return Result.failure(IllegalStateException("Invalid squad."))

            if (squad.memberUids.size >= MAX_SQUAD_MEMBERS && !squad.memberUids.contains(uid)) {
                return Result.failure(
                    IllegalStateException(
                        AppStrings.trText(
                            en = "This Squad is full (maximum 5 players).",
                            es = "Este Escuadrón está lleno (máximo 5 jugadores)."
                        )
                    )
                )
            }

            val updatedMembers = (squad.memberUids + uid).distinct().take(MAX_SQUAD_MEMBERS)
            squadRef.update(
                mapOf(
                    "memberUids" to updatedMembers,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            inviteRef.update(
                mapOf(
                    "status" to RivalsSquadInvite.STATUS_ACCEPTED,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(
                squad.copy(
                    id = squad.squadId,
                    memberUids = updatedMembers
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, inviteRef.path)
            Result.failure(e)
        }
    }

    /**
     * Sends a one-click pre-set taunt after passing a squad member on the leaderboard.
     * Strictly rejects any text outside the 7 default pre-set taunts.
     */
    suspend fun sendPresetTaunt(
        squadId: String,
        senderUsername: String,
        senderDisplayName: String = senderUsername,
        recipientUid: String,
        recipientUsername: String,
        tauntText: String,
        passEventKey: String
    ): Result<RivalsTaunt> = sendOneClickPresetTaunt(
        squadId = squadId,
        senderName = senderDisplayName.ifBlank { senderUsername },
        receiverUid = recipientUid,
        receiverName = recipientUsername,
        presetTaunt = tauntText,
        passEventKey = passEventKey
    )

    suspend fun sendOneClickPresetTaunt(
        squadId: String,
        senderName: String,
        receiverUid: String,
        receiverName: String,
        presetTaunt: String,
        passEventKey: String
    ): Result<RivalsTaunt> {
        val uid = requireUserId()
        if (!PRESET_TAUNTS.contains(presetTaunt)) {
            return Result.failure(
                IllegalArgumentException(
                    AppStrings.trText(
                        en = "Only official pre-set taunts are allowed.",
                        es = "Solo se permiten las burlas predeterminadas oficiales."
                    )
                )
            )
        }
        if (receiverUid.isBlank() || receiverUid == uid) {
            return Result.failure(IllegalArgumentException("Invalid taunt recipient."))
        }

        val safeEventKey = passEventKey.trim().replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(90)
            .ifBlank { "pass_${System.currentTimeMillis()}" }
        val tauntId = "taunt_${uid.takeLast(6)}_${receiverUid.takeLast(6)}_${safeEventKey}".take(120)
        val tauntRef = db.collection("rivals_taunts").document(tauntId)

        val safeSenderName = senderName.trim().ifBlank { "Squadmate" }.take(60)
        val safeReceiverName = receiverName.trim().ifBlank { "Rival" }.take(60)

        val payload = mapOf(
            "tauntId" to tauntId,
            "squadId" to squadId.trim().take(120),
            "senderUid" to uid,
            "senderName" to safeSenderName,
            "receiverUid" to receiverUid,
            "receiverName" to safeReceiverName,
            "tauntText" to presetTaunt,
            "passEventKey" to safeEventKey,
            "createdAt" to FieldValue.serverTimestamp()
        )

        return try {
            tauntRef.set(payload).await()
            Result.success(
                RivalsTaunt(
                    id = tauntId,
                    tauntId = tauntId,
                    squadId = squadId,
                    senderUid = uid,
                    senderName = safeSenderName,
                    receiverUid = receiverUid,
                    receiverName = safeReceiverName,
                    tauntText = presetTaunt,
                    passEventKey = safeEventKey
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, tauntRef.path)
            Result.failure(e)
        }
    }

    fun observeIncomingTaunts(receiverUid: String): Flow<List<RivalsTaunt>> {
        val path = "rivals_taunts"
        return db.collection(path)
            .whereEqualTo("receiverUid", receiverUid)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    RivalsTaunt::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == receiverUid) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                }
                throw error
            }
    }
}
