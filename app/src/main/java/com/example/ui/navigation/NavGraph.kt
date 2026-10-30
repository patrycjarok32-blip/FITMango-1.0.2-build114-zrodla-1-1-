package com.example.ui.navigation

import com.example.ui.components.FitMangoIcon as Icon

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import com.example.ui.theme.*
import com.example.viewmodel.DietaViewModel

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

@Composable
fun MangoBottomNav(
    items: List<Screen>,
    currentRoute: String?,
    onNavigate: (Screen) -> Unit
) {
    val reduced = com.example.ui.components.LocalReducedMangoMotion.current
    Surface(
        color = MutedSurface1,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = WarmBrown.copy(alpha = 0.4f),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            )
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 6.dp, horizontal = 2.dp)) {
            val slot = maxWidth / items.size.coerceAtLeast(1)
            val selectedIndex = items.indexOfFirst { it.route == currentRoute }
            val indicator by animateDpAsState(targetValue = slot * selectedIndex.coerceAtLeast(0),
                animationSpec = tween(if (reduced) 0 else 220), label = "nav_indicator")
            if (selectedIndex >= 0) Box(Modifier.offset(x = indicator, y = 4.dp).width(slot).height(36.dp)
                .padding(horizontal = 5.dp).clip(RoundedCornerShape(20.dp)).background(JuicyOrange.copy(alpha = .15f)))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            items.forEach { screen ->
                val isSelected = currentRoute == screen.route
                val scale by animateFloatAsState(
                    targetValue = if (isSelected && !reduced) 1.04f else 1f,
                    animationSpec = tween(if (reduced) 0 else 150),
                    label = "navScale"
                )
                val iconColor by animateColorAsState(
                    targetValue = if (isSelected) MangoYellow else GoldAccent,
                    animationSpec = tween(if (reduced) 0 else 150), label = "navIcon"
                )
                val labelColor by animateColorAsState(
                    targetValue = if (isSelected) MangoYellow else Cream.copy(alpha = 0.6f),
                    animationSpec = tween(if (reduced) 0 else 150), label = "navLabel"
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .scale(scale)
                        .clip(RoundedCornerShape(12.dp))
                        .selectable(selected = isSelected, role = Role.Tab, onClick = { onNavigate(screen) })
                        .heightIn(min = 56.dp)
                        .padding(vertical = 4.dp)
                        .testTag("nav_tab_${screen.route}")
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.Transparent)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = screen.icon,
                            contentDescription = screen.title,
                            tint = iconColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = screen.title,
                        color = labelColor,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        }
    }
}

@Composable
fun MainNavigation(viewModel: DietaViewModel) {
    val reduced = com.example.ui.components.LocalReducedMangoMotion.current
    val navController = rememberNavController()
    val keyboard = LocalSoftwareKeyboardController.current
    val pendingSharedText by viewModel.pendingSharedRecipeText.collectAsState()

    LaunchedEffect(pendingSharedText) {
        if (!pendingSharedText.isNullOrBlank()) {
            val currentRoute = navController.currentBackStackEntry?.destination?.route
            if (currentRoute != Screen.Recipes.route) {
                navController.navigate(Screen.Recipes.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }

    // Keep the bottom bar focused on the three core areas without duplicating
    // the external workout planner: diet, progress and imported activity.
    // Recipes, documents, AI coach and shopping remain available from existing
    // dashboard/profile entry points.
    val items = listOf(
        Screen.Dashboard,
        Screen.MealPlan,
        Screen.Progress,
        Screen.Sport,
        Screen.Recipes,
        Screen.Shopping,
        Screen.Profile
    )

    Scaffold(
        modifier = Modifier.imePadding(),
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route
            MangoBottomNav(
                items = items,
                currentRoute = currentRoute,
                onNavigate = { screen ->
                    keyboard?.hide()
                    if (currentRoute != screen.route) {
                        navController.navigate(screen.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = screen != Screen.Dashboard
                        }
                    }
                }
            )
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            enterTransition = {
                val direction = tabTransitionDirection(initialState.destination.route, targetState.destination.route)
                slideInHorizontally(tween(if (reduced) 0 else 240)) { width -> if (reduced) 0 else direction * width / 8 } + fadeIn(tween(if (reduced) 0 else 200))
            },
            exitTransition = {
                val direction = tabTransitionDirection(initialState.destination.route, targetState.destination.route)
                slideOutHorizontally(tween(if (reduced) 0 else 240)) { width -> if (reduced) 0 else -direction * width / 8 } + fadeOut(tween(if (reduced) 0 else 160))
            },
            popEnterTransition = {
                val direction = tabTransitionDirection(initialState.destination.route, targetState.destination.route, -1)
                slideInHorizontally(tween(if (reduced) 0 else 240)) { width -> if (reduced) 0 else direction * width / 8 } + fadeIn(tween(if (reduced) 0 else 200))
            },
            popExitTransition = {
                val direction = tabTransitionDirection(initialState.destination.route, targetState.destination.route, -1)
                slideOutHorizontally(tween(if (reduced) 0 else 240)) { width -> if (reduced) 0 else -direction * width / 8 } + fadeOut(tween(if (reduced) 0 else 160))
            }
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    viewModel = viewModel,
                    onNavigateToAiCoach = { navController.navigate(Screen.AiCoach.route) },
                    onNavigateToRecipes = { navController.navigate(Screen.Recipes.route) },
                    onNavigateToMealPlan = { navController.navigate(Screen.MealPlan.route) },
                    onNavigateToBarcodeScanner = { navController.navigate("barcode_scanner") },
                    onNavigateToProductPhoto = { navController.navigate("product_photo") },
                    onNavigateToMeasurements = { navController.navigate("measurements") },
                    onNavigateToDocuments = { navController.navigate(Screen.Documents.route) },
                    onNavigateToSport = { navController.navigate("sport") },
                    onNavigateToEffects = { navController.navigate("effects") }
                )
            }
            composable(Screen.MealPlan.route) {
                MealPlanScreen(viewModel = viewModel)
            }
            composable(Screen.Documents.route) {
                DocumentsScreen(viewModel = viewModel, onNavigateHome = {
                    keyboard?.hide()
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(navController.graph.findStartDestination().id)
                        launchSingleTop = true
                    }
                })
            }
            composable(Screen.AiCoach.route) {
                AiCoachScreen(viewModel = viewModel, onEffects = { navController.navigate("effects") })
            }
            composable(Screen.Recipes.route) {
                RecipesScreen(viewModel = viewModel, onNavigateHome = {
                    keyboard?.hide()
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(navController.graph.findStartDestination().id)
                        launchSingleTop = true
                    }
                })
            }
            composable(Screen.Shopping.route) {
                ShoppingScreen(viewModel = viewModel)
            }
            composable(Screen.Profile.route) {
                ProfileScreen(
                    viewModel = viewModel,
                    onNavigateToMeasurements = { navController.navigate("measurements") },
                    onNavigateToAiCoach = { navController.navigate(Screen.AiCoach.route) },
                    onNavigateToEffects = { navController.navigate("effects") }
                )
            }
            composable("sport") {
                SportsScreen(viewModel, onBack = { navController.popBackStack() })
            }
            composable("effects") {
                EffectsScreen(viewModel, onBack = { navController.popBackStack() }, onProfile = { navController.navigate(Screen.Profile.route) })
            }
            composable("measurements") {
                MeasurementsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("product_photo") {
                ProductScannerScreen(viewModel = viewModel, onBack = { navController.popBackStack() }, initialPhotoMode = true)
            }
            composable("barcode_scanner") {
                ProductScannerScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }
        }
    }
}

// Match movement to the order of the bottom tabs; auxiliary screens follow push/back.
private fun tabTransitionDirection(from: String?, to: String?, fallback: Int = 1): Int {
    val tabs = listOf("dashboard", "meal_plan", "documents", "ai_coach", "recipes", "shopping", "profile")
    val fromIndex = tabs.indexOf(from)
    val toIndex = tabs.indexOf(to)
    return if (fromIndex >= 0 && toIndex >= 0 && fromIndex != toIndex) {
        if (toIndex > fromIndex) 1 else -1
    } else fallback
}
