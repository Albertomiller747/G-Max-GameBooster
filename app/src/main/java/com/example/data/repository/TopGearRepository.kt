package com.example.data.repository

import android.content.Context
import com.example.BuildConfig
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

data class TopGearProduct(
    val id: String,
    val category: String,
    val group: String,
    val tier: String, // "budget", "value", "premium"
    val badge: String? = null,
    val name: String,
    val imageUrl: String = "",
    val benefitLine: String,
    val benefits: List<String>,
    val priceLevel: String, // "$", "$$", "$$$"
    val bestFor: String,
    val tradeOff: String,
    val pairsWith: String? = null,
    val affiliateUrl: String,
    val merchant: String = "Amazon",
    val active: Boolean = true,
    val lastUpdated: String = "2026-10-31"
) {
    val tierOrder: Int
        get() = when (tier.lowercase()) {
            "budget" -> 0
            "value" -> 1
            "premium" -> 2
            else -> 3
        }

    fun displayTierLabel(): String {
        return when (tier.lowercase()) {
            "budget" -> "Budget"
            "value" -> "Best Value"
            "premium" -> "Premium"
            else -> tier.replaceFirstChar { it.uppercase() }
        }
    }

    val isAvailable: Boolean
        get() = active && affiliateUrl.isNotBlank() && affiliateUrl.startsWith("http")
}

data class TopGearProductGroup(
    val groupTitle: String,
    val category: String,
    val whyThisMatters: String,
    val products: List<TopGearProduct>
)

enum class TopGearSyncSource {
    BUNDLED,
    LOCAL_CACHE,
    FIRESTORE_REMOTE
}

data class TopGearCatalogState(
    val isLoading: Boolean = true,
    val isOffline: Boolean = false,
    val syncSource: TopGearSyncSource = TopGearSyncSource.BUNDLED,
    val allActiveProducts: List<TopGearProduct> = emptyList(),
    val availableCategories: List<String> = listOf("All"),
    val selectedCategory: String = "All",
    val groups: List<TopGearProductGroup> = emptyList(),
    val resolvedApiImages: Map<String, String> = emptyMap(),
    val errorMessage: String? = null
)

/**
 * Privacy-first anonymous tap event logger.
 * Records ONLY product_id, tier, and action type locally (no user ID, no device ID, no personal data).
 * Can be disabled at any time via a toggle in Top Gear or Settings.
 */
object TopGearAnalyticsManager {
    private const val PREFS_NAME = "gmax_top_gear_analytics"
    private const val KEY_ANALYTICS_ENABLED = "anonymous_tap_logging_enabled"
    private const val KEY_TAP_COUNTS_JSON = "anonymous_tap_counts_json"

    private val _isEnabledFlow = MutableStateFlow(true)
    val isEnabledFlow: StateFlow<Boolean> = _isEnabledFlow.asStateFlow()

    fun isAnalyticsEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(KEY_ANALYTICS_ENABLED, true)
        _isEnabledFlow.value = enabled
        return enabled
    }

    fun setAnalyticsEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ANALYTICS_ENABLED, enabled).apply()
        _isEnabledFlow.value = enabled
    }

    fun logAnonymousTap(context: Context, productId: String, tier: String, action: String = "affiliate_open") {
        if (!isAnalyticsEnabled(context)) return
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_TAP_COUNTS_JSON, "{}") ?: "{}"
            val json = JSONObject(raw)
            val key = "${productId}__${tier.lowercase()}__$action"
            val currentCount = json.optInt(key, 0)
            json.put(key, currentCount + 1)
            prefs.edit().putString(KEY_TAP_COUNTS_JSON, json.toString()).apply()
        } catch (_: Exception) {}
    }

    fun getAnonymousTierTapSummary(context: Context): Map<String, Int> {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_TAP_COUNTS_JSON, "{}") ?: "{}"
            val json = JSONObject(raw)
            val summary = mutableMapOf("budget" to 0, "value" to 0, "premium" to 0)
            val keys = json.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val parts = k.split("__")
                if (parts.size >= 2) {
                    val tier = parts[1]
                    summary[tier] = (summary[tier] ?: 0) + json.optInt(k, 0)
                }
            }
            summary
        } catch (_: Exception) {
            emptyMap()
        }
    }
}

/**
 * Optional Amazon Creators API v3.1 in-memory image URL resolver.
 * Never scrapes Amazon HTML pages and never downloads or permanently stores images on disk.
 */
object AmazonCreatorsApiClient {
    private val inMemoryImageCache = ConcurrentHashMap<String, String>()
    @Volatile
    private var cachedAccessToken: String? = null
    @Volatile
    private var tokenExpiryEpochMs: Long = 0L

    private fun readOptionalConfig(fieldName: String, defaultVal: String = ""): String {
        return try {
            (BuildConfig::class.java.getDeclaredField(fieldName).get(null) as? String) ?: defaultVal
        } catch (_: Throwable) {
            defaultVal
        }
    }

    fun isConfigured(): Boolean {
        val credId = readOptionalConfig("AMAZON_CREATORS_CREDENTIAL_ID")
        val secret = readOptionalConfig("AMAZON_CREATORS_SECRET")
        return credId.isNotBlank() &&
            secret.isNotBlank() &&
            credId != "YOUR_AMAZON_CREATORS_CREDENTIAL_ID" &&
            secret != "YOUR_AMAZON_CREATORS_SECRET"
    }

    suspend fun resolveProductImagesInMemory(products: List<TopGearProduct>): Map<String, String> =
        withContext(Dispatchers.IO) {
            if (!isConfigured()) return@withContext inMemoryImageCache.toMap()
            val missing = products.filter { it.imageUrl.isBlank() && !inMemoryImageCache.containsKey(it.id) }
            if (missing.isEmpty()) return@withContext inMemoryImageCache.toMap()

            try {
                val token = fetchOAuthTokenIfNeeded() ?: return@withContext inMemoryImageCache.toMap()
                for (product in missing) {
                    val shortCode = product.affiliateUrl.substringAfterLast("/").trim()
                    if (shortCode.isNotBlank()) {
                        val resolvedUrl = queryCreatorsApiImage(token, shortCode)
                        if (!resolvedUrl.isNullOrBlank()) {
                            inMemoryImageCache[product.id] = resolvedUrl
                        }
                    }
                }
            } catch (_: Exception) {
                // Gracefully fall back to curated vector placeholders if API is unreachable
            }
            inMemoryImageCache.toMap()
        }

    private fun fetchOAuthTokenIfNeeded(): String? {
        val now = System.currentTimeMillis()
        val existing = cachedAccessToken
        if (!existing.isNullOrBlank() && now < tokenExpiryEpochMs) {
            return existing
        }
        val credId = readOptionalConfig("AMAZON_CREATORS_CREDENTIAL_ID")
        val secret = readOptionalConfig("AMAZON_CREATORS_SECRET")
        if (credId.isBlank() || secret.isBlank()) return null
        var conn: HttpURLConnection? = null
        return try {
            val url = URL("https://api.amazon.com/auth/o2/token")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 4500
                readTimeout = 4500
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
            }
            val body = "grant_type=client_credentials" +
                "&client_id=${java.net.URLEncoder.encode(credId, "UTF-8")}" +
                "&client_secret=${java.net.URLEncoder.encode(secret, "UTF-8")}"
            OutputStreamWriter(conn.outputStream).use { it.write(body) }
            if (conn.responseCode in 200..299) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                val accessToken = json.optString("access_token", "")
                val expiresInSec = json.optLong("expires_in", 3600L)
                if (accessToken.isNotBlank()) {
                    cachedAccessToken = accessToken
                    tokenExpiryEpochMs = now + (expiresInSec - 60L).coerceAtLeast(60L) * 1000L
                    accessToken
                } else null
            } else null
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun queryCreatorsApiImage(bearerToken: String, itemIdentifier: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            val appId = readOptionalConfig("AMAZON_CREATORS_APP_ID")
            val version = readOptionalConfig("AMAZON_CREATORS_VERSION", "3.1").ifBlank { "3.1" }
            val endpoint = URL("https://webservices.amazon.com/paapi5/getitems")
            conn = (endpoint.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 4500
                readTimeout = 4500
                doOutput = true
                setRequestProperty("Authorization", "Bearer $bearerToken")
                setRequestProperty("X-Amz-Target", "com.amazon.paapi5.v1.ProductAdvertisingAPIv1.GetItems")
                setRequestProperty("X-Amz-Creators-App-Id", appId)
                setRequestProperty("X-Amz-Creators-Version", version)
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
            val payload = JSONObject().apply {
                put("ItemIds", JSONArray().put(itemIdentifier))
                put("Resources", JSONArray().put("Images.Primary.Large"))
            }
            OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }
            if (conn.responseCode in 200..299) {
                val resp = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val items = resp.optJSONObject("ItemsResult")?.optJSONArray("Items")
                val first = items?.optJSONObject(0)
                first?.optJSONObject("Images")
                    ?.optJSONObject("Primary")
                    ?.optJSONObject("Large")
                    ?.optString("URL")
                    ?.takeIf { it.startsWith("https://") }
            } else null
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}

class TopGearRepository(private val context: Context) {

    companion object {
        const val ASSET_FILE_NAME = "top_gear_products.json"
        private const val LOCAL_CACHE_FILE_NAME = "top_gear_cache.json"
        private const val FIRESTORE_COLLECTION = "top_gear_products"

        val ORDERED_CATEGORIES = listOf(
            "All",
            "Cooling",
            "Controllers",
            "Finger Sleeves",
            "Power",
            "Audio",
            "Accessories"
        )

        private val GROUP_WHY_THIS_MATTERS = mapOf(
            "Phone Coolers" to "Heat is the #1 cause of sudden FPS drops and screen dimming after 15 minutes of play.",
            "Tablet Coolers" to "Large tablet processors draw higher wattage and need wider cooling plates to stay at peak clock speeds.",
            "Wireless Console & PC Controllers" to "Pair a full-size Bluetooth controller to unlock GMAX Landscape Console Mode and zero-touch screen visibility.",
            "Telescopic Mobile Controllers" to "Snap your phone into a dedicated handheld console with physical sticks and triggers.",
            "Clip-On Mobile Triggers" to "Keep touchscreen swipe speed while moving aim and fire actions to physical index-finger buttons.",
            "Touchscreen Finger Sleeves" to "Eliminate sweat drag and thumb stutter so every micro-aim adjustment registers smoothly.",
            "Wireless Gaming Audio" to "Low-latency wireless audio lets you hear footsteps and reload cues the instant they happen.",
            "Desk & Streaming Accessories" to "Play comfortably at eye level in Console Mode or capture crisp 60FPS HDMI gameplay."
        )

        fun getWhyThisMattersForGroup(groupTitle: String, category: String): String {
            return GROUP_WHY_THIS_MATTERS[groupTitle] ?: when (category) {
                "Cooling" -> "Sustained thermal dissipation prevents CPU/GPU throttling during long matches."
                "Controllers" -> "Physical controls improve movement accuracy and pair directly with GMAX Console Mode."
                "Finger Sleeves" -> "Consistent low-friction glide for responsive touchscreen aiming."
                "Power" -> "Reliable power delivery keeps your device charged without excess heat."
                "Audio" -> "Accurate directional sound cues without distracting wireless lag."
                else -> "Thoughtfully chosen upgrades that make mobile gaming more comfortable and consistent."
            }
        }

        fun parseProductsJson(jsonStr: String): List<TopGearProduct> {
            val array = JSONArray(jsonStr)
            val result = mutableListOf<TopGearProduct>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val benefitsArr = obj.optJSONArray("benefits")
                val benefits = mutableListOf<String>()
                if (benefitsArr != null) {
                    for (j in 0 until minOf(benefitsArr.length(), 3)) {
                        val b = benefitsArr.optString(j, "").trim()
                        if (b.isNotEmpty()) benefits.add(b)
                    }
                }
                val rawBadge = if (obj.isNull("badge")) null else obj.optString("badge", "").takeIf { it.isNotBlank() }
                val rawPairsWith = if (obj.isNull("pairs_with")) null else obj.optString("pairs_with", "").takeIf { it.isNotBlank() }

                result.add(
                    TopGearProduct(
                        id = obj.optString("id", ""),
                        category = obj.optString("category", ""),
                        group = obj.optString("group", ""),
                        tier = obj.optString("tier", "value"),
                        badge = rawBadge,
                        name = obj.optString("name", ""),
                        imageUrl = obj.optString("image_url", ""),
                        benefitLine = obj.optString("benefit_line", ""),
                        benefits = benefits,
                        priceLevel = obj.optString("price_level", "$$"),
                        bestFor = obj.optString("best_for", ""),
                        tradeOff = obj.optString("trade_off", ""),
                        pairsWith = rawPairsWith,
                        affiliateUrl = obj.optString("affiliate_url", ""),
                        merchant = obj.optString("merchant", "Amazon"),
                        active = obj.optBoolean("active", true),
                        lastUpdated = obj.optString("last_updated", "2026-10-31")
                    )
                )
            }
            return result
        }
    }

    private val firestore: FirebaseFirestore? by lazy {
        try {
            val dbIdRes = context.resources.getIdentifier("firestore_database_id", "string", context.packageName)
            val dbId = if (dbIdRes != 0) context.getString(dbIdRes) else "(default)"
            if (dbId.isNotBlank() && dbId != "(default)") {
                Firebase.firestore(dbId)
            } else {
                Firebase.firestore
            }
        } catch (_: Exception) {
            null
        }
    }

    fun loadBundledProducts(): List<TopGearProduct> {
        return try {
            val jsonStr = context.assets.open(ASSET_FILE_NAME).bufferedReader().use { it.readText() }
            parseProductsJson(jsonStr)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun loadCachedOrBundledProducts(): Pair<List<TopGearProduct>, TopGearSyncSource> {
        val bundled = loadBundledProducts()
        val bundledById = bundled.associateBy { it.id }
        val cacheFile = File(context.filesDir, LOCAL_CACHE_FILE_NAME)
        if (cacheFile.exists() && cacheFile.canRead()) {
            try {
                val cachedList = parseProductsJson(cacheFile.readText())
                if (cachedList.isNotEmpty()) {
                    // Merge cached remote overrides over bundled catalog, keeping bundled imageUrl if cache had blank
                    val mergedMap = LinkedHashMap<String, TopGearProduct>()
                    bundled.forEach { mergedMap[it.id] = it }
                    cachedList.forEach { cached ->
                        val fallbackImg = bundledById[cached.id]?.imageUrl ?: ""
                        mergedMap[cached.id] = if (cached.imageUrl.isBlank() && fallbackImg.isNotBlank()) {
                            cached.copy(imageUrl = fallbackImg)
                        } else {
                            cached
                        }
                    }
                    return Pair(mergedMap.values.toList(), TopGearSyncSource.LOCAL_CACHE)
                }
            } catch (_: Exception) {}
        }
        return Pair(bundled, TopGearSyncSource.BUNDLED)
    }

    suspend fun refreshFromRemoteIfAvailable(currentProducts: List<TopGearProduct>): Pair<List<TopGearProduct>, TopGearSyncSource> =
        withContext(Dispatchers.IO) {
            val user = try { FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
            val db = firestore
            if (user == null || db == null) {
                return@withContext Pair(currentProducts, TopGearSyncSource.BUNDLED)
            }

            try {
                val currentById = currentProducts.associateBy { it.id }
                val snapshot = db.collection(FIRESTORE_COLLECTION).get().await()
                if (snapshot != null && !snapshot.isEmpty) {
                    val remoteProducts = snapshot.documents.mapNotNull { doc ->
                        try {
                            val data = doc.data ?: return@mapNotNull null
                            val id = (data["id"] as? String)?.takeIf { it.isNotBlank() } ?: doc.id
                            val category = (data["category"] as? String) ?: return@mapNotNull null
                            val group = (data["group"] as? String) ?: return@mapNotNull null
                            val tier = (data["tier"] as? String) ?: "value"
                            val badge = (data["badge"] as? String)?.takeIf { it.isNotBlank() }
                            val name = (data["name"] as? String) ?: return@mapNotNull null
                            val rawImageUrl = (data["image_url"] as? String) ?: ""
                            val imageUrl = rawImageUrl.ifBlank { currentById[id]?.imageUrl ?: "" }
                            val benefitLine = (data["benefit_line"] as? String) ?: ""
                            val benefitsRaw = (data["benefits"] as? List<*>)?.mapNotNull { it as? String }?.take(3) ?: emptyList()
                            val priceLevel = (data["price_level"] as? String) ?: "$$"
                            val bestFor = (data["best_for"] as? String) ?: ""
                            val tradeOff = (data["trade_off"] as? String) ?: ""
                            val pairsWith = (data["pairs_with"] as? String)?.takeIf { it.isNotBlank() }
                            val affiliateUrl = (data["affiliate_url"] as? String) ?: ""
                            val merchant = (data["merchant"] as? String) ?: "Amazon"
                            val active = (data["active"] as? Boolean) ?: true
                            val lastUpdated = (data["last_updated"] as? String) ?: "2026-10-31"

                            TopGearProduct(
                                id = id,
                                category = category,
                                group = group,
                                tier = tier,
                                badge = badge,
                                name = name,
                                imageUrl = imageUrl,
                                benefitLine = benefitLine,
                                benefits = benefitsRaw,
                                priceLevel = priceLevel,
                                bestFor = bestFor,
                                tradeOff = tradeOff,
                                pairsWith = pairsWith,
                                affiliateUrl = affiliateUrl,
                                merchant = merchant,
                                active = active,
                                lastUpdated = lastUpdated
                            )
                        } catch (_: Exception) {
                            null
                        }
                    }

                    if (remoteProducts.isNotEmpty()) {
                        val mergedMap = LinkedHashMap<String, TopGearProduct>()
                        currentProducts.forEach { mergedMap[it.id] = it }
                        remoteProducts.forEach { mergedMap[it.id] = it }
                        val mergedList = mergedMap.values.toList()
                        saveProductsToLocalCache(mergedList)
                        return@withContext Pair(mergedList, TopGearSyncSource.FIRESTORE_REMOTE)
                    }
                }
            } catch (_: Exception) {
                // Offline or no remote overrides yet; keep local cached / bundled copy
            }
            Pair(currentProducts, TopGearSyncSource.BUNDLED)
        }

    fun buildCatalogState(
        rawProducts: List<TopGearProduct>,
        selectedCategory: String,
        syncSource: TopGearSyncSource,
        isOffline: Boolean,
        resolvedApiImages: Map<String, String> = emptyMap()
    ): TopGearCatalogState {
        // Hide any product where active is false
        val activeProducts = rawProducts.filter { it.active }

        // Show only categories that contain at least one active product
        val populatedCategories = ORDERED_CATEGORIES.filter { cat ->
            cat == "All" || activeProducts.any { it.category.equals(cat, ignoreCase = true) }
        }

        val effectiveCategory = if (selectedCategory in populatedCategories) selectedCategory else "All"

        val filteredProducts = if (effectiveCategory == "All") {
            activeProducts
        } else {
            activeProducts.filter { it.category.equals(effectiveCategory, ignoreCase = true) }
        }

        val groupedMap = LinkedHashMap<String, MutableList<TopGearProduct>>()
        for (product in filteredProducts) {
            groupedMap.getOrPut(product.group) { mutableListOf() }.add(product)
        }

        val groups = groupedMap.map { (groupTitle, items) ->
            val sortedItems = items.sortedBy { it.tierOrder }
            val category = sortedItems.firstOrNull()?.category ?: ""
            TopGearProductGroup(
                groupTitle = groupTitle,
                category = category,
                whyThisMatters = getWhyThisMattersForGroup(groupTitle, category),
                products = sortedItems
            )
        }

        return TopGearCatalogState(
            isLoading = false,
            isOffline = isOffline,
            syncSource = syncSource,
            allActiveProducts = activeProducts,
            availableCategories = populatedCategories,
            selectedCategory = effectiveCategory,
            groups = groups,
            resolvedApiImages = resolvedApiImages,
            errorMessage = null
        )
    }

    private fun saveProductsToLocalCache(products: List<TopGearProduct>) {
        try {
            val arr = JSONArray()
            for (p in products) {
                val obj = JSONObject().apply {
                    put("id", p.id)
                    put("category", p.category)
                    put("group", p.group)
                    put("tier", p.tier)
                    if (p.badge != null) put("badge", p.badge) else put("badge", JSONObject.NULL)
                    put("name", p.name)
                    put("image_url", p.imageUrl)
                    put("benefit_line", p.benefitLine)
                    put("benefits", JSONArray(p.benefits))
                    put("price_level", p.priceLevel)
                    put("best_for", p.bestFor)
                    put("trade_off", p.tradeOff)
                    if (p.pairsWith != null) put("pairs_with", p.pairsWith) else put("pairs_with", JSONObject.NULL)
                    put("affiliate_url", p.affiliateUrl)
                    put("merchant", p.merchant)
                    put("active", p.active)
                    put("last_updated", p.lastUpdated)
                }
                arr.put(obj)
            }
            File(context.filesDir, LOCAL_CACHE_FILE_NAME).writeText(arr.toString(2))
        } catch (_: Exception) {}
    }
}
