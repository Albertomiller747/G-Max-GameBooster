package com.example.data.repository

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun handleFirestoreError(exception: Exception, operationType: OperationType, path: String?): String {
    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", exception.message ?: exception.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@IgnoreExtraProperties
data class UserCloudProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Gamer",
    val username: String = "",
    val countryCode: String = "US",
    val countryName: String = "United States",
    val email: String = "",
    val photoUrl: String = "",
    val dateOfBirth: String = "Not Set",
    val bio: String = "",
    val selectedTags: List<String> = emptyList(),
    @get:PropertyName("isVerifiedDeveloper")
    val isVerifiedDeveloper: Boolean = false,
    val mostPlayedGameName: String = "No games played yet",
    val mostPlayedGamePkg: String = "",
    val mostPlayedMinutes: Long = 0L,
    val totalPlayTimeMinutes: Long = 0L,
    val totalSessionsCount: Long = 0L,
    val themeMode: String = "AUTO_SYSTEM",
    val customBgColor: Long = 0xFF121417L,
    val reducedAnimations: Boolean = false,
    val autoBigScreenLandscape: Boolean = true,
    val showControllerLegendBar: Boolean = true,
    val suppressActivationWarning: Boolean = false,
    val selectedBoostMode: String = "GAMING",
    val appliedOptionIds: List<String> = listOf("pop_3_ram", "net_wifi_5g"),
    val hudEnabled: Boolean = false,
    val hudFpsMode: String = "BALANCED",
    val hudTargetCap: String = "AUTO",
    val hudPosition: String = "TOP_START",
    val hudScalePct: Long = 100L,
    val hudOpacityPct: Long = 85L,
    val hudShowFps: Boolean = true,
    val hudShowTemp: Boolean = true,
    val hudShowBattery: Boolean = true,
    val hudShowTime: Boolean = false,
    val hudShowRamPct: Boolean = true,
    val hudShowClock: Boolean = true,
    val crosshairEnabled: Boolean = false,
    val crosshairType: String = "CROSS",
    val crosshairColorArgb: Long = 0xFF00E676L,
    val crosshairSizeDp: Long = 24L,
    val lightbarColorHex: String = "#38BDF8",
    val recResolutionLabel: String = "1080p",
    val recFps: Long = 60L,
    val recBitrateMbps: Long = 12L,
    val recMicEnabled: Boolean = true,
    val recShowTimerHud: Boolean = true,
    val recOverlayEnabled: Boolean = true,
    val recBubbleSizeDp: Long = 42L,
    val targetGamePkg: String = "",
    val targetGameName: String = "",
    val libraryPackages: List<String> = emptyList(),
    val bannerUrl: String = "",
    val gamerscore: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class UsernameClaim(
    @DocumentId val id: String = "",
    val userId: String = "",
    val username: String = "",
    val displayName: String = "",
    val bio: String = "",
    val photoUrl: String = "",
    val bannerUrl: String = "",
    val countryCode: String = "US",
    val countryName: String = "United States",
    val gamerscore: Long = 0L,
    val mostPlayedGameName: String = "No games played yet",
    val mostPlayedMinutes: Long = 0L,
    val totalPlayTimeMinutes: Long = 0L,
    val selectedTags: List<String> = emptyList(),
    @get:PropertyName("isVerifiedDeveloper")
    val isVerifiedDeveloper: Boolean = false,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

class UserAccountRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth: FirebaseAuth
        get() = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeUserProfile(userId: String): Flow<UserCloudProfile?> {
        val path = "users/$userId"
        return db.collection("users").document(userId)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(
                        UserCloudProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                } else {
                    null
                }
            }
            .catch { error ->
                if (error is Exception) {
                    handleFirestoreError(error, OperationType.GET, path)
                }
                throw error
            }
    }

    suspend fun getUserProfile(userId: String): Result<UserCloudProfile?> {
        val docRef = db.collection("users").document(userId)
        return try {
            val snap = docRef.get().await()
            if (snap.exists()) {
                val parsed = snap.toObject(
                    UserCloudProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                Result.success(parsed)
            } else {
                Result.success(null)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun saveOrUpdateUserProfile(profile: UserCloudProfile): Result<UserCloudProfile> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        val sanitized = sanitizeProfile(profile, uid)
        return try {
            val existingSnap = docRef.get().await()
            val existingCreatedAt = if (existingSnap.exists()) {
                existingSnap.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else {
                null
            }

            val savedProfile = if (existingSnap.exists() && existingCreatedAt != null) {
                val payload = sanitized.copy(
                    createdAt = existingCreatedAt,
                    updatedAt = null
                )
                docRef.set(payload).await()
                payload.copy(id = uid)
            } else {
                val payload = sanitized.copy(
                    createdAt = null,
                    updatedAt = null
                )
                docRef.set(payload).await()
                payload.copy(id = uid)
            }

            // Best-effort sync to public @username directory so players can look up this profile by username
            try {
                syncUsernameClaim(savedProfile)
            } catch (_: Exception) {}

            Result.success(savedProfile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun syncUsernameClaim(profile: UserCloudProfile): Result<Unit> {
        val uid = requireUserId()
        val isDev = com.example.util.PremadeTagsCatalog.isOfficialDeveloperEmail(auth.currentUser?.email)
        val safeDisplayName = com.example.util.PremadeTagsCatalog.sanitizeDisplayName(
            profile.displayName,
            isDev
        ).take(100)
        var handle = com.example.util.PremadeTagsCatalog.sanitizeUsername(
            profile.username,
            safeDisplayName,
            isDev
        ).take(20)
        if (handle.length < 3 || !handle.matches(Regex("^[a-zA-Z0-9_]+$"))) {
            val uidSuffix = uid.replace(Regex("[^a-zA-Z0-9]"), "").takeLast(6).lowercase(Locale.ROOT).ifEmpty { "user" }
            handle = "player_$uidSuffix".take(20)
        }
        var lower = handle.lowercase(Locale.ROOT)
        var claimRef = db.collection("usernames").document(lower)
        return try {
            val existingSnap = claimRef.get().await()
            if (existingSnap.exists()) {
                val ownerUid = existingSnap.getString("userId")
                if (ownerUid != null && ownerUid != uid) {
                    // Shared display name caused default handle collision — generate unique handle for this account
                    val suffix = uid.replace(Regex("[^a-zA-Z0-9]"), "").takeLast(4).lowercase(Locale.ROOT).ifEmpty { "01" }
                    handle = "${handle.take(15)}_$suffix"
                    lower = handle.lowercase(Locale.ROOT)
                    claimRef = db.collection("usernames").document(lower)
                }
            }
            val existingGamerscore = existingSnap.getLong("gamerscore") ?: 0L
            val existingPhotoUrl = existingSnap.getString("photoUrl").orEmpty()
            val resolvedGamerscore = maxOf(profile.gamerscore.coerceAtLeast(0L), existingGamerscore)
            val resolvedPhotoUrl = profile.photoUrl.trim().take(1000).ifBlank { existingPhotoUrl.take(1000) }
            val claimPayload = UsernameClaim(
                id = lower,
                userId = uid,
                username = handle,
                displayName = safeDisplayName,
                bio = profile.bio.trim().take(300),
                photoUrl = resolvedPhotoUrl,
                bannerUrl = profile.bannerUrl.trim().take(1000),
                countryCode = profile.countryCode.trim().uppercase(Locale.ROOT).ifEmpty { "US" }.take(10),
                countryName = profile.countryName.trim().ifEmpty { "United States" }.take(100),
                gamerscore = resolvedGamerscore,
                mostPlayedGameName = profile.mostPlayedGameName.trim().ifEmpty { "No games played yet" }.take(120),
                mostPlayedMinutes = profile.mostPlayedMinutes.coerceAtLeast(0L),
                totalPlayTimeMinutes = profile.totalPlayTimeMinutes.coerceAtLeast(0L),
                selectedTags = com.example.util.PremadeTagsCatalog.sanitizeSelectedTags(profile.selectedTags),
                isVerifiedDeveloper = isDev,
                updatedAt = null
            )
            claimRef.set(claimPayload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, claimRef.path)
            Result.failure(e)
        }
    }

    /**
     * Looks up a player's public profile card by their @username or display name,
     * checking exact `/usernames/{usernameLower}` first and falling back to closest similarity match.
     */
    suspend fun searchPlayerByUsername(rawQuery: String): Result<UsernameClaim?> {
        val trimmed = rawQuery.trim().removePrefix("@")
        if (trimmed.length < 2) {
            return Result.failure(IllegalArgumentException("Enter at least 2 characters to search."))
        }
        val cleanLower = trimmed.lowercase(Locale.ROOT)
        // Fast path: exact @username lookup when query is a valid handle
        if (cleanLower.length in 3..20 && cleanLower.matches(Regex("^[a-z0-9_]+$"))) {
            val claimRef = db.collection("usernames").document(cleanLower)
            try {
                val snap = claimRef.get().await()
                if (snap.exists()) {
                    val parsed = snap.toObject(
                        UsernameClaim::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                    if (parsed != null) {
                        return Result.success(parsed)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Fall through to similarity search
            }
        }
        return searchPlayersBySimilarity(trimmed, maxResults = 1).map { it.firstOrNull() }
    }

    /**
     * Searches and ranks the closest matching player accounts across both unique `@username` (nickname)
     * and shared `displayName` (accounts can have the same display name, while @username is unique).
     */
    suspend fun searchPlayersBySimilarity(rawQuery: String, maxResults: Int = 8): Result<List<UsernameClaim>> {
        val trimmed = rawQuery.trim().removePrefix("@")
        if (trimmed.length < 2) {
            return Result.success(emptyList())
        }
        val cleanLower = trimmed.lowercase(Locale.ROOT)
        val candidatesById = LinkedHashMap<String, UsernameClaim>()

        return try {
            // 1. Check exact handle document first if valid
            if (cleanLower.length in 3..20 && cleanLower.matches(Regex("^[a-z0-9_]+$"))) {
                try {
                    val exactSnap = db.collection("usernames").document(cleanLower).get().await()
                    if (exactSnap.exists()) {
                        val exactClaim = exactSnap.toObject(
                            UsernameClaim::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                        if (exactClaim != null) {
                            val key = exactClaim.username.lowercase(Locale.ROOT).ifBlank { exactClaim.userId }
                            candidatesById[key] = exactClaim
                        }
                    }
                } catch (_: Exception) {}
            }

            // 2. Scan public `/usernames` directory (up to 120 recent/active claims)
            try {
                val claimsSnap = db.collection("usernames").limit(120).get().await()
                for (doc in claimsSnap.documents) {
                    val claim = doc.toObject(
                        UsernameClaim::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ) ?: continue
                    val key = claim.username.lowercase(Locale.ROOT).ifBlank { doc.id }
                    if (!candidatesById.containsKey(key)) {
                        candidatesById[key] = claim.copy(id = doc.id)
                    }
                }
            } catch (_: Exception) {}

            // 3. Also scan `/users` profiles so accounts searched by shared display name are always found
            try {
                val usersSnap = db.collection("users").limit(100).get().await()
                for (doc in usersSnap.documents) {
                    val profile = doc.toObject(
                        UserCloudProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ) ?: continue
                    val handle = profile.username.trim().removePrefix("@")
                        .ifBlank { profile.displayName.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9_]"), "_").take(20) }
                    val key = handle.lowercase(Locale.ROOT).ifBlank { doc.id }
                    if (!candidatesById.containsKey(key)) {
                        candidatesById[key] = UsernameClaim(
                            id = key,
                            userId = profile.userId.ifBlank { doc.id },
                            username = handle,
                            displayName = profile.displayName.ifBlank { handle },
                            bio = profile.bio,
                            photoUrl = profile.photoUrl,
                            bannerUrl = profile.bannerUrl,
                            countryCode = profile.countryCode.ifBlank { "US" },
                            countryName = profile.countryName.ifBlank { "United States" },
                            gamerscore = profile.gamerscore,
                            mostPlayedGameName = profile.mostPlayedGameName,
                            mostPlayedMinutes = profile.mostPlayedMinutes,
                            totalPlayTimeMinutes = profile.totalPlayTimeMinutes,
                            selectedTags = profile.selectedTags,
                            isVerifiedDeveloper = profile.isVerifiedDeveloper
                        )
                    }
                }
            } catch (_: Exception) {}

            val ranked = candidatesById.values
                .map { claim ->
                    val score = calculateAccountMatchScore(
                        rawQuery = trimmed,
                        candidateUsername = claim.username,
                        candidateDisplayName = claim.displayName
                    )
                    claim to score
                }
                .filter { (_, score) -> score >= 35 }
                .sortedWith(
                    compareByDescending<Pair<UsernameClaim, Int>> { it.second }
                        .thenByDescending { it.first.gamerscore }
                )
                .take(maxResults.coerceIn(1, 20))
                .map { it.first }

            Result.success(ranked)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Checks availability and claims a unique username in `/usernames/{usernameLower}`,
     * releasing any previous username claim owned by this user.
     */
    suspend fun claimAndSaveUsername(
        newUsername: String,
        previousUsername: String? = null
    ): Result<String> {
        val uid = requireUserId()
        val isDev = com.example.util.PremadeTagsCatalog.isOfficialDeveloperEmail(auth.currentUser?.email)
        val validated = com.example.util.PremadeTagsCatalog.validateUsername(newUsername, isDev)
            .getOrElse { return Result.failure(it) }
        val lower = validated.lowercase(Locale.ROOT)
        val claimRef = db.collection("usernames").document(lower)

        return try {
            val existingSnap = claimRef.get().await()
            if (existingSnap.exists()) {
                val ownerUid = existingSnap.getString("userId")
                if (ownerUid != null && ownerUid != uid) {
                    return Result.failure(IllegalArgumentException("Username @$validated is already taken by another player."))
                }
            }

            val userProfileSnap = try {
                db.collection("users").document(uid).get().await()
            } catch (_: Exception) {
                null
            }
            val currentProfile = userProfileSnap?.toObject(
                UserCloudProfile::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )

            val existingGamerscore = existingSnap.getLong("gamerscore") ?: 0L
            val existingPhoto = existingSnap.getString("photoUrl").orEmpty()
            val claimPayload = UsernameClaim(
                id = lower,
                userId = uid,
                username = validated,
                displayName = currentProfile?.displayName?.take(100) ?: validated,
                bio = currentProfile?.bio?.take(300) ?: "",
                photoUrl = currentProfile?.photoUrl?.take(1000)?.ifBlank { existingPhoto.take(1000) } ?: existingPhoto.take(1000),
                bannerUrl = currentProfile?.bannerUrl?.take(1000) ?: "",
                countryCode = currentProfile?.countryCode?.ifEmpty { "US" }?.take(10) ?: "US",
                countryName = currentProfile?.countryName?.ifEmpty { "United States" }?.take(100) ?: "United States",
                gamerscore = maxOf(currentProfile?.gamerscore?.coerceAtLeast(0L) ?: 0L, existingGamerscore),
                mostPlayedGameName = currentProfile?.mostPlayedGameName?.take(120) ?: "No games played yet",
                mostPlayedMinutes = currentProfile?.mostPlayedMinutes?.coerceAtLeast(0L) ?: 0L,
                totalPlayTimeMinutes = currentProfile?.totalPlayTimeMinutes?.coerceAtLeast(0L) ?: 0L,
                selectedTags = com.example.util.PremadeTagsCatalog.sanitizeSelectedTags(currentProfile?.selectedTags ?: emptyList()),
                isVerifiedDeveloper = isDev,
                updatedAt = null
            )
            claimRef.set(claimPayload).await()

            // Release previous username if different
            val prevClean = previousUsername?.trim()?.removePrefix("@")?.lowercase(Locale.ROOT)
            if (!prevClean.isNullOrBlank() && prevClean != lower && prevClean.length in 3..20) {
                try {
                    val prevRef = db.collection("usernames").document(prevClean)
                    val prevSnap = prevRef.get().await()
                    if (prevSnap.exists() && prevSnap.getString("userId") == uid) {
                        prevRef.delete().await()
                    }
                } catch (_: Exception) {}
            }

            // Update user profile if it exists
            try {
                db.collection("users").document(uid).update(
                    mapOf(
                        "username" to validated,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } catch (_: Exception) {}

            Result.success(validated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, claimRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateCountry(countryCode: String, countryName: String): Result<Unit> {
        val uid = requireUserId()
        val safeCode = countryCode.trim().uppercase(Locale.ROOT).take(10).ifEmpty { "US" }
        val safeName = countryName.trim().take(100).ifEmpty { "United States" }
        val docRef = db.collection("users").document(uid)
        return try {
            docRef.update(
                mapOf(
                    "countryCode" to safeCode,
                    "countryName" to safeName,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            try {
                val snap = docRef.get().await()
                val profile = snap.toObject(UserCloudProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                if (profile != null) syncUsernameClaim(profile)
            } catch (_: Exception) {}
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateDateOfBirth(dateOfBirth: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        val safeDob = dateOfBirth.trim().ifEmpty { "Not Set" }.take(40)
        return try {
            docRef.update(
                mapOf(
                    "dateOfBirth" to safeDob,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateBio(bio: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        val safeBio = bio.trim().take(300)
        return try {
            docRef.update(
                mapOf(
                    "bio" to safeBio,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            try {
                val snap = docRef.get().await()
                val profile = snap.toObject(UserCloudProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                if (profile != null) syncUsernameClaim(profile)
            } catch (_: Exception) {}
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateSelectedTags(tags: List<String>): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        val safeTags = com.example.util.PremadeTagsCatalog.sanitizeSelectedTags(tags)
        return try {
            docRef.update(
                mapOf(
                    "selectedTags" to safeTags,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            try {
                val snap = docRef.get().await()
                val profile = snap.toObject(UserCloudProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                if (profile != null) syncUsernameClaim(profile)
            } catch (_: Exception) {}
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateDisplayName(displayName: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        val isDev = com.example.util.PremadeTagsCatalog.isOfficialDeveloperEmail(auth.currentUser?.email)
        val safeName = com.example.util.PremadeTagsCatalog.sanitizeDisplayName(displayName, isDev).take(100)
        return try {
            docRef.update(
                mapOf(
                    "displayName" to safeName,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            try {
                val snap = docRef.get().await()
                val profile = snap.toObject(UserCloudProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                if (profile != null) syncUsernameClaim(profile)
            } catch (_: Exception) {}
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    private fun sanitizeProfile(raw: UserCloudProfile, uid: String): UserCloudProfile {
        val authEmail = auth.currentUser?.email ?: raw.email
        val isVerifiedDev = com.example.util.PremadeTagsCatalog.isOfficialDeveloperEmail(authEmail) &&
            com.example.util.PremadeTagsCatalog.isOfficialDeveloperEmail(auth.currentUser?.email)
        val safeTags = com.example.util.PremadeTagsCatalog.sanitizeSelectedTags(raw.selectedTags)
        val safeName = com.example.util.PremadeTagsCatalog.sanitizeDisplayName(raw.displayName, isVerifiedDev)
        val safeUsername = com.example.util.PremadeTagsCatalog.sanitizeUsername(raw.username, safeName, isVerifiedDev)

        return raw.copy(
            id = uid,
            userId = uid,
            displayName = safeName,
            username = safeUsername.take(30),
            countryCode = raw.countryCode.trim().uppercase(Locale.ROOT).ifEmpty { "US" }.take(10),
            countryName = raw.countryName.trim().ifEmpty { "United States" }.take(100),
            email = raw.email.trim().take(200),
            photoUrl = raw.photoUrl.trim().take(1000),
            dateOfBirth = raw.dateOfBirth.trim().ifEmpty { "Not Set" }.take(40),
            bio = raw.bio.trim().take(300),
            selectedTags = safeTags,
            isVerifiedDeveloper = isVerifiedDev,
            mostPlayedGameName = raw.mostPlayedGameName.trim().ifEmpty { "No games played yet" }.take(120),
            mostPlayedGamePkg = raw.mostPlayedGamePkg.trim().take(150),
            mostPlayedMinutes = raw.mostPlayedMinutes.coerceIn(0L, 10_000_000L),
            totalPlayTimeMinutes = raw.totalPlayTimeMinutes.coerceIn(0L, 100_000_000L),
            totalSessionsCount = raw.totalSessionsCount.coerceIn(0L, 10_000_000L),
            themeMode = raw.themeMode.trim().ifEmpty { "AUTO_SYSTEM" }.take(40),
            selectedBoostMode = raw.selectedBoostMode.trim().ifEmpty { "GAMING" }.take(30),
            appliedOptionIds = raw.appliedOptionIds.map { it.take(80) }.filter { it.isNotBlank() }.take(50),
            hudFpsMode = raw.hudFpsMode.trim().ifEmpty { "BALANCED" }.take(30),
            hudTargetCap = raw.hudTargetCap.trim().ifEmpty { "AUTO" }.take(20),
            hudPosition = raw.hudPosition.trim().ifEmpty { "TOP_START" }.take(30),
            hudScalePct = raw.hudScalePct.coerceIn(50L, 200L),
            hudOpacityPct = raw.hudOpacityPct.coerceIn(10L, 100L),
            crosshairType = raw.crosshairType.trim().ifEmpty { "CROSS" }.take(30),
            crosshairSizeDp = raw.crosshairSizeDp.coerceIn(8L, 100L),
            lightbarColorHex = raw.lightbarColorHex.trim().ifEmpty { "#38BDF8" }.take(20),
            recResolutionLabel = raw.recResolutionLabel.trim().ifEmpty { "1080p" }.take(20),
            recFps = raw.recFps.coerceIn(15L, 240L),
            recBitrateMbps = raw.recBitrateMbps.coerceIn(1L, 100L),
            recBubbleSizeDp = raw.recBubbleSizeDp.coerceIn(24L, 80L),
            targetGamePkg = raw.targetGamePkg.trim().take(150),
            targetGameName = raw.targetGameName.trim().take(120),
            libraryPackages = raw.libraryPackages.map { it.take(150) }.filter { it.isNotBlank() }.take(100),
            bannerUrl = raw.bannerUrl.trim().take(1000),
            gamerscore = raw.gamerscore.coerceAtLeast(0L)
        )
    }

    suspend fun updatePhotoUrl(photoUrl: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        val safePhoto = photoUrl.trim().take(1000)
        return try {
            docRef.update(
                mapOf(
                    "photoUrl" to safePhoto,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            try {
                val snap = docRef.get().await()
                val profile = snap.toObject(UserCloudProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                if (profile != null) syncUsernameClaim(profile)
            } catch (_: Exception) {}
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateBannerUrl(bannerUrl: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        val safeBanner = bannerUrl.trim().take(1000)
        return try {
            docRef.update(
                mapOf(
                    "bannerUrl" to safeBanner,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            try {
                val snap = docRef.get().await()
                val profile = snap.toObject(UserCloudProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                if (profile != null) syncUsernameClaim(profile)
            } catch (_: Exception) {}
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateGamerscore(score: Long): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        val safeScore = score.coerceAtLeast(0L)
        return try {
            docRef.update(
                mapOf(
                    "gamerscore" to safeScore,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            try {
                val snap = docRef.get().await()
                val profile = snap.toObject(UserCloudProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                if (profile != null) syncUsernameClaim(profile)
            } catch (_: Exception) {}
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteUserProfileAndAccount(): Result<Unit> {
        val uid = requireUserId()
        val user = auth.currentUser ?: throw IllegalStateException("Not authenticated")
        val docRef = db.collection("users").document(uid)
        return try {
            val snap = try { docRef.get().await() } catch (_: Exception) { null }
            val claimedUsername = snap?.getString("username")?.trim()?.lowercase(Locale.ROOT)
            if (!claimedUsername.isNullOrBlank()) {
                try {
                    db.collection("usernames").document(claimedUsername).delete().await()
                } catch (_: Exception) {}
            }
            docRef.delete().await()
            try {
                user.delete().await()
            } catch (_: Exception) {
                auth.signOut()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    companion object {
        fun Context.findActivity(): Activity? {
            var current: Context? = this
            while (current is ContextWrapper) {
                if (current is Activity) return current
                current = current.baseContext
            }
            return null
        }

        private fun isAlreadySignedIn(context: Context): Boolean {
            if (Firebase.auth.currentUser != null) return true
            val prefs = context.getSharedPreferences("gmax_hub_ui_prefs", Context.MODE_PRIVATE)
            val savedProvider = prefs.getString("hub_social_auth_provider", "").orEmpty()
            val savedEmail = prefs.getString("hub_user_custom_email", "").orEmpty()
            return savedProvider.isNotBlank() ||
                com.example.util.PremadeTagsCatalog.isOfficialDeveloperEmail(savedEmail)
        }

        private const val LOCAL_PLAYER_DIR_PREFS = "gmax_local_player_directory"
        private const val KEY_PLAYERS_JSON = "players_by_handle_json"

        /**
         * Persists a player's public profile snapshot into the device's multi-account player directory
         * so every unique account created or signed into on this device (or fetched from cloud)
         * can be looked up with its exact gamerscore, profile picture, bio, and nickname.
         */
        fun upsertLocalPlayerDirectoryEntry(
            context: Context,
            claim: UsernameClaim
        ) {
            val handle = claim.username.trim().removePrefix("@").lowercase(Locale.ROOT)
            if (handle.length < 2) return
            try {
                val prefs = context.applicationContext.getSharedPreferences(LOCAL_PLAYER_DIR_PREFS, Context.MODE_PRIVATE)
                val raw = prefs.getString(KEY_PLAYERS_JSON, null)
                val root = if (!raw.isNullOrBlank()) JSONObject(raw) else JSONObject()
                val existingObj = root.optJSONObject(handle)
                val prevPhoto = existingObj?.optString("photoUrl", "").orEmpty()
                val prevScore = existingObj?.optLong("gamerscore", 0L) ?: 0L
                val obj = JSONObject().apply {
                    put("userId", claim.userId.ifBlank { handle })
                    put("username", claim.username.trim().removePrefix("@"))
                    put("displayName", claim.displayName.ifBlank { claim.username })
                    put("bio", claim.bio)
                    put("photoUrl", claim.photoUrl.ifBlank { prevPhoto })
                    put("bannerUrl", claim.bannerUrl)
                    put("countryCode", claim.countryCode.ifBlank { "US" })
                    put("countryName", claim.countryName.ifBlank { "United States" })
                    put("gamerscore", if (claim.gamerscore > 0L) claim.gamerscore else prevScore)
                    put("mostPlayedGameName", claim.mostPlayedGameName)
                    put("mostPlayedMinutes", claim.mostPlayedMinutes)
                    put("totalPlayTimeMinutes", claim.totalPlayTimeMinutes)
                    put("isVerifiedDeveloper", claim.isVerifiedDeveloper)
                }
                root.put(handle, obj)
                prefs.edit().putString(KEY_PLAYERS_JSON, root.toString()).apply()
            } catch (_: Exception) {}
        }

        fun getLocalPlayerDirectoryEntries(context: Context): List<UsernameClaim> {
            val list = mutableListOf<UsernameClaim>()
            try {
                val prefs = context.applicationContext.getSharedPreferences(LOCAL_PLAYER_DIR_PREFS, Context.MODE_PRIVATE)
                val raw = prefs.getString(KEY_PLAYERS_JSON, null) ?: return list
                val root = JSONObject(raw)
                val keys = root.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val obj = root.optJSONObject(key) ?: continue
                    val uname = obj.optString("username", key)
                    list.add(
                        UsernameClaim(
                            id = key,
                            userId = obj.optString("userId", key),
                            username = uname,
                            displayName = obj.optString("displayName", uname),
                            bio = obj.optString("bio", ""),
                            photoUrl = obj.optString("photoUrl", ""),
                            bannerUrl = obj.optString("bannerUrl", ""),
                            countryCode = obj.optString("countryCode", "US"),
                            countryName = obj.optString("countryName", "United States"),
                            gamerscore = obj.optLong("gamerscore", 0L),
                            mostPlayedGameName = obj.optString("mostPlayedGameName", "No games played yet"),
                            mostPlayedMinutes = obj.optLong("mostPlayedMinutes", 0L),
                            totalPlayTimeMinutes = obj.optLong("totalPlayTimeMinutes", 0L),
                            isVerifiedDeveloper = obj.optBoolean("isVerifiedDeveloper", false)
                        )
                    )
                }
            } catch (_: Exception) {}
            return list
        }

        /**
         * Scores how closely [rawQuery] matches an account's unique [candidateUsername] (`@username`)
         * and/or shared [candidateDisplayName] (`displayName`).
         * Returns 0..1000 (higher = closer match).
         */
        fun calculateAccountMatchScore(
            rawQuery: String,
            candidateUsername: String,
            candidateDisplayName: String
        ): Int {
            val q = rawQuery.trim().removePrefix("@").lowercase(Locale.ROOT)
            if (q.isBlank()) return 0
            val qCompact = q.replace(Regex("[^a-z0-9]"), "")
            val u = candidateUsername.trim().removePrefix("@").lowercase(Locale.ROOT)
            val uCompact = u.replace(Regex("[^a-z0-9]"), "")
            val d = candidateDisplayName.trim().lowercase(Locale.ROOT)
            val dCompact = d.replace(Regex("[^a-z0-9]"), "")

            var best = 0

            // 1. Exact matches
            if (u.isNotEmpty() && u == q) best = maxOf(best, 1000)
            if (d.isNotEmpty() && d == q) best = maxOf(best, 960)
            if (qCompact.isNotEmpty() && uCompact == qCompact) best = maxOf(best, 940)
            if (qCompact.isNotEmpty() && dCompact == qCompact) best = maxOf(best, 920)

            // 2. Prefix matches
            if (u.isNotEmpty() && u.startsWith(q)) {
                val lenPenalty = (u.length - q.length).coerceIn(0, 20)
                best = maxOf(best, 880 - lenPenalty * 2)
            }
            if (d.isNotEmpty() && d.startsWith(q)) {
                val lenPenalty = (d.length - q.length).coerceIn(0, 25)
                best = maxOf(best, 850 - lenPenalty * 2)
            }
            // Word-boundary prefix in displayName (e.g., searching "Miller" matches "Alberto Miller")
            if (d.isNotEmpty()) {
                val words = d.split(Regex("\\s+")).filter { it.isNotBlank() }
                for (w in words) {
                    if (w == q) best = maxOf(best, 860)
                    else if (w.startsWith(q)) best = maxOf(best, 810)
                }
            }

            // 3. Substring matches
            if (u.isNotEmpty() && u.contains(q)) {
                val idx = u.indexOf(q).coerceAtLeast(0)
                best = maxOf(best, 760 - (idx * 6).coerceAtMost(60))
            }
            if (d.isNotEmpty() && d.contains(q)) {
                val idx = d.indexOf(q).coerceAtLeast(0)
                best = maxOf(best, 740 - (idx * 5).coerceAtMost(60))
            }
            if (qCompact.length >= 2) {
                if (uCompact.contains(qCompact)) best = maxOf(best, 710)
                if (dCompact.contains(qCompact)) best = maxOf(best, 690)
            }

            // 4. Fuzzy Levenshtein edit distance on username and display name
            if (qCompact.length >= 3) {
                if (uCompact.isNotEmpty()) {
                    val distU = levenshteinDistance(qCompact, uCompact)
                    val maxLenU = maxOf(qCompact.length, uCompact.length).coerceAtLeast(1)
                    val similarityPctU = ((maxLenU - distU).coerceAtLeast(0) * 100) / maxLenU
                    if (distU <= 3 || similarityPctU >= 55) {
                        best = maxOf(best, (similarityPctU * 6).coerceIn(40, 660))
                    }
                }
                if (dCompact.isNotEmpty()) {
                    val distD = levenshteinDistance(qCompact, dCompact)
                    val maxLenD = maxOf(qCompact.length, dCompact.length).coerceAtLeast(1)
                    val similarityPctD = ((maxLenD - distD).coerceAtLeast(0) * 100) / maxLenD
                    if (distD <= 3 || similarityPctD >= 55) {
                        best = maxOf(best, (similarityPctD * 6).coerceIn(40, 640))
                    }
                }
            }

            return best
        }

        fun levenshteinDistance(a: String, b: String): Int {
            if (a == b) return 0
            if (a.isEmpty()) return b.length
            if (b.isEmpty()) return a.length
            var prev = IntArray(b.length + 1) { it }
            var curr = IntArray(b.length + 1)
            for (i in a.indices) {
                curr[0] = i + 1
                for (j in b.indices) {
                    val cost = if (a[i] == b[j]) 0 else 1
                    curr[j + 1] = minOf(
                        curr[j] + 1,
                        prev[j + 1] + 1,
                        prev[j] + cost
                    )
                }
                val tmp = prev
                prev = curr
                curr = tmp
            }
            return prev[b.length]
        }

        fun attemptAutoSignIn(
            context: Context,
            credentialManager: CredentialManager,
            onAuthSuccess: () -> Unit,
            onUnauthenticated: () -> Unit,
            scope: CoroutineScope
        ) {
            // Never trigger a Google Credential Manager popup automatically on launch:
            // if the user is already signed in, complete immediately; otherwise stay unauthenticated until they tap Google Sign-In.
            if (isAlreadySignedIn(context)) {
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        }

        fun onGoogleSignInClicked(
            context: Context,
            credentialManager: CredentialManager,
            onAuthSuccess: () -> Unit,
            onAuthError: (String) -> Unit,
            scope: CoroutineScope,
            onAuthCancelled: () -> Unit = {},
            onRequireFallbackPicker: (() -> Unit)? = null
        ) {
            // Do not show the Google Sign-In popup if the user is already signed in
            if (Firebase.auth.currentUser != null) {
                onAuthSuccess()
                return
            }

            val clientId = try {
                context.getString(R.string.default_web_client_id).trim()
            } catch (e: Exception) {
                ""
            }

            if (clientId.isBlank()) {
                if (onRequireFallbackPicker != null) {
                    onRequireFallbackPicker()
                } else {
                    onAuthError("Google Sign-In configuration missing: default_web_client_id not found")
                }
                return
            }

            scope.launch {
                val activity = context.findActivity()
                val activityContext = activity ?: context

                // Stage 1: Try interactive GetSignInWithGoogleOption
                val stage1StartMs = android.os.SystemClock.elapsedRealtime()
                try {
                    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
                    val request = GetCredentialRequest.Builder()
                        .addCredentialOption(signInOption)
                        .build()

                    val result = credentialManager.getCredential(activityContext, request)
                    val credential = result.credential
                    if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                        val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                        val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                        Firebase.auth.signInWithCredential(authCredential).await()
                        onAuthSuccess()
                        return@launch
                    }
                } catch (e: GetCredentialCancellationException) {
                    val elapsedMs = android.os.SystemClock.elapsedRealtime() - stage1StartMs
                    val msg = e.message.orEmpty()
                    val isConfigOrInstantRejection = elapsedMs < 1200L ||
                        msg.contains("28444") ||
                        msg.contains("Developer console", ignoreCase = true) ||
                        msg.contains("configuration", ignoreCase = true) ||
                        msg.contains("failed", ignoreCase = true)
                    if (!isConfigOrInstantRejection) {
                        Log.w("Auth", "Google Sign-In flow cancelled by user after ${elapsedMs}ms: $msg")
                        onAuthCancelled()
                        return@launch
                    }
                    Log.w("Auth", "Stage 1 instant cancellation/rejection (${elapsedMs}ms: $msg), trying Stage 2 GetGoogleIdOption")
                } catch (e: Exception) {
                    Log.w("Auth", "Stage 1 GetSignInWithGoogleOption failed, trying Stage 2 GetGoogleIdOption", e)
                }

                // Stage 2: Try GetGoogleIdOption with filterByAuthorizedAccounts = false
                val stage2StartMs = android.os.SystemClock.elapsedRealtime()
                try {
                    val googleIdOption = GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId(clientId)
                        .setAutoSelectEnabled(false)
                        .build()
                    val fallbackRequest = GetCredentialRequest.Builder()
                        .addCredentialOption(googleIdOption)
                        .build()

                    val result = credentialManager.getCredential(activityContext, fallbackRequest)
                    val credential = result.credential
                    if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                        val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                        val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                        Firebase.auth.signInWithCredential(authCredential).await()
                        onAuthSuccess()
                        return@launch
                    }
                } catch (e: GetCredentialCancellationException) {
                    val elapsedMs = android.os.SystemClock.elapsedRealtime() - stage2StartMs
                    val msg = e.message.orEmpty()
                    val isConfigOrInstantRejection = elapsedMs < 1200L ||
                        msg.contains("28444") ||
                        msg.contains("Developer console", ignoreCase = true) ||
                        msg.contains("configuration", ignoreCase = true) ||
                        msg.contains("failed", ignoreCase = true)
                    if (!isConfigOrInstantRejection) {
                        Log.w("Auth", "Google ID option cancelled by user after ${elapsedMs}ms: $msg")
                        onAuthCancelled()
                        return@launch
                    }
                    Log.w("Auth", "Stage 2 instant cancellation/rejection (${elapsedMs}ms: $msg), trying Stage 3 Firebase Google OAuth")
                } catch (e: Exception) {
                    Log.w("Auth", "Stage 2 GetGoogleIdOption unavailable on device: ${e.message}")
                }

                // Stage 3: Try Firebase Auth Google OAuthProvider (web/Custom Tab flow that works without device Credential Manager or SHA-1 lock)
                if (activity != null) {
                    val stage3StartMs = android.os.SystemClock.elapsedRealtime()
                    try {
                        val googleOAuthProvider = OAuthProvider.newBuilder("google.com").apply {
                            scopes = listOf("email", "profile")
                            addCustomParameter("prompt", "select_account")
                        }.build()

                        val pending = Firebase.auth.pendingAuthResult
                        val authResult = if (pending != null) {
                            pending.await()
                        } else {
                            Firebase.auth.startActivityForSignInWithProvider(activity, googleOAuthProvider).await()
                        }
                        if (authResult?.user != null) {
                            onAuthSuccess()
                            return@launch
                        }
                    } catch (e: Exception) {
                        val elapsedMs = android.os.SystemClock.elapsedRealtime() - stage3StartMs
                        val msg = e.message.orEmpty()
                        val userExplicitlyClosedBrowser = elapsedMs >= 1500L &&
                            (msg.contains("user_cancelled", ignoreCase = true) ||
                                msg.contains("canceled by the user", ignoreCase = true))
                        if (userExplicitlyClosedBrowser) {
                            onAuthCancelled()
                            return@launch
                        }
                        Log.w("Auth", "Stage 3 Firebase Google OAuth provider fallback triggered: $msg")
                    }
                }

                // Stage 4: Fallback to interactive account picker / instant sign-in sheet
                if (onRequireFallbackPicker != null) {
                    onRequireFallbackPicker()
                } else {
                    onAuthError("No Google account found in device Credential Manager.")
                }
            }
        }

        fun signOut(
            credentialManager: CredentialManager,
            onSignOutComplete: () -> Unit,
            scope: CoroutineScope
        ) {
            Firebase.auth.signOut()
            scope.launch {
                try {
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (e: Exception) {
                    Log.e("Auth", "Failed to clear credential state", e)
                } finally {
                    onSignOutComplete()
                }
            }
        }
    }
}
