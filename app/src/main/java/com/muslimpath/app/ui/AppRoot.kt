package com.muslimpath.app.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.muslimpath.app.AppViewModel
import com.muslimpath.app.ui.screens.AccountScreen
import com.muslimpath.app.ui.screens.AskQuestionScreen
import com.muslimpath.app.ui.screens.CategoryScreen
import com.muslimpath.app.ui.screens.DocScreen
import com.muslimpath.app.ui.screens.HistoryScreen
import com.muslimpath.app.ui.screens.HomeScreen
import com.muslimpath.app.ui.screens.LearnScreen
import com.muslimpath.app.ui.screens.LessonScreen
import com.muslimpath.app.ui.screens.NotificationSettingsScreen
import com.muslimpath.app.ui.screens.OnboardingScreen
import com.muslimpath.app.ui.screens.ProfileScreen
import com.muslimpath.app.ui.screens.QuestionsScreen
import com.muslimpath.app.ui.screens.RouteScreen
import com.muslimpath.app.ui.screens.SavedScreen
import com.muslimpath.app.ui.screens.SuraScreen
import com.muslimpath.app.ui.screens.QuranBookmarksScreen
import com.muslimpath.app.ui.screens.QuranListScreen
import com.muslimpath.app.ui.screens.ReciterScreen
import com.muslimpath.app.ui.screens.PrayerTimesScreen
import com.muslimpath.app.ui.screens.PrayerCityScreen
import com.muslimpath.app.ui.screens.QiblaScreen

enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Главная", Icons.Outlined.Home),
    Learn("learn", "Обучение", Icons.Outlined.AutoStories),
    Quran("quran", "Коран", Icons.AutoMirrored.Outlined.MenuBook),
    Prayer("prayer", "Намаз", Icons.Outlined.Schedule),
    Profile("profile", "Профиль", Icons.Outlined.Person),
}

/** Navigation helpers shared by screens. */
class Nav(private val nav: NavHostController) {
    fun back() = nav.popBackStack()
    fun lesson(id: String) = nav.navigate("lesson/$id")
    fun route(id: String) = nav.navigate("route/$id")
    fun category(id: String) = nav.navigate("category/$id")
    fun ask(lessonId: String? = null) = nav.navigate("ask" + (lessonId?.let { "?lesson=$it" } ?: ""))
    fun questions() = nav.navigate("questions")
    fun saved() = nav.navigate("saved")
    fun history() = nav.navigate("history")
    fun notifications() = nav.navigate("notifications")
    fun account() = nav.navigate("account")
    fun doc(id: String) = nav.navigate("doc/$id")
    fun sura(n: Int, ayah: Int = 1) = nav.navigate("sura/$n?ayah=$ayah")
    fun quranBookmarks() = nav.navigate("quranBookmarks")
    fun quran() = tab(Tab.Quran)
    fun reciters() = nav.navigate("reciters")
    fun prayer() = tab(Tab.Prayer)
    fun prayerCity() = nav.navigate("prayerCity")
    fun qibla() = nav.navigate("qibla")
    fun tab(tab: Tab) = nav.navigate(tab.route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun AppRoot(vm: AppViewModel = viewModel(), openRequest: String? = null, onOpenHandled: () -> Unit = {}) {
    val state by vm.state.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val nav = remember(navController) { Nav(navController) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.messageShown() }
    }

    if (!state.onboardingDone) {
        OnboardingScreen(vm.content, onFinish = vm::finishOnboarding)
        return
    }

    // Opened from a prayer notification.
    LaunchedEffect(openRequest) {
        if (openRequest == "prayer") { nav.prayer(); onOpenHandled() }
    }

    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val showBar = Tab.entries.any { t -> destination?.route == t.route }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showBar) androidx.compose.material3.Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
            ) { NavigationBar(containerColor = Color.Transparent, windowInsets = WindowInsets(0)) {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = destination?.hierarchy?.any { it.route == tab.route } == true,
                        onClick = { nav.tab(tab) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            } }
        },
    ) { padding ->
        NavHost(navController, startDestination = Tab.Home.route, modifier = Modifier.padding(bottom = padding.calculateBottomPadding())) {
            composable(Tab.Home.route) { HomeScreen(vm, state, nav) }
            composable(Tab.Learn.route) { LearnScreen(vm, state, nav) }
            composable(Tab.Quran.route) { QuranListScreen(vm, state, nav) }
            composable(Tab.Prayer.route) { PrayerTimesScreen(vm, state, nav) }
            composable(Tab.Profile.route) { ProfileScreen(vm, state, nav) }

            composable("route/{id}") { RouteScreen(vm, state, nav, it.arguments?.getString("id").orEmpty()) }
            composable("category/{id}") { CategoryScreen(vm, state, nav, it.arguments?.getString("id").orEmpty()) }
            composable("lesson/{id}") { LessonScreen(vm, state, nav, it.arguments?.getString("id").orEmpty()) }
            composable(
                "ask?lesson={lesson}",
                arguments = listOf(navArgument("lesson") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) { AskQuestionScreen(vm, nav, it.arguments?.getString("lesson")) }
            composable("questions") { QuestionsScreen(vm, state, nav) }
            composable("saved") { SavedScreen(vm, state, nav) }
            composable("history") { HistoryScreen(vm, state, nav) }
            composable("notifications") { NotificationSettingsScreen(vm, state, nav) }
            composable("account") { AccountScreen(vm, nav) }
            composable("doc/{id}") { DocScreen(nav, it.arguments?.getString("id").orEmpty()) }
            composable(
                "sura/{n}?ayah={ayah}",
                arguments = listOf(
                    navArgument("n") { type = NavType.IntType },
                    navArgument("ayah") { type = NavType.IntType; defaultValue = 1 },
                ),
            ) { SuraScreen(vm, state, nav, it.arguments?.getInt("n") ?: 1, it.arguments?.getInt("ayah") ?: 1) }
            composable("quranBookmarks") { QuranBookmarksScreen(vm, state, nav) }
            composable("reciters") { ReciterScreen(vm, state, nav) }
            composable("prayerCity") { PrayerCityScreen(vm, nav) }
            composable("qibla") { QiblaScreen(state, nav) }
        }
    }
}
