package com.myvault.app.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myvault.app.backup.BackupHealth
import com.myvault.app.ui.components.CircleIconButton
import com.myvault.app.ui.components.IconTile
import com.myvault.app.ui.components.ItemRow
import com.myvault.app.ui.components.PrimaryButton
import com.myvault.app.ui.components.SectionHeader
import com.myvault.app.ui.components.VaultLogo
import com.myvault.app.ui.components.VaultSurface
import com.myvault.app.ui.items.CategoryCard
import com.myvault.app.ui.items.rememberQuickCopy
import com.myvault.app.ui.theme.VaultTheme
import com.myvault.app.vault.ItemType
import com.myvault.app.vault.VaultSearch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val shortDate = DateTimeFormatter.ofPattern("d MMM")

fun formatDay(epochMs: Long): String = shortDate.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

@Composable
fun HomeScreen(
    vault: VaultViewModel,
    onSearch: () -> Unit,
    onCategory: (ItemType) -> Unit,
    onItem: (String) -> Unit,
    onNewItem: (ItemType) -> Unit,
    onBackup: () -> Unit,
    onSecurity: () -> Unit,
    onAllCategories: () -> Unit,
    onAllFavorites: () -> Unit,
) {
    val contents by vault.contents.collectAsStateWithLifecycle()
    val backup by vault.backup.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    val quickCopy = rememberQuickCopy(vault)
    val copied by vault.copied.collectAsStateWithLifecycle()
    val items = contents?.items
    val palette = VaultTheme.palette

    Box(Modifier.fillMaxSize().testTag("home"), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = 640.dp).fillMaxSize().testTag("home-list"),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 28.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VaultLogo(size = 52.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("MyVault", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
                        Text("Your private & encrypted vault", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    CircleIconButton(Icons.Rounded.Search, "Search", onSearch, Modifier.testTag("search-bar"))
                    Spacer(Modifier.width(10.dp))
                    CircleIconButton(Icons.Rounded.Add, "Add item", { picking = true }, Modifier.testTag("quick-add"))
                }
                Spacer(Modifier.height(20.dp))

                // Security status
                VaultSurface(modifier = Modifier.fillMaxWidth().testTag("status-items"), shape = RoundedCornerShape(22.dp), onClick = onSecurity) {
                    Row(
                        Modifier
                            .background(Brush.horizontalGradient(listOf(palette.success.copy(alpha = 0.16f), palette.accent.copy(alpha = 0.10f))))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconTile(Icons.Rounded.Lock, palette.success, size = 50.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Vault is Secure", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                "Encrypted on this device. Only your passphrase opens it.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(palette.card)
                                .clickable(onClick = vault::lock)
                                .testTag("lock")
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Lock, contentDescription = "Lock vault", tint = palette.success, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Lock", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                // Last backup status
                val good = backup.health == BackupHealth.GOOD
                VaultSurface(modifier = Modifier.fillMaxWidth().testTag("status-backup"), shape = RoundedCornerShape(18.dp), onClick = onBackup, elevation = 6.dp) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconTile(if (good) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff, if (good) palette.success else palette.warning, size = 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(backup.health.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                backup.lastSuccessAt?.let { "Last backup ${formatDay(it)}" } ?: "Tap to set up backups",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                    }
                }

                if ((contents?.unreadable ?: 0) > 0) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f)).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "${contents?.unreadable} stored record(s) are damaged and could not be opened. Restore from a backup to recover them.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            if (items == null) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 64.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = palette.accent, strokeWidth = 2.dp)
                    }
                }
            } else {
                val counts = VaultSearch.countsByType(items)
                val favorites = VaultSearch.favorites(items)
                val recent = VaultSearch.recentlyAccessed(items)

                if (items.isEmpty()) {
                    item {
                        Spacer(Modifier.height(14.dp))
                        VaultSurface(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(60.dp).clip(RoundedCornerShape(50)).background(palette.accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.Shield, contentDescription = null, tint = palette.accent, modifier = Modifier.size(30.dp))
                                }
                                Spacer(Modifier.height(12.dp))
                                Text("Your vault is empty", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Add a password, a card, a note or anything else you want to keep safe and find quickly.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(Modifier.height(16.dp))
                                PrimaryButton("Add your first item", { picking = true })
                            }
                        }
                    }
                }

                item {
                    SectionHeader("Categories", detail = if (items.size == 1) "1 item" else "${items.size} items")
                }
                items(ItemType.entries.chunked(2), key = { row -> "cat-${row.first().name}" }) { row ->
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { type ->
                            CategoryCard(type, counts[type] ?: 0, { onCategory(type) }, Modifier.weight(1f).fillMaxHeight().testTag("shortcut-${type.name}"))
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }

                if (favorites.isNotEmpty()) {
                    item { SectionHeader("Favorites", action = if (favorites.size > 3) "See all" else null, onAction = onAllFavorites) }
                    items(favorites.take(3), key = { "fav-${it.id}" }) {
                        ItemRow(it, { onItem(it.id) }, Modifier.padding(bottom = 10.dp), onCopy = { quickCopy(it) }, copied = copied?.itemId == it.id)
                    }
                }

                if (recent.isNotEmpty()) {
                    item { SectionHeader("Recently opened") }
                    items(recent, key = { "recent-${it.id}" }) {
                        ItemRow(it, { onItem(it.id) }, Modifier.padding(bottom = 10.dp), onCopy = { quickCopy(it) }, copied = copied?.itemId == it.id)
                    }
                }
            }
        }
    }

    if (picking) {
        TypePickerSheet(onDismiss = { picking = false }, onPicked = {
            picking = false
            onNewItem(it)
        })
    }
}
