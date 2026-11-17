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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Locale

@IgnoreExtraProperties
data class ReferralCodeEntry(
    @DocumentId val id: String = "",
    val referrerUid: String = "",
    val referrerUsername: String = "",
    val referrerDisplayName: String = "",
    val referrerDeviceHash: String = "",
    val code: String = "",
    val passesRemaining: Long = 3L,
    val validatedCount: Long = 0L,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ReferralActivation(
    @DocumentId val id: String = "",
    val inviteeUid: String = "",
    val inviteeUsername: String = "",
    val inviteeDisplayName: String = "",
    val referrerUid: String = "",
    val referrerUsername: String = "",
    val referrerCode: String = "",
    val deviceFingerprintHash: String = "",
    val status: String = STATUS_PENDING,
    val boostedMinutesCompleted: Long = 0L,
    val hudMatchCompleted: Boolean = false,
    val activationTrigger: String = TRIGGER_NONE,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val isActivated: Boolean
        get() = status == STATUS_ACTIVATED

    companion object {
        const val STATUS_PENDING = "PENDING_ACTIVATION"
        const val STATUS_ACTIVATED = "ACTIVATED"
        const val TRIGGER_NONE = "NONE"
        const val TRIGGER_15M_BOOST = "15M_BOOSTED_PLAY"
        const val TRIGGER_1_HUD_MATCH = "1_HUD_MATCH"
    }
}

@IgnoreExtraProperties
data class DeviceReferralClaim(
    @DocumentId val id: String = "",
    val deviceFingerprintHash: String = "",
    val inviteeUid: String = "",
    val referrerUid: String = "",
    val referrerCode: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

data class SquadMilestoneTier(
    val tierNumber: Int,
    val referralsNeeded: Int,
    val tierTagline: String,
    val rewardTitle: String,
    val proBonusLabel: String,
    val proDaysGranted: Int,
    val whyItWorks: String,
    val moduleKey: String
)

data class SquadPassSummaryState(
    val myReferralCode: String = "",
    val guestPassesRemaining: Int = 3,
    val guestPassesMaxBatch: Int = 3,
    val validatedSquadmatesCount: Int = 0,
    val pendingSquadmatesCount: Int = 0,
    val squadRoster: List<ReferralActivation> = emptyList(),
    val myInviteeActivation: ReferralActivation? = null,
    val inviteeStarterThemeUnlocked: Boolean = false,
    val invitee14DayProUnlocked: Boolean = false,
    val totalStackingProDaysEarned: Int = 0,
    val isLifetimeProUnlocked: Boolean = false,
    val unlockedModuleKeys: Set<String> = emptySet(),
    val equippedCrosshairPack: Boolean = false,
    val equippedAdvancedHud: Boolean = false,
    val equippedConsoleVanguardBadge: Boolean = false,
    val statusBannerMessage: String? = null,
    val highDopamineToastMessage: String? = null
)

object SquadMilestoneCatalog {
    const val MODULE_STARTER_THEME = "starter_crosshair_hud_theme"
    const val MODULE_CROSSHAIR_PACK = "custom_crosshair_pack"
    const val MODULE_ADVANCED_HUD = "advanced_performance_hud"
    const val MODULE_CONSOLE_VANGUARD = "console_mode_vanguard_badge"
    const val MODULE_AMBASSADOR_PRO = "ambassador_year_pro_pass"

    val TIERS: List<SquadMilestoneTier> = listOf(
        SquadMilestoneTier(
            tierNumber = 1,
            referralsNeeded = 1,
            tierTagline = "1 Squadmate",
            rewardTitle = "Permanent Custom Crosshair Pack + 7 Days Pro",
            proBonusLabel = "+7 Days Pro",
            proDaysGranted = 7,
            whyItWorks = "Sharing your referral code with your duo partner unlocks a permanent tactical reticle pack and 1 week of Pro.",
            moduleKey = MODULE_CROSSHAIR_PACK
        ),
        SquadMilestoneTier(
            tierNumber = 3.coerceAtMost(2),
            referralsNeeded = 3,
            tierTagline = "3 Squadmates (The Full Squad)",
            rewardTitle = "Permanent Advanced Performance HUD + 1 Month Pro",
            proBonusLabel = "+30 Days Pro",
            proDaysGranted = 30,
            whyItWorks = "Share your referral code in your main squad chat so the whole 3 to 4 player team runs GMax.",
            moduleKey = MODULE_ADVANCED_HUD
        ),
        SquadMilestoneTier(
            tierNumber = 3,
            referralsNeeded = 5,
            tierTagline = "5 Squadmates (The Clan Leader)",
            rewardTitle = "Permanent Console Mode / Pro Module + \"Founder/Vanguard\" Badge",
            proBonusLabel = "+60 Days Pro",
            proDaysGranted = 60,
            whyItWorks = "5 validated squadmates unlocks permanent Console Mode telemetry & the exclusive Vanguard profile emblem.",
            moduleKey = MODULE_CONSOLE_VANGUARD
        ),
        SquadMilestoneTier(
            tierNumber = 4,
            referralsNeeded = 10,
            tierTagline = "10+ Squadmates (Ambassador)",
            rewardTitle = "1 Year Pro Pass (Lifetime Pro at 15+ Squadmates)",
            proBonusLabel = "+1 Year / Lifetime Pro",
            proDaysGranted = 365,
            whyItWorks = "Built for Discord server admins and content creators sharing their referral code with the community.",
            moduleKey = MODULE_AMBASSADOR_PRO
        )
    )

    fun computeUnlockedModules(
        validatedReferrals: Int,
        inviteeActivation: ReferralActivation?
    ): Set<String> {
        val unlocked = LinkedHashSet<String>()
        if (inviteeActivation != null) {
            unlocked.add(MODULE_STARTER_THEME)
        }
        for (tier in TIERS) {
            if (validatedReferrals >= tier.referralsNeeded) {
                unlocked.add(tier.moduleKey)
            }
        }
        return unlocked
    }

    fun computeTotalProDays(
        validatedReferrals: Int,
        inviteeActivation: ReferralActivation?
    ): Int {
        var days = 0
        if (inviteeActivation?.isActivated == true) {
            days += 14
        }
        if (validatedReferrals >= 1) days += 7
        if (validatedReferrals >= 3) days += 30
        if (validatedReferrals >= 5) days += 60
        if (validatedReferrals >= 10) days += 365
        return days
    }

    fun computeScarcityPassesRemaining(totalClaimedOrValidated: Int): Int {
        val usedInCurrentWave = totalClaimedOrValidated % 3
        return (3 - usedInCurrentWave).coerceIn(1, 3)
    }
}

class SquadReferralRepository(private val db: FirebaseFirestore) {

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
                    en = "Sign in with Google first to use Squad Referral Codes.",
                    es = "Inicia sesión con Google primero para usar los códigos de referencia."
                )
            )
    }

    companion object {
        private val STRICT_CODE_REGEX = Regex("^GMAX-[A-Z0-9_\\-]{3,25}$")
        private val EMBEDDED_CODE_REGEX = Regex("\\b(GMAX-[A-Za-z0-9_\\-]{3,25})\\b")

        fun generateReferralCode(uid: String, username: String): String {
            val cleanUser = username.trim()
                .removePrefix("@")
                .uppercase(Locale.ROOT)
                .replace(Regex("[^A-Z0-9]"), "")
                .take(8)
                .ifEmpty { "SQUAD" }
            val cleanUidSuffix = uid.uppercase(Locale.ROOT)
                .replace(Regex("[^A-Z0-9]"), "")
                .takeLast(4)
                .padStart(4, 'X')
            return "GMAX-${cleanUser}-${cleanUidSuffix}".take(24)
        }

        /**
         * Strictly validates a `GMAX-USER-XXXX` referral code and rejects any URL or web link.
         */
        fun normalizeReferralCode(rawInput: String): String? {
            val trimmed = rawInput.trim()
            if (trimmed.isBlank()) return null

            // Reject URLs or deep links completely — referral is strictly code-only
            val lower = trimmed.lowercase(Locale.ROOT)
            if (lower.contains("http://") ||
                lower.contains("https://") ||
                lower.contains("gmax://") ||
                lower.contains("www.") ||
                lower.contains("?ref=") ||
                lower.contains("&ref=") ||
                lower.contains("/")
            ) {
                return null
            }

            val tokenMatch = EMBEDDED_CODE_REGEX.find(trimmed)
            if (tokenMatch != null) {
                val candidate = tokenMatch.groupValues[1].uppercase(Locale.ROOT)
                if (STRICT_CODE_REGEX.matches(candidate)) {
                    return candidate
                }
            }

            val directClean = trimmed.uppercase(Locale.ROOT).replace(Regex("[^A-Z0-9_\\-]"), "")
            return if (STRICT_CODE_REGEX.matches(directClean)) directClean else null
        }

        /**
         * Builds code-only share text (no URLs or deep links anywhere).
         */
        fun buildGuestPassShareCopy(
            referrerUsername: String,
            code: String,
            passesRemaining: Int,
            gameTitle: String? = null
        ): String {
            val cleanHandle = referrerUsername.trim().removePrefix("@").ifEmpty { "SquadLeader" }
            val cleanCode = code.trim().uppercase(Locale.ROOT).ifEmpty { "GMAX-SQUAD-0001" }
            val intro = if (!gameTitle.isNullOrBlank()) {
                AppStrings.trText(
                    en = "Just locked in high FPS telemetry on $gameTitle with GMAX Gaming Hub. ",
                    es = "Acabo de asegurar altos FPS en $gameTitle con GMAX Gaming Hub. "
                )
            } else {
                AppStrings.trText(
                    en = "Running GMAX Gaming Hub for 120FPS stability & smooth HUD telemetry. ",
                    es = "Usando GMAX Gaming Hub para estabilidad a 120FPS y telemetría HUD fluida. "
                )
            }
            val body = AppStrings.trText(
                en = "I have $passesRemaining Pro Guest Passes left to gift the squad. Enter my Squad Referral Code in Extras • Squad Pass for 14 Days of Full Pro Access + Starter Tactical Crosshair & HUD Theme:",
                es = "Me quedan $passesRemaining Pases Pro de Invitado para el escuadrón. Ingresa mi Código de Escuadrón en Extras • Pase de Escuadrón para 14 Días de Acceso Pro + Mira Táctica y Tema HUD:"
            )
            val codeLineLabel = AppStrings.trText(
                en = "🎟️ Squad Referral Code",
                es = "🎟️ Código de Escuadrón"
            )
            val footer = AppStrings.trText(
                en = "(Shared by @$cleanHandle • Enter code in Extras → Squad Pass)",
                es = "(Compartido por @$cleanHandle • Ingresa el código en Extras → Pase de Escuadrón)"
            )
            return buildString {
                append(intro)
                append(body)
                append("\n\n")
                append(codeLineLabel).append(": ").append(cleanCode).append("\n\n")
                append(footer)
            }
        }
    }

    suspend fun registerOrUpdateMyReferralCode(
        username: String,
        displayName: String,
        validatedCount: Long = 0L,
        passesRemaining: Long = 3L,
        referrerDeviceHash: String = ""
    ): Result<ReferralCodeEntry> {
        val uid = requireUserId()
        val code = generateReferralCode(uid, username)
        val safeUsername = username.trim().removePrefix("@").ifBlank { "player" }.take(40)
        val safeDisplayName = displayName.trim().ifBlank { safeUsername }.take(100)
        val safePasses = passesRemaining.coerceIn(0L, 999L)
        val safeValidated = validatedCount.coerceIn(0L, 100000L)
        val safeDeviceHash = referrerDeviceHash.trim()
            .replace(Regex("[^a-zA-Z0-9_\\-]"), "")
            .take(128)

        val docRef = db.collection("referral_codes").document(code)
        val payload = mutableMapOf<String, Any>(
            "referrerUid" to uid,
            "referrerUsername" to safeUsername,
            "referrerDisplayName" to safeDisplayName,
            "code" to code,
            "passesRemaining" to safePasses,
            "validatedCount" to safeValidated,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (safeDeviceHash.isNotBlank()) {
            payload["referrerDeviceHash"] = safeDeviceHash
        }

        return try {
            docRef.set(payload).await()
            Result.success(
                ReferralCodeEntry(
                    id = code,
                    referrerUid = uid,
                    referrerUsername = safeUsername,
                    referrerDisplayName = safeDisplayName,
                    referrerDeviceHash = safeDeviceHash,
                    code = code,
                    passesRemaining = safePasses,
                    validatedCount = safeValidated
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun lookupReferralCode(rawCode: String): Result<ReferralCodeEntry?> {
        val normalized = normalizeReferralCode(rawCode)
            ?: return Result.failure(
                IllegalArgumentException(
                    AppStrings.trText(
                        en = "Invalid Squad Referral Code format. Enter a code like GMAX-SQUAD-1234 (URLs are not accepted).",
                        es = "Formato de código inválido. Ingresa un código como GMAX-SQUAD-1234 (no se aceptan enlaces URL)."
                    )
                )
            )
        val docRef = db.collection("referral_codes").document(normalized)
        return try {
            val snap = docRef.get().await()
            if (snap.exists()) {
                val parsed = snap.toObject(
                    ReferralCodeEntry::class.java,
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

    suspend fun claimGuestPass(
        rawCodeOrLink: String,
        inviteeUsername: String,
        inviteeDisplayName: String,
        deviceFingerprintHash: String
    ): Result<ReferralActivation> {
        val uid = requireUserId()
        val normalizedCode = normalizeReferralCode(rawCodeOrLink)
            ?: return Result.failure(
                IllegalArgumentException(
                    AppStrings.trText(
                        en = "Enter a valid Squad Referral Code (e.g., GMAX-SQUAD-1234). URLs are not accepted.",
                        es = "Ingresa un Código de Escuadrón válido (ej. GMAX-SQUAD-1234). No se aceptan enlaces URL."
                    )
                )
            )
        val safeFingerprint = deviceFingerprintHash.trim()
            .replace(Regex("[^a-zA-Z0-9_\\-]"), "")
            .take(128)
        if (safeFingerprint.length < 16) {
            return Result.failure(
                IllegalArgumentException(
                    AppStrings.trText(
                        en = "Hardware fingerprint verification failed.",
                        es = "Falló la verificación de huella de hardware del dispositivo."
                    )
                )
            )
        }

        // 1. Check if this invitee already claimed a Guest Pass
        val activationRef = db.collection("referral_activations").document(uid)
        try {
            val existingActivationSnap = activationRef.get().await()
            if (existingActivationSnap.exists()) {
                val existing = existingActivationSnap.toObject(
                    ReferralActivation::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                if (existing != null) {
                    return Result.success(existing)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, activationRef.path)
            return Result.failure(e)
        }

        // 2. Resolve the referral code entry
        val codeEntry = lookupReferralCode(normalizedCode).getOrElse { return Result.failure(it) }
            ?: return Result.failure(
                IllegalArgumentException(
                    AppStrings.trText(
                        en = "Squad Referral Code $normalizedCode was not found.",
                        es = "No se encontró el Código de Escuadrón $normalizedCode."
                    )
                )
            )

        // 3. Anti-Fraud Guard A: Cannot claim your own referral code
        if (codeEntry.referrerUid == uid) {
            return Result.failure(
                IllegalArgumentException(
                    AppStrings.trText(
                        en = "Anti-Fraud Guard: You cannot claim your own Squad Referral Code.",
                        es = "Protección Antifraude: No puedes reclamar tu propio Código de Escuadrón."
                    )
                )
            )
        }

        // 4. Anti-Fraud Guard B: Block same-device self-referrals (switching Google accounts on the same phone)
        if (codeEntry.referrerDeviceHash.isNotBlank() &&
            codeEntry.referrerDeviceHash.equals(safeFingerprint, ignoreCase = true)
        ) {
            return Result.failure(
                IllegalStateException(
                    AppStrings.trText(
                        en = "Hardware Anti-Fraud Guard: You cannot claim a referral code created on this same physical device.",
                        es = "Protección Antifraude de Hardware: No puedes reclamar un código creado en este mismo dispositivo físico."
                    )
                )
            )
        }

        // 5. Anti-Fraud Guard C: Block circular referrals (User A referred User B -> User B cannot refer User A)
        try {
            val referrerActivationSnap = db.collection("referral_activations")
                .document(codeEntry.referrerUid)
                .get()
                .await()
            if (referrerActivationSnap.exists() && referrerActivationSnap.getString("referrerUid") == uid) {
                return Result.failure(
                    IllegalStateException(
                        AppStrings.trText(
                            en = "Anti-Fraud Guard: Mutual/circular referrals between the same two accounts are not allowed.",
                            es = "Protección Antifraude: No se permiten referencias mutuas o circulares entre las mismas dos cuentas."
                        )
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Permission denied on cross-account activation doc means current user is NOT their referrer; safe to proceed
        }

        // 6. Anti-Fraud Guard D: 1 Claim Per Physical Hardware Device
        val deviceClaimRef = db.collection("device_referral_claims").document(safeFingerprint)
        try {
            val existingDeviceSnap = deviceClaimRef.get().await()
            if (existingDeviceSnap.exists()) {
                val claimedByUid = existingDeviceSnap.getString("inviteeUid")
                if (claimedByUid != null && claimedByUid != uid) {
                    return Result.failure(
                        IllegalStateException(
                            AppStrings.trText(
                                en = "Hardware Anti-Fraud Guard: This device has already claimed a Squad Referral Code on another account.",
                                es = "Protección Antifraude de Hardware: Este dispositivo ya reclamó un Código de Escuadrón en otra cuenta."
                            )
                        )
                    )
                }
            } else {
                val devicePayload = mapOf(
                    "deviceFingerprintHash" to safeFingerprint,
                    "inviteeUid" to uid,
                    "referrerUid" to codeEntry.referrerUid,
                    "referrerCode" to normalizedCode,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                deviceClaimRef.set(devicePayload).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, deviceClaimRef.path)
            return Result.failure(e)
        }

        // 7. Create the PENDING_ACTIVATION record (rewards trigger only on Verified Core Action)
        val safeInviteeUser = inviteeUsername.trim().removePrefix("@").ifBlank { "squadmate" }.take(40)
        val safeInviteeDisplay = inviteeDisplayName.trim().ifBlank { safeInviteeUser }.take(100)
        val safeReferrerUser = codeEntry.referrerUsername.trim().ifBlank { "squad_leader" }.take(40)

        val activationPayload = mapOf(
            "inviteeUid" to uid,
            "inviteeUsername" to safeInviteeUser,
            "inviteeDisplayName" to safeInviteeDisplay,
            "referrerUid" to codeEntry.referrerUid,
            "referrerUsername" to safeReferrerUser,
            "referrerCode" to normalizedCode,
            "deviceFingerprintHash" to safeFingerprint,
            "status" to ReferralActivation.STATUS_PENDING,
            "boostedMinutesCompleted" to 0L,
            "hudMatchCompleted" to false,
            "activationTrigger" to ReferralActivation.TRIGGER_NONE,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        return try {
            activationRef.set(activationPayload).await()
            Result.success(
                ReferralActivation(
                    id = uid,
                    inviteeUid = uid,
                    inviteeUsername = safeInviteeUser,
                    inviteeDisplayName = safeInviteeDisplay,
                    referrerUid = codeEntry.referrerUid,
                    referrerUsername = safeReferrerUser,
                    referrerCode = normalizedCode,
                    deviceFingerprintHash = safeFingerprint,
                    status = ReferralActivation.STATUS_PENDING,
                    boostedMinutesCompleted = 0L,
                    hudMatchCompleted = false,
                    activationTrigger = ReferralActivation.TRIGGER_NONE
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, activationRef.path)
            Result.failure(e)
        }
    }

    /**
     * Records Verified Core Action progress (15 minutes of boosted gameplay OR 1 completed match with HUD active).
     * Automatically transitions a PENDING_ACTIVATION Guest Pass to ACTIVATED once the threshold is met.
     */
    suspend fun recordVerifiedCoreActionProgress(
        inviteeUsername: String,
        inviteeDisplayName: String,
        additionalBoostedMinutes: Long = 0L,
        completedHudMatch: Boolean = false
    ): Result<ReferralActivation?> {
        val uid = requireUserId()
        val activationRef = db.collection("referral_activations").document(uid)
        return try {
            val snap = activationRef.get().await()
            if (!snap.exists()) {
                return Result.success(null)
            }
            val current = snap.toObject(
                ReferralActivation::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return Result.success(null)

            if (current.status == ReferralActivation.STATUS_ACTIVATED) {
                return Result.success(current)
            }

            val updatedMinutes = (current.boostedMinutesCompleted + additionalBoostedMinutes.coerceAtLeast(0L))
                .coerceIn(0L, 100000L)
            val updatedHudMatch = current.hudMatchCompleted || completedHudMatch
            val meetsThreshold = updatedMinutes >= 15L || updatedHudMatch

            val newStatus = if (meetsThreshold) {
                ReferralActivation.STATUS_ACTIVATED
            } else {
                ReferralActivation.STATUS_PENDING
            }
            val newTrigger = when {
                updatedHudMatch -> ReferralActivation.TRIGGER_1_HUD_MATCH
                updatedMinutes >= 15L -> ReferralActivation.TRIGGER_15M_BOOST
                else -> ReferralActivation.TRIGGER_NONE
            }

            val safeUser = inviteeUsername.trim().removePrefix("@")
                .ifBlank { current.inviteeUsername.ifBlank { "squadmate" } }
                .take(40)
            val safeDisplay = inviteeDisplayName.trim()
                .ifBlank { current.inviteeDisplayName.ifBlank { safeUser } }
                .take(100)

            val updateMap = mapOf(
                "inviteeUsername" to safeUser,
                "inviteeDisplayName" to safeDisplay,
                "status" to newStatus,
                "boostedMinutesCompleted" to updatedMinutes,
                "hudMatchCompleted" to updatedHudMatch,
                "activationTrigger" to newTrigger,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            activationRef.update(updateMap).await()

            Result.success(
                current.copy(
                    id = uid,
                    inviteeUsername = safeUser,
                    inviteeDisplayName = safeDisplay,
                    status = newStatus,
                    boostedMinutesCompleted = updatedMinutes,
                    hudMatchCompleted = updatedHudMatch,
                    activationTrigger = newTrigger
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, activationRef.path)
            Result.failure(e)
        }
    }

    fun observeReferrerSquadActivations(referrerUid: String): Flow<List<ReferralActivation>> {
        val path = "referral_activations"
        return db.collection(path)
            .whereEqualTo("referrerUid", referrerUid)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    ReferralActivation::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == referrerUid) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                }
                throw error
            }
    }

    fun observeInviteeActivation(inviteeUid: String): Flow<ReferralActivation?> {
        val path = "referral_activations/$inviteeUid"
        return db.collection("referral_activations").document(inviteeUid)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(
                        ReferralActivation::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                } else {
                    null
                }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == inviteeUid) {
                    handleFirestoreError(exceptionToLog, OperationType.GET, path)
                }
                throw error
            }
    }
}
