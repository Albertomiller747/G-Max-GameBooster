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
data class LeaderboardEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val username: String = "Gamer",
    val photoUrl: String = "",
    val countryCode: String = "US",
    val countryName: String = "United States",
    val gamerscore: Long = 0L,
    @get:PropertyName("isVerifiedDeveloper")
    val isVerifiedDeveloper: Boolean = false,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class UsernameClaim(
    @DocumentId val id: String = "",
    val userId: String = "",
    val username: String = "",
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

            // Best-effort sync to worldwide leaderboard
            try {
                syncLeaderboardEntry(savedProfile)
            } catch (_: Exception) {}

            Result.success(savedProfile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun syncLeaderboardEntry(profile: UserCloudProfile): Result<Unit> {
        val uid = requireUserId()
        val leaderboardRef = db.collection("leaderboard").document(uid)
        val isDev = com.example.util.PremadeTagsCatalog.isOfficialDeveloperEmail(auth.currentUser?.email)
        val handle = com.example.util.PremadeTagsCatalog.sanitizeUsername(
            profile.username,
            profile.displayName,
            isDev
        )
        val entry = LeaderboardEntry(
            id = uid,
            userId = uid,
            username = handle,
            photoUrl = profile.photoUrl.trim().take(1000),
            countryCode = profile.countryCode.trim().ifEmpty { "US" }.take(10),
            countryName = profile.countryName.trim().ifEmpty { "United States" }.take(100),
            gamerscore = profile.gamerscore.coerceAtLeast(0L),
            isVerifiedDeveloper = isDev,
            updatedAt = null
        )
        return try {
            leaderboardRef.set(entry).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, leaderboardRef.path)
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

            val claimPayload = UsernameClaim(
                id = lower,
                userId = uid,
                username = validated,
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

            // Update user profile & leaderboard entry if they exist
            try {
                db.collection("users").document(uid).update(
                    mapOf(
                        "username" to validated,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } catch (_: Exception) {}

            try {
                db.collection("leaderboard").document(uid).update(
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
                db.collection("leaderboard").document(uid).update(
                    mapOf(
                        "countryCode" to safeCode,
                        "countryName" to safeName,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } catch (_: Exception) {}
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeWorldwideLeaderboard(limitCount: Long = 100L): Flow<List<LeaderboardEntry>> {
        val path = "leaderboard"
        return db.collection("leaderboard")
            .whereGreaterThanOrEqualTo("gamerscore", 0L)
            .orderBy("gamerscore", Query.Direction.DESCENDING)
            .limit(limitCount)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    doc.toObject(
                        LeaderboardEntry::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
            }
            .catch { error ->
                if (error is Exception) {
                    handleFirestoreError(error, OperationType.LIST, path)
                }
                throw error
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
        val safeName = com.example.util.PremadeTagsCatalog.sanitizeDisplayName(displayName, isDev)
        return try {
            docRef.update(
                mapOf(
                    "displayName" to safeName,
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
                db.collection("leaderboard").document(uid).update(
                    mapOf(
                        "photoUrl" to safePhoto,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
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
                db.collection("leaderboard").document(uid).update(
                    mapOf(
                        "gamerscore" to safeScore,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
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
            try {
                db.collection("leaderboard").document(uid).delete().await()
            } catch (_: Exception) {}
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

        fun attemptAutoSignIn(
            context: Context,
            credentialManager: CredentialManager,
            onAuthSuccess: () -> Unit,
            onUnauthenticated: () -> Unit,
            scope: CoroutineScope
        ) {
            if (Firebase.auth.currentUser != null) {
                onAuthSuccess()
                return
            }
            val clientId = try {
                context.getString(R.string.default_web_client_id).trim()
            } catch (e: Exception) {
                onUnauthenticated()
                return
            }
            if (clientId.isBlank()) {
                onUnauthenticated()
                return
            }

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(true)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            scope.launch {
                try {
                    val activityContext = context.findActivity() ?: context
                    val result = credentialManager.getCredential(activityContext, request)
                    val credential = result.credential
                    if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                        val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                        val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                        Firebase.auth.signInWithCredential(authCredential).await()
                        onAuthSuccess()
                    } else {
                        onUnauthenticated()
                    }
                } catch (e: Exception) {
                    onUnauthenticated()
                }
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
                    Log.w("Auth", "Google Sign-In flow cancelled by user: ${e.message}")
                    onAuthCancelled()
                    return@launch
                } catch (e: Exception) {
                    Log.w("Auth", "Stage 1 GetSignInWithGoogleOption failed, trying Stage 2 GetGoogleIdOption", e)
                }

                // Stage 2: Try GetGoogleIdOption with filterByAuthorizedAccounts = false
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
                    Log.w("Auth", "Google ID option cancelled by user: ${e.message}")
                    onAuthCancelled()
                    return@launch
                } catch (e: Exception) {
                    Log.w("Auth", "Stage 2 GetGoogleIdOption unavailable on device: ${e.message}")
                }

                // Stage 3: Fallback to interactive account picker / instant sign-in sheet
                if (onRequireFallbackPicker != null) {
                    onRequireFallbackPicker()
                } else {
                    onAuthError("No Google account found in device Credential Manager.")
                }
            }
        }

        fun onFacebookSignInClicked(
            context: Context,
            onAuthSuccess: () -> Unit,
            onAuthError: (String) -> Unit,
            scope: CoroutineScope,
            onAuthCancelled: () -> Unit = {},
            onRequireFallbackPicker: (() -> Unit)? = null
        ) {
            scope.launch {
                val activity = context.findActivity()
                if (activity != null) {
                    try {
                        val provider = OAuthProvider.newBuilder("facebook.com").apply {
                            scopes = listOf("email", "public_profile")
                        }.build()

                        val pending = Firebase.auth.pendingAuthResult
                        val authResult = if (pending != null) {
                            pending.await()
                        } else {
                            Firebase.auth.startActivityForSignInWithProvider(activity, provider).await()
                        }
                        if (authResult?.user != null) {
                            onAuthSuccess()
                            return@launch
                        }
                    } catch (e: Exception) {
                        val msg = e.message ?: ""
                        if (msg.contains("cancel", ignoreCase = true) || msg.contains("user_cancelled", ignoreCase = true)) {
                            onAuthCancelled()
                            return@launch
                        }
                        Log.w("Auth", "Firebase Facebook OAuth provider fallback triggered: $msg")
                    }
                }

                if (onRequireFallbackPicker != null) {
                    onRequireFallbackPicker()
                } else {
                    onAuthError("Facebook login could not be completed.")
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
