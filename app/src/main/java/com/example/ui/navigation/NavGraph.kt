
package com.example.ui.navigation

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.*
import com.example.viewmodel.DietaViewModel
import kotlinx.coroutines.launch

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Dieta", Icons.Outlined.RestaurantMenu)
    object MealPlan : Screen("meal_plan", "Jadłospis", Icons.Outlined.CalendarMonth)
    object Documents : Screen("documents", "Dokumenty", Icons.Outlined.PictureAsPdf)
    object AiCoach : Screen("ai_coach", "AI", Icons.Outlined.SmartToy)
    object Recipes : Screen("recipes", "Przepisy", Icons.Outlined.RestaurantMenu)
    object Shopping : Screen("shopping", "Zakupy", Icons.Outlined.ShoppingCart)
    object Progress : Screen("effects", "Postępy", Icons.Outlined.TrendingUp)
    object Sport : Screen("sport", "Aktywność", Icons.Outlined.FitnessCenter)
    object Profile : Screen("profile", "Profil", Icons.Outlined.Person)
}

private const val TABS_ROUTE = "main_tabs"

private val mainTabs = listOf(
    Screen.Dashboard, Screen.MealPlan, Screen.Progress, Screen.Sport,
    Screen.Recipes, Screen.Shopping, Screen.Profile
)

@Composable
fun MangoBottomNav(
    items: List<Screen>,
    currentRoute: String?,
    onNavigate: (Screen) -> Unit
) {
    val reduced = com.example.ui.components.LocalReducedMangoMotion.current
    val colors = MaterialTheme.colorScheme
    Surface(
        color = colors.surface,
        tonalElevation = 0.dp,
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
    ) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().navigationBarsPadding()
                .padding(vertical = 6.dp, horizontal = 2.dp)
        ) {
            val slot = maxWidth / items.size.coerceAtLeast(1)
            val selectedIndex = items.indexOfFirst { it.route == currentRoute }
            val indicator by animateDpAsState(
                targetValue = slot * selectedIndex.coerceAtLeast(0),
                animationSpec = tween(if (reduced) 0 else 220),
                label = "nav_indicator"
            )
            if (selectedIndex >= 0) {
                Box(Modifier.offset(x = indicator, y = 4.dp)
                    .width(slot).height(36.dp).padding(horizontal = 5.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.primary.copy(alpha = 0.13f)))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                items.forEach { screen ->
                    val selected = screen.route == currentRoute
                    val scale by animateFloatAsState(
                        if (selected && !reduced) 1.04f else 1f,
                        animationSpec = tween(if (reduced) 0 else 150), label = "navScale"
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f).scale(scale)
                            .clip(RoundedCornerShape(12.dp))
                            .selectable(selected = selected, role = Role.Tab) { onNavigate(screen) }
                            .heightIn(min = 56.dp).padding(vertical = 4.dp)
                            .testTag("nav_tab_${screen.route}")
                    ) {
                        Icon(screen.icon, screen.title,
                            tint = if (selected) colors.primary else colors.onSurfaceVariant,
                            modifier = Modifier.size(24.dp))
                        Spacer(Modifier.height(5.dp))
                        Text(screen.title, fontSize = 10.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) colors.primary else colors.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
fun MainNavigation(viewModel: DietaViewModel) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { mainTabs.size })
    val reduced = com.example.ui.components.LocalReducedMangoMotion.current
    val keyboard = LocalSoftwareKeyboardController.current
    val currentEntry by navController.currentBackStackEntryAsState()
    val navRoute = currentEntry?.destination?.route
    val selectedTab = mainTabs[pagerState.currentPage.coerceIn(mainTabs.indices)]
    val pendingSharedText by viewModel.pendingSharedRecipeText.collectAsState()

    fun showTab(screen: Screen) {
        val index = mainTabs.indexOf(screen)
        if (index < 0) return
        keyboard?.hide()
        if (navController.currentDestination?.route != TABS_ROUTE) {
            navController.navigate(TABS_ROUTE) {
                popUpTo(navController.graph.findStartDestination().id) { inclusive = false }
                launchSingleTop = true
            }
        }
        scope.launch {
            if (reduced) pagerState.scrollToPage(index)
            else pagerState.animateScrollToPage(index)
        }
    }

    fun showExtra(route: String) {
        keyboard?.hide()
        navController.navigate(route) { launchSingleTop = true }
    }

    LaunchedEffect(pendingSharedText) {
        if (!pendingSharedText.isNullOrBlank()) showTab(Screen.Recipes)
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        bottomBar = {
            MangoBottomNav(
                items = mainTabs,
                currentRoute = if (navRoute == TABS_ROUTE) selectedTab.route else null,
                onNavigate = ::showTab
            )
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = TABS_ROUTE,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding)
        ) {
            composable(TABS_ROUTE) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    key = { mainTabs[it].route }
                ) { page ->
                    when (mainTabs[page]) {
                        Screen.Dashboard -> DashboardScreen(
                            viewModel = viewModel,
                            onNavigateToAiCoach = { showExtra(Screen.AiCoach.route) },
                            onNavigateToRecipes = { showTab(Screen.Recipes) },
                            onNavigateToMealPlan = { showTab(Screen.MealPlan) },
                            onNavigateToBarcodeScanner = { showExtra("barcode_scanner") },
                            onNavigateToProductPhoto = { showExtra("product_photo") },
                            onNavigateToMeasurements = { showExtra("measurements") },
                            onNavigateToDocuments = { showExtra(Screen.Documents.route) },
                            onNavigateToSport = { showTab(Screen.Sport) },
                            onNavigateToEffects = { showTab(Screen.Progress) }
                        )
                        Screen.MealPlan -> MealPlanScreen(viewModel = viewModel)
                        Screen.Progress -> EffectsScreen(
                            viewModel,
                            onBack = { showTab(Screen.Dashboard) },
                            onProfile = { showTab(Screen.Profile) }
                        )
                        Screen.Sport -> SportsScreen(
                            viewModel,
                            onBack = { showTab(Screen.Dashboard) }
                        )
                        Screen.Recipes -> RecipesScreen(
                            viewModel = viewModel,
                            onNavigateHome = { showTab(Screen.Dashboard) }
                        )
                        Screen.Shopping -> ShoppingScreen(viewModel = viewModel)
                        Screen.Profile -> ProfileScreen(
                            viewModel = viewModel,
                            onNavigateToMeasurements = { showExtra("measurements") },
                            onNavigateToAiCoach = { showExtra(Screen.AiCoach.route) },
                            onNavigateToEffects = { showTab(Screen.Progress) }
                        )
                        else -> Unit
                    }
                }
            }
            composable(Screen.Documents.route) {
                DocumentsScreen(viewModel = viewModel, onNavigateHome = {
                    showTab(Screen.Dashboard)
                })
            }
            composable(Screen.AiCoach.route) {
                AiCoachScreen(viewModel = viewModel, onEffects = {
                    showTab(Screen.Progress)
                })
            }
            composable("measurements") {
                MeasurementsScreen(viewModel = viewModel, onBack = {
                    navController.popBackStack()
                })
            }
            composable("product_photo") {
                ProductScannerScreen(viewModel = viewModel,
                    onBack = { navController.popBackStack() }, initialPhotoMode = true)
            }
            composable("barcode_scanner") {
                ProductScannerScreen(viewModel = viewModel,
                    onBack = { navController.popBackStack() })
            }
        }
    }
}
