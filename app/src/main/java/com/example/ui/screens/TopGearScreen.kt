package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.example.data.repository.AmazonCreatorsApiClient
import com.example.data.repository.TopGearAnalyticsManager
import com.example.data.repository.TopGearCatalogState
import com.example.data.repository.TopGearProduct
import com.example.data.repository.TopGearProductGroup
import com.example.data.repository.TopGearRepository
import com.example.data.repository.TopGearSyncSource
import com.example.ui.theme.HubBlue
import com.example.ui.theme.LaunchGreen
import com.example.ui.theme.SolarGold
import com.example.util.ConsoleHapticManager
import com.example.util.NetworkConnectivityMonitor
import com.example.util.rememberIsDeviceOnline
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopGearScreen(
    onNavigateToGmaxFeature: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val repository = remember(context) { TopGearRepository(context) }

    val isOnlineLive by rememberIsDeviceOnline()
    var retryTrigger by remember { mutableIntStateOf(0) }
    LaunchedEffect(retryTrigger) {
        if (retryTrigger > 0) {
            NetworkConnectivityMonitor.triggerImmediateRecheck(context)
        }
    }
    val isDeviceOnline = isOnlineLive

    var selectedCategory by rememberSaveable { mutableStateOf("All") }
    var rawProducts by remember { mutableStateOf<List<TopGearProduct>>(emptyList()) }
    var syncSource by remember { mutableStateOf(TopGearSyncSource.BUNDLED) }
    var isLoading by remember { mutableStateOf(true) }
    var resolvedApiImages by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var selectedDetailProduct by remember { mutableStateOf<TopGearProduct?>(null) }

    val compareExpandedGroups = remember { mutableStateMapOf<String, Boolean>() }
    val analyticsEnabled by TopGearAnalyticsManager.isEnabledFlow.collectAsState()

    LaunchedEffect(Unit) {
        TopGearAnalyticsManager.isAnalyticsEnabled(context)
    }

    LaunchedEffect(retryTrigger, isDeviceOnline) {
        if (rawProducts.isEmpty()) {
            isLoading = true
            val (initialList, initialSource) = repository.loadCachedOrBundledProducts()
            rawProducts = initialList
            syncSource = initialSource
            isLoading = false
        }
        if (isDeviceOnline) {
            val (refreshedList, refreshedSource) = repository.refreshFromRemoteIfAvailable(rawProducts)
            rawProducts = refreshedList
            syncSource = refreshedSource
            val apiImages = AmazonCreatorsApiClient.resolveProductImagesInMemory(refreshedList)
            if (apiImages.isNotEmpty()) {
                resolvedApiImages = apiImages
            }
        }
    }

    val catalogState: TopGearCatalogState = remember(
        rawProducts,
        selectedCategory,
        syncSource,
        isDeviceOnline,
        resolvedApiImages,
        isLoading
    ) {
        if (isLoading && rawProducts.isEmpty()) {
            TopGearCatalogState(isLoading = true, isOffline = !isDeviceOnline)
        } else {
            repository.buildCatalogState(
                rawProducts = rawProducts,
                selectedCategory = selectedCategory,
                syncSource = syncSource,
                isOffline = !isDeviceOnline,
                resolvedApiImages = resolvedApiImages
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("top_gear_screen")
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 12.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            // 1 & 2. Editorial Header + Always-Visible Amazon Associate Disclosure
            item(key = "top_gear_header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    Text(
                        text = com.example.util.tr("Top Gear", "Top Gear"),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp,
                            color = MaterialTheme.colorScheme.onBackground
                        ),
                        modifier = Modifier.testTag("top_gear_title")
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = com.example.util.tr("Hand-picked gear for mobile gamers", "Equipo seleccionado para gamers móviles"),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        ),
                        modifier = Modifier.testTag("top_gear_subtitle")
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    // Always-visible one-line disclosure under the header (never hidden behind a tap)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                            width = 1.dp,
                            color = HubBlue.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("amazon_associate_disclosure")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = com.example.util.tr("Affiliate Disclosure", "Aviso de Afiliado"),
                                tint = HubBlue,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = com.example.util.tr(
                                    "As an Amazon Associate I earn from qualifying purchases.",
                                    "Como Afiliado de Amazon, obtengo ingresos por las compras adscritas que cumplen los requisitos."
                                ),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                                    fontWeight = FontWeight.Medium
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // 3. Horizontally scrolling category chips (only categories with >= 1 active product)
            item(key = "top_gear_categories") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .testTag("top_gear_category_chips"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    catalogState.availableCategories.forEach { category ->
                        val isSelected = catalogState.selectedCategory.equals(category, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (isSelected) HubBlue.copy(alpha = 0.18f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) HubBlue
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(50)
                                )
                                .clickable {
                                    ConsoleHapticManager.playSelectClick(context)
                                    selectedCategory = category
                                }
                                .focusable()
                                .padding(horizontal = 16.dp, vertical = 9.dp)
                                .testTag("top_gear_chip_${category.lowercase().replace(" ", "_")}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = com.example.util.AppStrings.autoTranslateDynamic(category, com.example.util.currentAppLanguage()),
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    color = if (isSelected) HubBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp
                                )
                            )
                        }
                    }
                }
            }

            // Online-Only Gate: Top Gear requires an active internet connection
            if (catalogState.isOffline) {
                item(key = "top_gear_offline_banner") {
                    OfflineCatalogBanner(
                        onRetry = {
                            ConsoleHapticManager.playSelectClick(context)
                            retryTrigger++
                        },
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                }
            } else if (catalogState.isLoading) {
                items(3, key = { "skeleton_$it" }) {
                    TopGearSkeletonGroupCard(
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            } else if (catalogState.groups.isEmpty()) {
                // Empty-category fallback state
                item(key = "top_gear_empty_category") {
                    EmptyCategoryStateCard(
                        category = catalogState.selectedCategory,
                        onResetToAll = { selectedCategory = "All" },
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            } else {
                // 4. Vertical list of curated product groups
                items(
                    items = catalogState.groups,
                    key = { it.groupTitle }
                ) { group ->
                    val isCompareOpen = compareExpandedGroups[group.groupTitle] == true
                    ProductGroupSection(
                        group = group,
                        resolvedApiImages = catalogState.resolvedApiImages,
                        isCompareExpanded = isCompareOpen,
                        onToggleCompare = {
                            ConsoleHapticManager.playSelectClick(context)
                            val next = !isCompareOpen
                            compareExpandedGroups[group.groupTitle] = next
                            group.products.firstOrNull()?.let { first ->
                                TopGearAnalyticsManager.logAnonymousTap(
                                    context,
                                    first.id,
                                    first.tier,
                                    "compare_toggle"
                                )
                            }
                        },
                        onSelectProductForDetails = { product ->
                            ConsoleHapticManager.playSelectClick(context)
                            TopGearAnalyticsManager.logAnonymousTap(
                                context,
                                product.id,
                                product.tier,
                                "card_detail_open"
                            )
                            selectedDetailProduct = product
                        },
                        onOpenAffiliateLink = { product ->
                            openAffiliateUrlExternally(context, product)
                        },
                        onClickPairsWith = { pairsWithText ->
                            ConsoleHapticManager.playSelectClick(context)
                            onNavigateToGmaxFeature(pairsWithText)
                        }
                    )
                }
            }

            // Privacy-first Anonymous Tap Logging Opt-Out Footer Card
            item(key = "top_gear_privacy_footer") {
                AnonymousAnalyticsToggleCard(
                    isEnabled = analyticsEnabled,
                    onToggle = { enabled ->
                        TopGearAnalyticsManager.setAnalyticsEnabled(context, enabled)
                    },
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        }

        // Product Detail ModalBottomSheet
        val activeDetailProduct = selectedDetailProduct
        if (activeDetailProduct != null) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { selectedDetailProduct = null },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface,
                scrimColor = Color.Black.copy(alpha = 0.65f)
            ) {
                ProductDetailBottomSheetContent(
                    product = activeDetailProduct,
                    isSingleProductGroup = catalogState.groups
                        .find { it.groupTitle == activeDetailProduct.group }
                        ?.products?.size == 1,
                    resolvedImageUrl = activeDetailProduct.imageUrl.ifBlank {
                        catalogState.resolvedApiImages[activeDetailProduct.id] ?: ""
                    },
                    onOpenAmazon = {
                        openAffiliateUrlExternally(context, activeDetailProduct)
                    },
                    onClickPairsWith = { pairsWithText ->
                        selectedDetailProduct = null
                        onNavigateToGmaxFeature(pairsWithText)
                    },
                    onClose = {
                        coroutineScope.launch {
                            sheetState.hide()
                            selectedDetailProduct = null
                        }
                    }
                )
            }
        }
    }
}

/**
 * Renders one product group according to the exact Tier Logic:
 * - 3 products: horizontally swipeable row of Budget, Best Value (centered, Best Pick badge, slightly larger card with accent border), and Premium + Compare toggle.
 * - 2 products: 2 cards with their real tier labels, no empty 3rd slot, plus Compare toggle.
 * - 1 product: one full-width featured card with "Editor's Pick" badge instead of a tier label, no tier row, no Compare toggle, no empty slots.
 */
@Composable
private fun ProductGroupSection(
    group: TopGearProductGroup,
    resolvedApiImages: Map<String, String>,
    isCompareExpanded: Boolean,
    onToggleCompare: () -> Unit,
    onSelectProductForDetails: (TopGearProduct) -> Unit,
    onOpenAffiliateLink: (TopGearProduct) -> Unit,
    onClickPairsWith: (String) -> Unit
) {
    val count = group.products.size
    val showCompareToggle = count >= 2

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("top_gear_group_${group.groupTitle.lowercase().replace(" ", "_")}")
    ) {
        // Group Header + Why This Matters + Compare Toggle (only when count >= 2)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = com.example.util.AppStrings.autoTranslateDynamic(group.groupTitle, com.example.util.currentAppLanguage()),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 20.sp
                    )
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = com.example.util.AppStrings.autoTranslateDynamic(group.whyThisMatters, com.example.util.currentAppLanguage()),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 19.sp
                    )
                )
            }

            if (showCompareToggle) {
                Spacer(modifier = Modifier.width(12.dp))
                Surface(
                    color = if (isCompareExpanded) HubBlue.copy(alpha = 0.2f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isCompareExpanded) HubBlue
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { onToggleCompare() }
                        .testTag("compare_toggle_${group.groupTitle.lowercase().replace(" ", "_")}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CompareArrows,
                            contentDescription = com.example.util.tr("Compare Tiers", "Comparar Niveles"),
                            tint = if (isCompareExpanded) HubBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (isCompareExpanded) com.example.util.tr("Hide Compare", "Ocultar") else com.example.util.tr("Compare", "Comparar"),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isCompareExpanded) HubBlue else MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
            }
        }

        // Side-by-Side Comparison Table (when Compare is toggled on for 2 or 3 products)
        AnimatedVisibility(visible = showCompareToggle && isCompareExpanded) {
            SideBySideTierCompareTable(
                products = group.products,
                onSelectProduct = onSelectProductForDetails,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (count) {
            1 -> {
                // Single-version product: One full-width featured card with "Editor's Pick" badge instead of a tier label
                val singleProduct = group.products.first()
                FeaturedSingleProductCard(
                    product = singleProduct,
                    resolvedImageUrl = singleProduct.imageUrl.ifBlank {
                        resolvedApiImages[singleProduct.id] ?: ""
                    },
                    onCardClick = { onSelectProductForDetails(singleProduct) },
                    onOpenAmazon = { onOpenAffiliateLink(singleProduct) },
                    onClickPairsWith = onClickPairsWith,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                )
            }
            2 -> {
                // 2 products: Show the two cards with their real tier labels, no empty third slot
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(group.products, key = { it.id }) { product ->
                        val isCenterBestPick = product.tier.equals("value", ignoreCase = true) ||
                            product.badge.equals("Best Pick", ignoreCase = true)
                        TierProductCard(
                            product = product,
                            isHighlightedBestPick = isCenterBestPick,
                            resolvedImageUrl = product.imageUrl.ifBlank {
                                resolvedApiImages[product.id] ?: ""
                            },
                            onCardClick = { onSelectProductForDetails(product) },
                            onOpenAmazon = { onOpenAffiliateLink(product) },
                            onClickPairsWith = onClickPairsWith,
                            modifier = Modifier.width(292.dp)
                        )
                    }
                }
            }
            else -> {
                // 3 products: Horizontally swipeable row ordered Budget (0), Best Value (1 - center), Premium (2)
                // Best Value is shown in the center with a "Best Pick" badge and a slightly larger card with an accent border
                val orderedThree = remember(group.products) {
                    val budget = group.products.find { it.tier.equals("budget", ignoreCase = true) }
                    val value = group.products.find { it.tier.equals("value", ignoreCase = true) }
                    val premium = group.products.find { it.tier.equals("premium", ignoreCase = true) }
                    listOfNotNull(budget, value, premium).ifEmpty { group.products }
                }
                val rowListState = rememberLazyListState()

                LazyRow(
                    state = rowListState,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(orderedThree, key = { it.id }) { product ->
                        val isCenterBestValue = product.tier.equals("value", ignoreCase = true)
                        TierProductCard(
                            product = product,
                            isHighlightedBestPick = isCenterBestValue,
                            resolvedImageUrl = product.imageUrl.ifBlank {
                                resolvedApiImages[product.id] ?: ""
                            },
                            onCardClick = { onSelectProductForDetails(product) },
                            onOpenAmazon = { onOpenAffiliateLink(product) },
                            onClickPairsWith = onClickPairsWith,
                            modifier = Modifier.width(if (isCenterBestValue) 304.dp else 280.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Full-width featured card for 1-product groups.
 * Shows an "Editor's Pick" badge instead of a tier label, no tier row, no Compare toggle,
 * and no wording implying cheaper or pricier versions exist.
 */
@Composable
private fun FeaturedSingleProductCard(
    product: TopGearProduct,
    resolvedImageUrl: String,
    onCardClick: () -> Unit,
    onOpenAmazon: () -> Unit,
    onClickPairsWith: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val badgeText = product.badge?.takeIf { it.isNotBlank() } ?: "Editor's Pick"

    Card(
        modifier = modifier
            .widthIn(max = 640.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable { onCardClick() }
            .focusable()
            .testTag("product_card_${product.id}"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        ),
        border = BorderStroke(1.2.dp, HubBlue.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Top Badge Row: Editor's Pick + Price Level ($, $$, $$$)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = SolarGold.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, SolarGold.copy(alpha = 0.65f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = badgeText,
                            tint = SolarGold,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = SolarGold
                            )
                        )
                    }
                }

                PriceLevelPill(priceLevel = product.priceLevel)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Image + Title + One-Line Benefit
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProductArtworkBox(
                    product = product,
                    resolvedImageUrl = resolvedImageUrl,
                    modifier = Modifier.size(96.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = product.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 21.sp
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = com.example.util.AppStrings.autoTranslateDynamic(product.benefitLine, com.example.util.currentAppLanguage()),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 19.sp
                        ),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Up to 3 short benefit bullets
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                product.benefits.take(3).forEach { bullet ->
                    BenefitBulletRow(text = bullet)
                }
            }

            // "Pairs with" interactive GMAX feature chip
            if (!product.pairsWith.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                PairsWithFeatureChip(
                    pairsWithLabel = product.pairsWith,
                    onClick = { onClickPairsWith(product.pairsWith) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Button ("View on Amazon" or friendly unavailable fallback)
            AmazonExternalActionButton(
                product = product,
                isPrimaryAccent = true,
                onOpenAmazon = onOpenAmazon
            )
        }
    }
}

/**
 * Multi-tier Product Card for 2-product and 3-product groups.
 * When `isHighlightedBestPick` is true (Best Value center card), renders with a "Best Pick" badge,
 * slightly taller/padded framing, and a bright neon accent border.
 */
@Composable
private fun TierProductCard(
    product: TopGearProduct,
    isHighlightedBestPick: Boolean,
    resolvedImageUrl: String,
    onCardClick: () -> Unit,
    onOpenAmazon: () -> Unit,
    onClickPairsWith: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeLang = com.example.util.currentAppLanguage()
    val tierLabel = com.example.util.AppStrings.autoTranslateDynamic(product.displayTierLabel(), activeLang)
    val badgeLabel = (if (isHighlightedBestPick) {
        product.badge?.takeIf { it.isNotBlank() } ?: "Best Pick"
    } else {
        product.badge?.takeIf { it.isNotBlank() }
    })?.let { com.example.util.AppStrings.autoTranslateDynamic(it, activeLang) }

    val borderStroke = if (isHighlightedBestPick) {
        BorderStroke(2.dp, HubBlue)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.32f))
    }

    val containerBg = if (isHighlightedBestPick) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    }

    Card(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable { onCardClick() }
            .focusable()
            .testTag("product_card_${product.id}"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = containerBg),
        border = borderStroke
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = 16.dp,
                vertical = if (isHighlightedBestPick) 18.dp else 15.dp
            )
        ) {
            // Top Row: Tier label + optional "Best Pick" badge + Price Level
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = if (isHighlightedBestPick) HubBlue.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isHighlightedBestPick) HubBlue
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        )
                    ) {
                        Text(
                            text = tierLabel,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isHighlightedBestPick) HubBlue
                                else MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    if (badgeLabel != null) {
                        Surface(
                            color = LaunchGreen.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, LaunchGreen.copy(alpha = 0.65f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = badgeLabel,
                                    tint = LaunchGreen,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = badgeLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = LaunchGreen
                                    )
                                )
                            }
                        }
                    }
                }

                PriceLevelPill(priceLevel = product.priceLevel)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Product Image (tightly fitted to the actual artwork proportions)
            ProductArtworkBox(
                product = product,
                resolvedImageUrl = resolvedImageUrl,
                modifier = Modifier
                    .size(if (isHighlightedBestPick) 118.dp else 108.dp)
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Product Short Name
            Text(
                text = product.name,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = if (isHighlightedBestPick) 17.sp else 16.sp,
                    lineHeight = 21.sp
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(5.dp))

            // One-Line Benefit
            Text(
                text = com.example.util.AppStrings.autoTranslateDynamic(product.benefitLine, activeLang),
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Up to 3 Short Benefit Bullets
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                product.benefits.take(3).forEach { bullet ->
                    BenefitBulletRow(text = bullet)
                }
            }

            if (!product.pairsWith.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                PairsWithFeatureChip(
                    pairsWithLabel = product.pairsWith,
                    onClick = { onClickPairsWith(product.pairsWith) }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            AmazonExternalActionButton(
                product = product,
                isPrimaryAccent = isHighlightedBestPick,
                onOpenAmazon = onOpenAmazon
            )
        }
    }
}

/**
 * Loads the product image from `image_url` (or in-memory resolved Amazon Creators API URL)
 * without permanently storing or cropping images on disk, wrapping the frame tightly to the
 * actual product artwork.
 */
@Composable
private fun ProductArtworkBox(
    product: TopGearProduct,
    resolvedImageUrl: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (resolvedImageUrl.isNotBlank()) Color.White else Color(0xFF101926))
            .border(
                width = 1.dp,
                color = HubBlue.copy(alpha = 0.28f),
                shape = RoundedCornerShape(14.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (resolvedImageUrl.isNotBlank()) {
            SubcomposeAsyncImage(
                model = resolvedImageUrl,
                contentDescription = product.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
                loading = {
                    CuratedGearPlaceholderIllustration(category = product.category, group = product.group)
                },
                error = {
                    CuratedGearPlaceholderIllustration(category = product.category, group = product.group)
                }
            )
        } else {
            CuratedGearPlaceholderIllustration(category = product.category, group = product.group)
        }
    }
}

@Composable
private fun CuratedGearPlaceholderIllustration(
    category: String,
    group: String
) {
    val icon: ImageVector = when {
        category.equals("Cooling", ignoreCase = true) -> Icons.Default.AcUnit
        group.contains("Trigger", ignoreCase = true) -> Icons.Default.TouchApp
        category.equals("Controllers", ignoreCase = true) -> Icons.Default.SportsEsports
        category.equals("Finger Sleeves", ignoreCase = true) -> Icons.Default.TouchApp
        category.equals("Power", ignoreCase = true) -> Icons.Default.BatteryChargingFull
        category.equals("Audio", ignoreCase = true) -> Icons.Default.Headphones
        group.contains("Capture", ignoreCase = true) -> Icons.Default.Videocam
        else -> Icons.Default.Devices
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(HubBlue.copy(alpha = 0.14f))
                .border(1.dp, HubBlue.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = category,
                tint = HubBlue,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = category.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = HubBlue.copy(alpha = 0.85f),
                fontSize = 10.sp
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun PriceLevelPill(priceLevel: String) {
    val normalized = priceLevel.ifBlank { "$$" }
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Text(
            text = normalized,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                color = LaunchGreen
            ),
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun BenefitBulletRow(text: String) {
    val activeLang = com.example.util.currentAppLanguage()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = HubBlue,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(14.dp)
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = com.example.util.AppStrings.autoTranslateDynamic(text, activeLang),
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                lineHeight = 17.sp
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PairsWithFeatureChip(
    pairsWithLabel: String,
    onClick: () -> Unit
) {
    val activeLang = com.example.util.currentAppLanguage()
    val chipIcon = when {
        pairsWithLabel.contains("temperature", ignoreCase = true) -> Icons.Default.Thermostat
        pairsWithLabel.contains("Console", ignoreCase = true) -> Icons.Default.SportsEsports
        pairsWithLabel.contains("Touch", ignoreCase = true) || pairsWithLabel.contains("Crosshair", ignoreCase = true) -> Icons.Default.TouchApp
        pairsWithLabel.contains("Recorder", ignoreCase = true) -> Icons.Default.Videocam
        pairsWithLabel.contains("Battery", ignoreCase = true) -> Icons.Default.BatteryChargingFull
        else -> Icons.Default.Devices
    }

    Surface(
        color = HubBlue.copy(alpha = 0.12f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, HubBlue.copy(alpha = 0.38f)),
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .testTag("pairs_with_chip_${pairsWithLabel.lowercase().replace(" ", "_")}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = chipIcon,
                contentDescription = pairsWithLabel,
                tint = HubBlue,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = com.example.util.AppStrings.autoTranslateDynamic(pairsWithLabel, activeLang),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = HubBlue
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AmazonExternalActionButton(
    product: TopGearProduct,
    isPrimaryAccent: Boolean,
    onOpenAmazon: () -> Unit
) {
    if (!product.isAvailable) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("unavailable_badge_${product.id}")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = com.example.util.tr("Unavailable", "No disponible"),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = com.example.util.tr("This product may be unavailable", "Este producto puede no estar disponible"),
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }
        return
    }

    Button(
        onClick = onOpenAmazon,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isPrimaryAccent) HubBlue else MaterialTheme.colorScheme.surface,
            contentColor = if (isPrimaryAccent) Color(0xFF041019) else HubBlue
        ),
        border = if (isPrimaryAccent) null else BorderStroke(1.2.dp, HubBlue.copy(alpha = 0.7f)),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 11.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("view_on_amazon_${product.id}")
    ) {
        Text(
            text = com.example.util.tr("View on Amazon", "Ver en Amazon"),
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.ExtraBold
            )
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = com.example.util.tr("Opens in external browser", "Abre en navegador externo"),
            modifier = Modifier.size(15.dp)
        )
    }
}

/**
 * Side-by-side comparison table for 2 or 3 tiers in a product group.
 * Compares: Price Level, Main Benefit, Best For, and Trade-off.
 */
@Composable
private fun SideBySideTierCompareTable(
    products: List<TopGearProduct>,
    onSelectProduct: (TopGearProduct) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.testTag("tier_compare_table"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        border = BorderStroke(1.dp, HubBlue.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = com.example.util.tr("Side-by-Side Tier Comparison", "Comparación de Niveles Lado a Lado"),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = HubBlue
                )
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Column Headers (Tier + Product Name)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                products.forEach { product ->
                    val isBestPick = product.tier.equals("value", ignoreCase = true)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isBestPick) HubBlue.copy(alpha = 0.14f)
                                else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                            )
                            .clickable { onSelectProduct(product) }
                            .padding(8.dp)
                    ) {
                        Text(
                            text = com.example.util.AppStrings.autoTranslateDynamic(product.displayTierLabel(), com.example.util.currentAppLanguage()),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isBestPick) HubBlue else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = product.name,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            Spacer(modifier = Modifier.height(8.dp))

            CompareRowSection(
                label = com.example.util.tr("Price Level", "Nivel de Precio"),
                values = products.map { it.priceLevel },
                highlightColor = LaunchGreen
            )
            Spacer(modifier = Modifier.height(8.dp))
            CompareRowSection(
                label = com.example.util.tr("Main Benefit", "Beneficio Principal"),
                values = products.map { com.example.util.AppStrings.autoTranslateDynamic(it.benefitLine, com.example.util.currentAppLanguage()) }
            )
            Spacer(modifier = Modifier.height(8.dp))
            CompareRowSection(
                label = com.example.util.tr("Best For", "Ideal Para"),
                values = products.map { com.example.util.AppStrings.autoTranslateDynamic(it.bestFor, com.example.util.currentAppLanguage()) }
            )
            Spacer(modifier = Modifier.height(8.dp))
            CompareRowSection(
                label = com.example.util.tr("Trade-off", "Compromiso"),
                values = products.map { com.example.util.AppStrings.autoTranslateDynamic(it.tradeOff, com.example.util.currentAppLanguage()) }
            )
        }
    }
}

@Composable
private fun CompareRowSection(
    label: String,
    values: List<String>,
    highlightColor: Color? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                letterSpacing = 0.6.sp
            )
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            values.forEach { value ->
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = highlightColor ?: MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (highlightColor != null) FontWeight.ExtraBold else FontWeight.Normal,
                        lineHeight = 16.sp
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Bottom Sheet opened when tapping any product card.
 * Shows full benefits, "Best for", honest "Trade-off", "Pairs with" chip, and "View on Amazon" button.
 */
@Composable
private fun ProductDetailBottomSheetContent(
    product: TopGearProduct,
    isSingleProductGroup: Boolean,
    resolvedImageUrl: String,
    onOpenAmazon: () -> Unit,
    onClickPairsWith: (String) -> Unit,
    onClose: () -> Unit
) {
    val activeLang = com.example.util.currentAppLanguage()
    val badgeOrTierText = com.example.util.AppStrings.autoTranslateDynamic(
        if (isSingleProductGroup) {
            product.badge?.takeIf { it.isNotBlank() } ?: "Editor's Pick"
        } else {
            buildString {
                append(product.displayTierLabel())
                if (!product.badge.isNullOrBlank()) {
                    append(" • ")
                    append(product.badge)
                }
            }
        },
        activeLang
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp)
            .padding(bottom = 28.dp)
            .testTag("product_detail_bottom_sheet")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = HubBlue.copy(alpha = 0.16f),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, HubBlue.copy(alpha = 0.5f))
            ) {
                Text(
                    text = badgeOrTierText,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = HubBlue
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
            PriceLevelPill(priceLevel = product.priceLevel)
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProductArtworkBox(
                product = product,
                resolvedImageUrl = resolvedImageUrl,
                modifier = Modifier.size(84.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 19.sp
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = com.example.util.AppStrings.autoTranslateDynamic(product.benefitLine, activeLang),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = com.example.util.tr("Key Benefits", "Beneficios Clave"),
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            product.benefits.forEach { benefit ->
                BenefitBulletRow(text = benefit)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Best For Box
        Surface(
            color = LaunchGreen.copy(alpha = 0.1f),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, LaunchGreen.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = com.example.util.tr("BEST FOR", "IDEAL PARA"),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = LaunchGreen,
                        letterSpacing = 0.6.sp
                    )
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = com.example.util.AppStrings.autoTranslateDynamic(product.bestFor, activeLang),
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Honest Trade-off Box
        Surface(
            color = SolarGold.copy(alpha = 0.1f),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, SolarGold.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = com.example.util.tr("TRADE-OFF", "COMPROMISO"),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = SolarGold,
                        letterSpacing = 0.6.sp
                    )
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = com.example.util.AppStrings.autoTranslateDynamic(product.tradeOff, activeLang),
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        }

        if (!product.pairsWith.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            PairsWithFeatureChip(
                pairsWithLabel = product.pairsWith,
                onClick = { onClickPairsWith(product.pairsWith) }
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        AmazonExternalActionButton(
            product = product,
            isPrimaryAccent = true,
            onOpenAmazon = onOpenAmazon
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onClose,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(com.example.util.tr("Close", "Cerrar"))
        }
    }
}

@Composable
private fun OfflineCatalogBanner(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, SolarGold.copy(alpha = 0.45f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("top_gear_offline_banner")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = com.example.util.tr("Offline Mode", "Modo sin conexión"),
                    tint = SolarGold,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = com.example.util.tr(
                        "You're offline. Showing locally cached Top Gear picks.",
                        "Estás sin conexión. Mostrando selecciones locales de Top Gear."
                    ),
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            OutlinedButton(
                onClick = onRetry,
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag("top_gear_retry_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = com.example.util.tr("Retry", "Reintentar"),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = com.example.util.tr("Retry", "Reintentar"),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
private fun EmptyCategoryStateCard(
    category: String,
    onResetToAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("top_gear_empty_category_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = com.example.util.AppStrings.autoTranslateDynamic(
                    "No active gear in \"$category\" right now",
                    com.example.util.currentAppLanguage()
                ),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = com.example.util.tr(
                    "Try browsing all categories to see our current curated mobile gaming picks.",
                    "Intenta explorar todas las categorías para ver nuestras selecciones de gaming móvil."
                ),
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onResetToAll,
                colors = ButtonDefaults.buttonColors(
                    containerColor = HubBlue,
                    contentColor = Color(0xFF041019)
                ),
                shape = RoundedCornerShape(50)
            ) {
                Text(com.example.util.tr("Show All Gear", "Ver Todo el Equipo"), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TopGearSkeletonGroupCard(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "top_gear_skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeleton_alpha"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .width(160.dp)
                .height(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(16.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
        )
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
        )
    }
}

@Composable
private fun AnonymousAnalyticsToggleCard(
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("top_gear_analytics_toggle_card")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = com.example.util.tr("Anonymous Gear Tap Logging", "Registro Anónimo de Toques"),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = com.example.util.tr(
                        "Logs only product ID and tier (no personal data) to see which tiers perform best.",
                        "Registra solo ID de producto y nivel (sin datos personales) para mejorar las recomendaciones."
                    ),
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF041019),
                    checkedTrackColor = HubBlue
                ),
                modifier = Modifier.testTag("top_gear_analytics_switch")
            )
        }
    }
}

/**
 * Opens the product's Amazon affiliate URL strictly in the external browser (never inside an in-app WebView)
 * and logs an anonymous tap event (product ID + tier only).
 */
private fun openAffiliateUrlExternally(context: Context, product: TopGearProduct) {
    if (!product.isAvailable) {
        Toast.makeText(context, "This product may be unavailable", Toast.LENGTH_SHORT).show()
        return
    }
    TopGearAnalyticsManager.logAnonymousTap(context, product.id, product.tier, "affiliate_open")
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(product.affiliateUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "Could not open external browser", Toast.LENGTH_SHORT).show()
    }
}
