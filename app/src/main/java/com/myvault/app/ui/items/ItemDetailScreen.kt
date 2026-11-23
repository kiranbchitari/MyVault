package com.myvault.app.ui.items

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myvault.app.ui.components.CardPreview
import com.myvault.app.ui.components.ConfirmDialog
import com.myvault.app.ui.components.CopyButton
import com.myvault.app.ui.components.FormGroup
import com.myvault.app.ui.components.IconTile
import com.myvault.app.ui.components.RowDivider
import com.myvault.app.ui.components.LocalExpectExternalFlow
import com.myvault.app.ui.components.PrimaryButton
import com.myvault.app.ui.components.QuietButton
import com.myvault.app.ui.components.ScreenColumn
import com.myvault.app.ui.components.TagRow
import com.myvault.app.ui.components.TypeIcon
import com.myvault.app.ui.components.VaultCard
import com.myvault.app.ui.main.CopiedMark
import com.myvault.app.ui.main.VaultViewModel
import com.myvault.app.ui.main.formatDay
import com.myvault.app.ui.theme.VaultTheme
import com.myvault.app.vault.AttachmentInfo
import com.myvault.app.vault.FieldKind
import com.myvault.app.vault.ItemField
import com.myvault.app.vault.ItemTemplates
import com.myvault.app.vault.ItemType
import com.myvault.app.vault.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ItemDetailScreen(vault: VaultViewModel, itemId: String, onBack: () -> Unit, onEdit: () -> Unit) {
    val contents by vault.contents.collectAsStateWithLifecycle()
    val item = contents?.items?.firstOrNull { it.id == itemId }
    val attachments = contents?.attachments?.get(itemId).orEmpty()
    val copied by vault.copied.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val expectExternalFlow = LocalExpectExternalFlow.current
    val palette = VaultTheme.palette

    val revealed = remember { mutableStateMapOf<String, Boolean>() }
    var confirmDelete by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<ImageBitmap?>(null) }
    var deleteAttachment by remember { mutableStateOf<AttachmentInfo?>(null) }

    LaunchedEffect(itemId) { vault.markAccessed(itemId) }
    LaunchedEffect(item == null, contents == null) {
        // The item was deleted (or the id is stale): there is nothing to show.
        if (contents != null && item == null) onBack()
    }
    if (item == null) return

    // Showing or copying a secret goes through this gate, which may first ask who is there.
    val guarded = rememberIdentityGuard(vault)

    fun copy(field: ItemField) {
        if (field.kind == FieldKind.SECRET) guarded { vault.copyField(item, field) } else vault.copyField(item, field)
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val picked = withContext(Dispatchers.IO) { readPickedFile(context, uri) }
                when {
                    picked == null -> vault.show("That file could not be read")
                    picked.second.size > VaultRepository.MAX_ATTACHMENT_BYTES -> vault.show("That file is too large. The limit is 10 MB.")
                    else -> vault.addAttachment(itemId, picked.first, context.contentResolver.getType(uri) ?: "image/*", picked.second)
                }
            }
        }
    }

    ScreenColumn(
        title = item.type.titleCase(),
        modifier = Modifier.testTag("item-detail"),
        onBack = onBack,
        actions = {
            IconButton(onClick = { vault.toggleFavorite(item) }, modifier = Modifier.testTag("favorite")) {
                Icon(
                    if (item.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = if (item.favorite) "Remove from favorites" else "Add to favorites",
                    tint = if (item.favorite) palette.favorite else MaterialTheme.colorScheme.onBackground,
                )
            }
            IconButton(onClick = onEdit, modifier = Modifier.testTag("edit")) {
                Icon(Icons.Outlined.Edit, contentDescription = "Edit", tint = palette.accent)
            }
            IconButton(onClick = { confirmDelete = true }, modifier = Modifier.testTag("delete")) {
                Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        },
    ) {
        if (item.type.isCard) {
            CardPreview(
                nickname = item.title,
                network = item.field("network"),
                lastFour = item.field("last4"),
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp),
                holder = item.field("holder"),
                bank = item.field("bank"),
                expiry = item.field("expiry"),
                credit = item.type == ItemType.CREDIT_CARD,
            )
            Spacer(Modifier.height(24.dp))
        } else {
            Row(Modifier.padding(top = 6.dp, bottom = 20.dp, start = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                TypeIcon(item.type, size = 58.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(item.title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
                    Text(
                        if (item.type == ItemType.CUSTOM && item.customTypeName.isNotBlank()) item.customTypeName else item.type.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // The value this record is usually opened for gets one large button of its own.
        item.quickCopyField?.let { main ->
            val done = copied == CopiedMark(item.id, main.key)
            PrimaryButton(
                text = if (done) "Copied" else "Copy ${main.label.lowercase()}",
                onClick = { copy(main) },
                icon = if (done) Icons.Rounded.Check else Icons.Outlined.ContentCopy,
                modifier = Modifier.testTag("copy-main"),
            )
            Spacer(Modifier.height(18.dp))
        }

        val shown = item.fields.filter { it.value.isNotEmpty() }
        if (shown.isNotEmpty()) {
            FormGroup {
                shown.forEachIndexed { index, field ->
                    if (index > 0) RowDivider()
                    val secret = field.kind == FieldKind.SECRET
                    val visible = !secret || revealed[field.key] == true
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClickLabel = "Copy ${field.label.lowercase()}") { copy(field) }
                            .testTag("row-${field.key}")
                            .padding(start = 16.dp, end = 4.dp, top = 9.dp, bottom = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(fieldIcon(field.key, field.kind), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(field.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = if (visible) field.value else "••••••••••",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                fontFamily = if (secret) FontFamily.Monospace else null,
                                fontSize = if (secret) 15.sp else TextUnit.Unspecified,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.testTag("value-${field.key}"),
                            )
                        }
                        if (secret) {
                            IconButton(
                                onClick = { if (visible) revealed[field.key] = false else guarded { revealed[field.key] = true } },
                                modifier = Modifier.testTag("reveal-${field.key}"),
                            ) {
                                Icon(
                                    if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (visible) "Hide ${field.label}" else "Show ${field.label}",
                                    tint = palette.accent,
                                )
                            }
                        }
                        CopyButton(
                            label = "Copy ${field.label.lowercase()}",
                            copied = copied == CopiedMark(item.id, field.key),
                            onClick = { copy(field) },
                            modifier = Modifier.testTag("copy-${field.key}"),
                        )
                    }
                }
            }
            Text(
                "Tap any row to copy it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp, top = 8.dp),
            )
            Spacer(Modifier.height(16.dp))
        }

        if (item.notes.isNotBlank()) {
            FormGroup {
                val copyNotes = { vault.copyValue(item, "notes", "Notes", item.notes, sensitive = false) }
                Row(
                    Modifier.fillMaxWidth().clickable(onClickLabel = "Copy notes", onClick = copyNotes).padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(fieldIcon("notes"), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp).size(24.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f).padding(top = 4.dp)) {
                        Text("Notes", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(2.dp))
                        Text(item.notes, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    }
                    CopyButton("Copy notes", copied == CopiedMark(item.id, "notes"), copyNotes, Modifier.testTag("copy-notes"))
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (item.tags.isNotEmpty()) {
            TagRow(item.tags, Modifier.padding(horizontal = 2.dp))
            Spacer(Modifier.height(16.dp))
        }

        if (ItemTemplates.of(item.type).allowsAttachments) {
            VaultCard {
                Text("Attachments", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "Images are encrypted before they are stored, and shown only inside MyVault.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                attachments.forEach { attachment ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                guarded {
                                    scope.launch {
                                        val bytes = vault.readAttachment(attachment.id)
                                        val bitmap = bytes?.let { withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(it, 0, it.size) } }
                                        if (bitmap != null) viewing = bitmap.asImageBitmap() else vault.show("This attachment can't be displayed")
                                    }
                                }
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconTile(Icons.Outlined.Image, palette.accent, size = 34.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(attachment.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                            Text("${attachment.sizeBytes / 1024} KB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { deleteAttachment = attachment }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete attachment", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                QuietButton("Add an image", {
                    expectExternalFlow()
                    pickImage.launch("image/*")
                })
            }
            Spacer(Modifier.height(16.dp))
        }

        Text(
            "Created ${formatDay(item.createdAt)} · Updated ${formatDay(item.updatedAt)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
        Spacer(Modifier.height(24.dp))
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this item?",
            body = "\"${item.title}\" will be removed from your vault. This can't be undone, except by restoring a backup.",
            confirmLabel = "Delete",
            destructive = true,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                vault.delete(item.id)
            },
        )
    }

    deleteAttachment?.let { attachment ->
        ConfirmDialog(
            title = "Delete attachment?",
            body = "\"${attachment.name}\" will be permanently removed.",
            confirmLabel = "Delete",
            destructive = true,
            onDismiss = { deleteAttachment = null },
            onConfirm = {
                vault.deleteAttachment(attachment.id)
                deleteAttachment = null
            },
        )
    }

    viewing?.let { bitmap ->
        Dialog(onDismissRequest = { viewing = null }) {
            Box(Modifier.clip(RoundedCornerShape(20.dp)).background(palette.card).clickable { viewing = null }.padding(8.dp)) {
                Image(bitmap, contentDescription = "Attachment", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)))
            }
        }
    }
}

/** Reads a picked file fully into memory, refusing anything over the attachment limit. */
private fun readPickedFile(context: Context, uri: Uri): Pair<String, ByteArray>? = runCatching {
    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    } ?: "attachment"
    val limit = VaultRepository.MAX_ATTACHMENT_BYTES + 1
    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(64 * 1024)
        while (buffer.size() < limit) {
            val read = input.read(chunk)
            if (read < 0) break
            buffer.write(chunk, 0, read)
        }
        buffer.toByteArray()
    } ?: return null
    name to bytes
}.getOrNull()
