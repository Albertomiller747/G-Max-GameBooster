package com.example.data.repository

import android.app.Activity
import android.content.Context
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
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.IgnoreExtraProperties
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
    val email: String = "",
    val photoUrl: String = "",
    val dateOfBirth: String = "Not Set",
    val bio: String = "",
    val selectedTags: List<String> = emptyList(),
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
    @ServerTimestamp val createdAt: Timestamp? = null,
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

            if (existingSnap.exists() && existingCreatedAt != null) {
                val payload = sanitized.copy(
                    createdAt = existingCreatedAt,
                    updatedAt = null
                )
                docRef.set(payload).await()
                Result.success(payload.copy(id = uid))
            } else {
                val payload = sanitized.copy(
                    createdAt = null,
                    updatedAt = null
                )
                docRef.set(payload).await()
                Result.success(payload.copy(id = uid))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
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

        return raw.copy(
            id = uid,
            userId = uid,
            displayName = safeName,
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
            libraryPackages = raw.libraryPackages.map { it.take(150) }.filter { it.isNotBlank() }.take(100)
        )
    }

    companion object {
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
                context.getString(R.string.default_web_client_id)
            } catch (e: Exception) {
                onUnauthenticated()
                return
            }

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(true)
                .build()

            // Exactly one credential option per request for API 36 responsiveness
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            scope.launch {
                try {
                    val result = credentialManager.getCredential(context, request)
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
            onAuthCancelled: () -> Unit = {}
        ) {
            val clientId = try {
                context.getString(R.string.default_web_client_id)
            } catch (e: Exception) {
                onAuthError("Google Sign-In configuration missing: default_web_client_id not found")
                return
            }

            // Interactive Sign-In: MUST use GetSignInWithGoogleOption exclusively in its own request
            val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build()

            scope.launch {
                try {
                    val activityContext = context as? Activity ?: context
                    val result = credentialManager.getCredential(activityContext, request)
                    val credential = result.credential
                    if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                        val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                        val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                        Firebase.auth.signInWithCredential(authCredential).await()
                        onAuthSuccess()
                    } else {
                        onAuthError("Unexpected credential type")
                    }
                } catch (e: GetCredentialCancellationException) {
                    Log.w("Auth", "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
                    onAuthCancelled()
                } catch (e: Exception) {
                    Log.e("Auth", "Google Sign-In failed", e)
                    onAuthError(e.localizedMessage ?: "Sign in failed")
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
