package com.myvault.app.ui.main

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.myvault.app.ui.backup.BackupHistoryScreen
import com.myvault.app.ui.backup.BackupSettingsScreen
import com.myvault.app.ui.backup.DriveConnectionScreen
import com.myvault.app.ui.backup.RestoreScreen
import com.myvault.app.ui.components.TypeIcon
import com.myvault.app.ui.components.VaultBackground
import com.myvault.app.ui.components.VaultSurface
import com.myvault.app.ui.items.CategoriesScreen
import com.myvault.app.ui.items.CategoryDetailsScreen
import com.myvault.app.ui.items.FavoritesScreen
import com.myvault.app.ui.items.GeneratorScreen
import com.myvault.app.ui.items.ItemDetailScreen
import com.myvault.app.ui.items.ItemEditScreen
import com.myvault.app.ui.items.SearchScreen
import com.myvault.app.ui.settings.AboutScreen
import com.myvault.app.ui.settings.SecurityRecoveryScreen
import com.myvault.app.ui.settings.SettingsScreen
import com.myvault.app.ui.theme.VaultTheme
import com.myvault.app.vault.ItemType
import kotlinx.coroutines.flow.collectLatest

object Routes {
    const val HOME = "home"
    const val CATEGORIES = "categories"
    const val FAVORITES = "favorites"
    const val SETTINGS = "settings"
    const val SEARCH = "search"
    const val CATEGORY = "category/{type}"
    const val ITEM = "item/{id}"
    const val NEW_ITEM = "new/{type}"
    const val EDIT_ITEM = "edit/{id}"
    const val GENERATOR = "generator"
    const val BACKUP = "backup"
    const val DRIVE = "backup/drive"
    const val BACKUP_HISTORY = "backup/history"
    const val RESTORE = "backup/restore"
    const val SECURITY = "security"
    const val ABOUT = "about"

    fun category(type: ItemType) = "category/${type.name}"
    fun item(id: String) = "item/$id"
    fun newItem(type: ItemType) = "new/${type.name}"
    fun editItem(id: String) = "edit/$id"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Outlined.Home),
    Tab(Routes.CATEGORIES, "Categories", Icons.Outlined.GridView),
    Tab(Routes.FAVORITES, "Favorites", Icons.Rounded.StarBorder),
    Tab(Routes.SETTINGS, "Settings", Icons.Outlined.Settings),
)

/**
 * Holds the view models of one unlocked session. It is cleared when the unlocked UI leaves the
 * composition, which happens the moment the vault locks, so no view model can carry decrypted
 * data, search text or a half-written item across a lock.
 */
private class SessionViewModelOwner(private val activity: ComponentActivity) : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
    override val viewModelStore = ViewModelStore()
    override val defaultViewModelProviderFactory: ViewModelProvider.Factory get() = activity.defaultViewModelProviderFactory
    override val defaultViewModelCreationExtras: CreationExtras get() = activity.defaultViewModelCreationExtras
}

/** Everything shown while the vault is unlocked. */
@Composable
fun MainApp(startAtRestore: Boolean = false, onStartHandled: () -> Unit = {}) {
    val activity = LocalActivity.current as ComponentActivity
    val owner = remember { SessionViewModelOwner(activity) }
    DisposableEffect(owner) {
        onDispose { owner.viewModelStore.clear() }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
        MainScaffold(startAtRestore, onStartHandled)
    }
}

@Composable
private fun MainScaffold(startAtRestore: Boolean, onStartHandled: () -> Unit) {
    val navController = rememberNavController()
    val vault: VaultViewModel = hiltViewModel()
    val snackbar = remember { SnackbarHostState() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route

    LaunchedEffect(Unit) {
        // A newer message replaces the one on screen instead of queueing behind it.
        vault.messages.collectLatest { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(route) {
        // The add and edit forms pin Save to the bottom edge, where a lingering message would cover it.
        if (route == Routes.NEW_ITEM || route == Routes.EDIT_ITEM) snackbar.currentSnackbarData?.dismiss()
    }
    LaunchedEffect(startAtRestore) {
        if (startAtRestore) {
            navController.navigate(Routes.RESTORE)
            onStartHandled()
        }
    }

    VaultBackground(Modifier.testTag("main-app")) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = { if (tabs.any { it.route == route }) BottomBar(navController, route) },
            snackbarHost = {
                SnackbarHost(snackbar) { data ->
                    Snackbar(data, shape = RoundedCornerShape(16.dp), containerColor = MaterialTheme.colorScheme.inverseSurface, contentColor = MaterialTheme.colorScheme.inverseOnSurface)
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                modifier = Modifier.padding(padding).consumeWindowInsets(padding),
                enterTransition = { fadeIn(tween(200)) },
                exitTransition = { fadeOut(tween(120)) },
                popEnterTransition = { fadeIn(tween(200)) },
                popExitTransition = { fadeOut(tween(120)) },
            ) {
                val back: () -> Unit = { navController.popBackStack() }
                val openItem: (String) -> Unit = { navController.navigate(Routes.item(it)) }
                val newItem: (ItemType) -> Unit = { navController.navigate(Routes.newItem(it)) }

                composable(Routes.HOME) {
                    HomeScreen(
                        vault = vault,
                        onSearch = { navController.navigate(Routes.SEARCH) },
                        onCategory = { navController.navigate(Routes.category(it)) },
                        onItem = openItem,
                        onNewItem = newItem,
                        onBackup = { navController.navigate(Routes.BACKUP) },
                        onSecurity = { navController.navigate(Routes.SECURITY) },
                        onAllCategories = { navController.switchTab(Routes.CATEGORIES) },
                        onAllFavorites = { navController.switchTab(Routes.FAVORITES) },
                    )
                }
                composable(Routes.CATEGORIES) {
                    CategoriesScreen(vault, onCategory = { navController.navigate(Routes.category(it)) }, onSearch = { navController.navigate(Routes.SEARCH) })
                }
                composable(Routes.FAVORITES) { FavoritesScreen(vault, onItem = openItem) }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        vault = vault,
                        onSecurity = { navController.navigate(Routes.SECURITY) },
                        onBackup = { navController.navigate(Routes.BACKUP) },
                        onGenerator = { navController.navigate(Routes.GENERATOR) },
                        onAbout = { navController.navigate(Routes.ABOUT) },
                    )
                }
                composable(Routes.SEARCH) { SearchScreen(vault, onBack = back, onItem = openItem) }
                composable(Routes.CATEGORY) { entry ->
                    val type = ItemType.valueOf(entry.arguments?.getString("type") ?: ItemType.PASSWORD.name)
                    CategoryDetailsScreen(vault, type, onBack = back, onItem = openItem, onNew = { newItem(type) })
                }
                composable(Routes.ITEM) { entry ->
                    val id = entry.arguments?.getString("id").orEmpty()
                    ItemDetailScreen(vault, id, onBack = back, onEdit = { navController.navigate(Routes.editItem(id)) })
                }
                composable(Routes.NEW_ITEM) { entry ->
                    val type = ItemType.valueOf(entry.arguments?.getString("type") ?: ItemType.PASSWORD.name)
                    ItemEditScreen(itemId = null, newType = type, onBack = back, onSaved = { id ->
                        navController.popBackStack()
                        navController.navigate(Routes.item(id))
                    })
                }
                composable(Routes.EDIT_ITEM) { entry ->
                    ItemEditScreen(itemId = entry.arguments?.getString("id"), newType = null, onBack = back, onSaved = { back() })
                }
                composable(Routes.GENERATOR) { GeneratorScreen(onBack = back, onCopy = { vault.copy("Password", it, sensitive = true) }) }
                composable(Routes.BACKUP) {
                    BackupSettingsScreen(
                        onBack = back,
                        onDrive = { navController.navigate(Routes.DRIVE) },
                        onHistory = { navController.navigate(Routes.BACKUP_HISTORY) },
                        onRestore = { navController.navigate(Routes.RESTORE) },
                    )
                }
                composable(Routes.DRIVE) { DriveConnectionScreen(onBack = back) }
                composable(Routes.BACKUP_HISTORY) { BackupHistoryScreen(onBack = back) }
                composable(Routes.RESTORE) { RestoreScreen(onBack = back, onDone = { navController.popBackStack(Routes.HOME, inclusive = false) }) }
                composable(Routes.SECURITY) { SecurityRecoveryScreen(onBack = back, onBackup = { navController.navigate(Routes.BACKUP) }) }
                composable(Routes.ABOUT) { AboutScreen(onBack = back) }
            }
        }
    }
}

private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun BottomBar(navController: NavHostController, currentRoute: String?) {
    val palette = VaultTheme.palette
    Surface(color = palette.card) {
        Column {
            HorizontalDivider(thickness = 0.7.dp, color = palette.cardBorder)
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp)) {
                tabs.forEach { tab ->
                    val selected = currentRoute == tab.route
                    val colour = if (selected) palette.accent else MaterialTheme.colorScheme.onSurfaceVariant
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (selected) palette.accent.copy(alpha = 0.12f) else Color.Transparent)
                            .selectable(selected = selected, role = Role.Tab, onClick = { navController.switchTab(tab.route) })
                            .testTag("tab-${tab.route}")
                            .padding(vertical = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(tab.icon, contentDescription = null, tint = colour, modifier = Modifier.size(25.dp))
                        Spacer(Modifier.height(3.dp))
                        Text(tab.label, style = MaterialTheme.typography.labelSmall, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, color = colour)
                    }
                }
            }
        }
    }
}

/** Quick Add: choose what kind of record to create. Every kind is in view at once, one tap away. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TypePickerSheet(onDismiss: () -> Unit, onPicked: (ItemType) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // Open at full height: every kind should be visible without dragging the sheet up.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
    ) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, bottom = 18.dp)) {
            Text("Add to your vault", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(start = 4.dp))
            Text(
                "What would you like to keep safe?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 16.dp),
            )
            ItemType.entries.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { type ->
                        VaultSurface(
                            modifier = Modifier.weight(1f).fillMaxHeight().testTag("pick-${type.name}"),
                            shape = RoundedCornerShape(18.dp),
                            onClick = { onPicked(type) },
                            elevation = 5.dp,
                        ) {
                            Column(Modifier.padding(horizontal = 8.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                TypeIcon(type, size = 42.dp)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    type.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                )
                            }
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
