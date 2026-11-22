package com.myvault.app.ui.items

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myvault.app.ui.components.CircleIconButton
import com.myvault.app.ui.components.EmptyState
import com.myvault.app.ui.components.ItemRow
import com.myvault.app.ui.components.ScreenColumn
import com.myvault.app.ui.components.SearchField
import com.myvault.app.ui.components.SectionHeader
import com.myvault.app.ui.components.TypeIcon
import com.myvault.app.ui.components.VaultSurface
import com.myvault.app.ui.components.icon
import com.myvault.app.ui.main.VaultViewModel
import com.myvault.app.ui.theme.VaultTheme
import com.myvault.app.vault.ItemType
import com.myvault.app.vault.VaultItem
import com.myvault.app.vault.VaultSearch

@Composable
private fun ItemList(items: List<VaultItem>, onItem: (String) -> Unit, modifier: Modifier = Modifier, header: (@Composable () -> Unit)? = null) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(top = 4.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (header != null) item { header() }
        items(items, key = { it.id }) { ItemRow(it, { onItem(it.id) }) }
    }
}

/** A category tile: icon, name, item count and a chevron. */
@Composable
fun CategoryCard(type: ItemType, count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    VaultSurface(modifier = modifier, shape = RoundedCornerShape(20.dp), onClick = onClick, elevation = 8.dp) {
        Row(Modifier.padding(start = 14.dp, end = 8.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                TypeIcon(type, size = 40.dp)
                Spacer(Modifier.height(12.dp))
                Text(type.plural, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(if (count == 1) "1 item" else "$count items", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
fun CategoriesScreen(vault: VaultViewModel, onCategory: (ItemType) -> Unit, onSearch: () -> Unit) {
    val contents by vault.contents.collectAsStateWithLifecycle()
    val items = contents?.items.orEmpty()
    val counts = VaultSearch.countsByType(items)
    ScreenColumn(
        title = "Categories",
        modifier = Modifier.testTag("categories"),
        scroll = false,
        actions = {
            CircleIconButton(Icons.Rounded.Search, "Search", onSearch)
            Spacer(Modifier.width(12.dp))
        },
    ) {
        LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp)) {
            items(ItemType.entries.chunked(2)) { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { type ->
                        CategoryCard(type, counts[type] ?: 0, { onCategory(type) }, Modifier.weight(1f).fillMaxHeight().testTag("category-${type.name}"))
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun CategoryDetailsScreen(vault: VaultViewModel, type: ItemType, onBack: () -> Unit, onItem: (String) -> Unit, onNew: () -> Unit) {
    val contents by vault.contents.collectAsStateWithLifecycle()
    val items = VaultSearch.search(contents?.items.orEmpty(), VaultSearch.Filter(type = type))
    ScreenColumn(
        title = type.plural,
        modifier = Modifier.testTag("category-details"),
        onBack = onBack,
        scroll = false,
        actions = {
            CircleIconButton(Icons.Rounded.Add, "Add ${type.label}", onNew, Modifier.testTag("add-in-category"))
            Spacer(Modifier.width(12.dp))
        },
    ) {
        if (items.isEmpty()) {
            EmptyState(type.icon, "No ${type.plural.lowercase()} yet", "Anything you add here is encrypted before it is saved.", action = "Add ${type.label.lowercase()}", onAction = onNew)
        } else {
            ItemList(items, onItem, Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
fun FavoritesScreen(vault: VaultViewModel, onItem: (String) -> Unit) {
    val contents by vault.contents.collectAsStateWithLifecycle()
    val favorites = VaultSearch.favorites(contents?.items.orEmpty())
    ScreenColumn(title = "Favorites", modifier = Modifier.testTag("favorites"), scroll = false) {
        if (favorites.isEmpty()) {
            EmptyState(Icons.Rounded.StarBorder, "No favorites yet", "Star the items you reach for most and they'll be waiting here.")
        } else {
            ItemList(favorites, onItem, Modifier.padding(top = 4.dp))
        }
    }
}

private enum class SearchSort(val label: String) { RELEVANCE("Relevance"), NAME("Name"), UPDATED("Recently updated") }

@Composable
fun SearchScreen(vault: VaultViewModel, onBack: () -> Unit, onItem: (String) -> Unit) {
    val contents by vault.contents.collectAsStateWithLifecycle()
    // Plain remember: the query is never written to saved state and is gone when the vault locks.
    var query by remember { mutableStateOf("") }
    var type by remember { mutableStateOf<ItemType?>(null) }
    var favoritesOnly by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(SearchSort.RELEVANCE) }
    var sortMenu by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val all = contents?.items.orEmpty()
    val filtering = query.isNotBlank() || type != null || favoritesOnly
    val results = remember(all, query, type, favoritesOnly, sort) {
        val matched = VaultSearch.search(all, VaultSearch.Filter(query, type, favoritesOnly))
        when (sort) {
            SearchSort.RELEVANCE -> matched
            SearchSort.NAME -> matched.sortedBy { it.title.lowercase() }
            SearchSort.UPDATED -> matched.sortedByDescending { it.updatedAt }
        }
    }

    LaunchedEffect(Unit) { focus.requestFocus() }

    ScreenColumn(title = "Search", modifier = Modifier.testTag("search"), onBack = onBack, scroll = false) {
        SearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Titles, tags and fields",
            fieldModifier = Modifier.focusRequester(focus).testTag("search-field"),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Search),
        )
        Spacer(Modifier.height(14.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip("All", selected = type == null && !favoritesOnly) {
                type = null
                favoritesOnly = false
            }
            FilterChip("Favorites", selected = favoritesOnly, icon = Icons.Rounded.StarBorder) { favoritesOnly = !favoritesOnly }
            ItemType.entries.forEach { option ->
                FilterChip(option.plural, selected = type == option, icon = option.icon) { type = if (type == option) null else option }
            }
        }
        when {
            !filtering -> {
                val recent = VaultSearch.recentlyAccessed(all, limit = 8)
                if (recent.isEmpty()) {
                    EmptyState(Icons.Rounded.Search, "Find anything", "Search by name, category, tag, or the contents of any non-secret field.")
                } else {
                    ItemList(recent, onItem, header = { SectionHeader("Recently opened") })
                }
            }
            results.isEmpty() -> EmptyState(Icons.Rounded.SearchOff, "No matches", "Nothing in your vault matches that. Passwords and other secret values are never searched.")
            else -> ItemList(
                results, onItem,
                header = {
                    Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 4.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (results.size == 1) "1 result" else "${results.size} results",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f),
                        )
                        Box {
                            Row(
                                Modifier.clip(RoundedCornerShape(8.dp)).clickable { sortMenu = true }.testTag("sort").padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Sort: ${sort.label}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                                Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(20.dp))
                            }
                            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }, containerColor = VaultTheme.palette.card, shape = RoundedCornerShape(14.dp)) {
                                SearchSort.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.label, fontWeight = if (option == sort) FontWeight.SemiBold else FontWeight.Normal) },
                                        onClick = {
                                            sort = option
                                            sortMenu = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, icon: ImageVector? = null, onClick: () -> Unit) {
    val palette = VaultTheme.palette
    val content = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) palette.accent else palette.card)
            .clickable(onClick = onClick)
            .testTag("filter-$label")
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, color = content, maxLines = 1)
    }
}
