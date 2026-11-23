package com.myvault.app.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.StickyNote2
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Payment
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myvault.app.ui.theme.VaultTheme
import com.myvault.app.vault.ItemType
import com.myvault.app.vault.VaultItem

/** Called on every text edit, so typing counts as activity for the inactivity lock. */
val LocalUserActivity = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * Call just before opening a system screen (file picker, Google consent) so returning from it
 * does not find the vault locked.
 */
val LocalExpectExternalFlow = staticCompositionLocalOf<() -> Unit> { {} }

val ItemType.icon: ImageVector
    get() = when (this) {
        ItemType.PASSWORD -> Icons.Rounded.Key
        ItemType.APP_PIN -> Icons.Rounded.GridView
        ItemType.BANK_ACCOUNT -> Icons.Rounded.AccountBalance
        ItemType.CREDIT_CARD -> Icons.Rounded.CreditCard
        ItemType.DEBIT_CARD -> Icons.Rounded.Payment
        ItemType.WIFI -> Icons.Rounded.Wifi
        ItemType.IDENTITY -> Icons.Rounded.Badge
        ItemType.SECURE_NOTE -> Icons.AutoMirrored.Rounded.StickyNote2
        ItemType.DEVELOPER_SECRET -> Icons.Rounded.Code
        ItemType.CUSTOM -> Icons.Rounded.Layers
    }

/** One solid system-style colour per category. */
fun ItemType.tint(): Color = when (this) {
    ItemType.PASSWORD -> Color(0xFF1F7AFF)
    ItemType.APP_PIN -> Color(0xFF8B5CF6)
    ItemType.BANK_ACCOUNT -> Color(0xFF34C759)
    ItemType.CREDIT_CARD -> Color(0xFF3D6BFF)
    ItemType.DEBIT_CARD -> Color(0xFF1FA8E8)
    ItemType.WIFI -> Color(0xFF2D9CFF)
    ItemType.IDENTITY -> Color(0xFFFF9F0A)
    ItemType.SECURE_NOTE -> Color(0xFFFF4F6D)
    ItemType.DEVELOPER_SECRET -> Color(0xFF30B94D)
    ItemType.CUSTOM -> Color(0xFF8E8E93)
}

/** A solid rounded tile with a white glyph, like an iOS Settings icon. */
@Composable
fun IconTile(icon: ImageVector, color: Color, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(Brush.verticalGradient(listOf(lerp(color, Color.White, 0.16f), color))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.56f))
    }
}

@Composable
fun TypeIcon(type: ItemType, modifier: Modifier = Modifier, size: Dp = 44.dp) = IconTile(type.icon, type.tint(), modifier, size)

/** A round floating button for header actions. */
@Composable
fun CircleIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    VaultSurface(modifier = modifier.size(42.dp), shape = CircleShape, onClick = onClick, elevation = 6.dp) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
fun VaultTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(VaultIcons.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(30.dp))
            }
        } else {
            Spacer(Modifier.width(14.dp))
        }
        Text(
            title,
            style = if (onBack != null) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
        )
        actions()
    }
}

/** Screen body used inside the main scaffold: top bar, then scrolling content capped in width. */
@Composable
fun ScreenColumn(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    scroll: Boolean = true,
    floating: @Composable BoxScope.() -> Unit = {},
    /** Pinned under the scrolling content, and kept above the keyboard. */
    bottomBar: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxSize()) {
            VaultTopBar(title, onBack, actions)
            val body = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 18.dp)
            Column(if (scroll) body.verticalScroll(rememberScrollState()) else body, content = content)
            if (bottomBar != null) Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp), content = bottomBar)
        }
        floating()
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}, detail: String? = null) {
    Row(modifier.fillMaxWidth().padding(top = 24.dp, bottom = 12.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
        if (detail != null) Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) {
            Text(
                action,
                style = MaterialTheme.typography.titleSmall,
                color = VaultTheme.palette.accent,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

/**
 * A round copy button. For a couple of seconds after its value is copied it turns into a green
 * tick, which is the confirmation people look for.
 */
@Composable
fun CopyButton(label: String, copied: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = VaultTheme.palette
    val tint = if (copied) palette.success else palette.accent
    Box(
        modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(tint.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
            Crossfade(copied, label = "copied") { done ->
                Icon(
                    if (done) Icons.Rounded.Check else Icons.Outlined.ContentCopy,
                    contentDescription = if (done) "$label, copied" else label,
                    tint = tint,
                    modifier = Modifier.size(if (done) 21.dp else 18.dp),
                )
            }
        }
    }
}

/**
 * One record in a list. With [onCopy], records that have a main value (a password, PIN, key…)
 * get a copy button, so the value can be taken without opening the record.
 */
@Composable
fun ItemRow(item: VaultItem, onClick: () -> Unit, modifier: Modifier = Modifier, onCopy: (() -> Unit)? = null, copied: Boolean = false) {
    val quick = if (onCopy != null) item.quickCopyField else null
    VaultSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().testTag("item-${item.title}"),
        shape = RoundedCornerShape(18.dp),
        elevation = 6.dp,
    ) {
        Row(
            Modifier.heightIn(min = 72.dp).padding(start = 14.dp, end = if (quick != null) 6.dp else 14.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TypeIcon(item.type, size = 42.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val subtitle = item.subtitle.ifEmpty { if (item.type == ItemType.CUSTOM) item.customTypeName else item.type.label }
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (item.favorite) {
                Icon(Icons.Rounded.Star, contentDescription = "Favorite", tint = VaultTheme.palette.favorite, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
            }
            if (quick != null && onCopy != null) {
                CopyButton("Copy ${quick.label.lowercase()}", copied, onCopy, Modifier.testTag("quick-copy-${item.title}"))
            } else {
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Column(modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(76.dp).clip(CircleShape).background(VaultTheme.palette.accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = VaultTheme.palette.accent, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            PrimaryButton(action, onAction, modifier = Modifier.widthIn(max = 260.dp))
        }
    }
}

/** A hairline between rows of a grouped card, inset past the leading icon. */
@Composable
fun RowDivider(startIndent: Dp = 54.dp) {
    HorizontalDivider(Modifier.padding(start = startIndent), thickness = 0.7.dp, color = VaultTheme.palette.cardBorder)
}

/** A grouped, inset list: the container for [FormRow]s and detail rows. */
@Composable
fun FormGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    VaultSurface(modifier = modifier.fillMaxWidth()) {
        Column(content = content)
    }
}

/** One editable row of a grouped form: leading icon, small label, and the value typed in place. */
@Composable
fun FormRow(
    icon: ImageVector,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1,
    monospace: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    val userActivity = LocalUserActivity.current
    val palette = VaultTheme.palette
    // The field's own text and cursor. When the caller hands back something other than what was
    // typed (digits filtered, a slash added to a date), the cursor goes to the end of the new
    // text; left where it was, the next keystroke would land in the middle of it.
    var typed by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    val field = if (typed.text == value) typed else TextFieldValue(value, TextRange(value.length))
    Row(
        modifier.fillMaxWidth().padding(start = 16.dp, end = if (trailing != null) 4.dp else 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = if (singleLine) 0.dp else 4.dp).size(24.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            BasicTextField(
                value = field,
                onValueChange = { new ->
                    userActivity()
                    typed = new
                    if (new.text != value) onValueChange(new.text)
                },
                modifier = fieldModifier.fillMaxWidth().heightIn(min = 24.dp),
                singleLine = singleLine,
                minLines = minLines,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    fontFamily = if (monospace) FontFamily.Monospace else null,
                ),
                cursorBrush = SolidColor(palette.accent),
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                visualTransformation = visualTransformation,
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline, maxLines = 1)
                        }
                        inner()
                    }
                },
            )
        }
        if (trailing != null) trailing()
    }
}

/** One-tap answers for a field. Tapping the chosen one again clears it. */
@Composable
fun SuggestionChips(options: List<String>, selected: String, onPick: (String) -> Unit, modifier: Modifier = Modifier, tagPrefix: String = "suggest") {
    val palette = VaultTheme.palette
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val chosen = option.equals(selected.trim(), ignoreCase = true)
            Text(
                option,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Medium,
                color = if (chosen) Color.White else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (chosen) palette.accent else palette.fill)
                    .selectable(selected = chosen, role = Role.RadioButton, onClick = { onPick(if (chosen) "" else option) })
                    .testTag("$tagPrefix-$option")
                    .padding(horizontal = 13.dp, vertical = 7.dp),
            )
        }
    }
}

/** A rounded, filled search field in the iOS style. */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val userActivity = LocalUserActivity.current
    val palette = VaultTheme.palette
    Row(
        modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(14.dp)).background(palette.fill).padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = value,
            onValueChange = {
                userActivity()
                onValueChange(it)
            },
            modifier = fieldModifier.weight(1f),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(palette.accent),
            keyboardOptions = keyboardOptions,
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    inner()
                }
            },
        )
        if (value.isNotEmpty()) {
            IconButton(onClick = { onValueChange("") }) {
                Icon(Icons.Rounded.Cancel, contentDescription = "Clear search", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** A segmented control: mutually exclusive options in one rounded track. */
@Composable
fun <T> ChoiceChips(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    tagPrefix: String = "choice",
    enabled: Boolean = true,
) {
    val palette = VaultTheme.palette
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(palette.fill).padding(3.dp)) {
        options.forEach { option ->
            val isSelected = option == selected
            val shape = RoundedCornerShape(9.dp)
            Box(
                Modifier
                    .weight(1f)
                    .then(if (isSelected && !palette.isDark) Modifier.shadow(3.dp, shape, ambientColor = Color(0x22000000), spotColor = Color(0x22000000)) else Modifier)
                    .clip(shape)
                    .background(
                        when {
                            !isSelected -> Color.Transparent
                            palette.isDark -> Color(0xFF636366)
                            else -> Color.White
                        },
                    )
                    .selectable(selected = isSelected, enabled = enabled, role = Role.RadioButton, onClick = { onSelected(option) })
                    .testTag("$tagPrefix-$option")
                    .padding(vertical = 9.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun VaultSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val palette = VaultTheme.palette
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedTrackColor = palette.accent,
            checkedThumbColor = Color.White,
            checkedBorderColor = Color.Transparent,
            uncheckedTrackColor = if (palette.isDark) Color(0xFF39393D) else Color(0xFFE4E4EA),
            uncheckedThumbColor = Color.White,
            uncheckedBorderColor = Color.Transparent,
            disabledUncheckedTrackColor = palette.fill,
            disabledUncheckedThumbColor = Color.White.copy(alpha = if (palette.isDark) 0.4f else 1f),
            disabledUncheckedBorderColor = Color.Transparent,
            disabledCheckedTrackColor = palette.accent.copy(alpha = 0.4f),
            disabledCheckedThumbColor = Color.White,
        ),
    )
}

/** A tappable row inside a card: optional icon tile, title, detail, and a chevron or control. */
@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconColor: Color = VaultTheme.palette.accent,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            IconTile(icon, iconColor, size = 32.dp)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when {
            trailing != null -> {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
            onClick != null -> Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagRow(tags: List<String>, modifier: Modifier = Modifier, onRemove: ((String) -> Unit)? = null) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tags.forEach { tag ->
            Row(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(VaultTheme.palette.fill)
                    .then(if (onRemove != null) Modifier.clickable { onRemove(tag) } else Modifier)
                    .padding(horizontal = 11.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(tag, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                if (onRemove != null) {
                    Spacer(Modifier.width(6.dp))
                    Text("✕", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("confirm")) {
                Text(confirmLabel, fontWeight = FontWeight.SemiBold, color = if (destructive) MaterialTheme.colorScheme.error else VaultTheme.palette.accent)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = VaultTheme.palette.accent) } },
        containerColor = VaultTheme.palette.card,
        shape = RoundedCornerShape(22.dp),
    )
}

/**
 * Asks for one or two passphrases. With [confirmNew] the dialog collects a new passphrase twice
 * and refuses to submit until they match.
 */
@Composable
fun PassphraseDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onSubmit: (current: String, new: String) -> Unit,
    onDismiss: () -> Unit,
    askCurrent: Boolean = true,
    currentLabel: String = "Master passphrase",
    confirmNew: Boolean = false,
    newLabel: String = "New passphrase",
    minNewLength: Int = 12,
    error: String? = null,
    working: Boolean = false,
) {
    // Plain remember: passphrases never reach saved instance state.
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    val newOk = !confirmNew || (new.length >= minNewLength && new == repeat)
    val ready = (!askCurrent || current.isNotEmpty()) && newOk && !working
    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (askCurrent) PassphraseField(current, { current = it }, currentLabel, enabled = !working, testTag = "dialog-current")
                if (confirmNew) {
                    PassphraseField(new, { new = it }, newLabel, enabled = !working, testTag = "dialog-new")
                    PassphraseField(
                        repeat, { repeat = it }, "Repeat it", enabled = !working, testTag = "dialog-repeat",
                        errorText = if (repeat.isNotEmpty() && new != repeat) "Doesn't match" else null,
                    )
                    if (new.isNotEmpty() && new.length < minNewLength) {
                        Text("Use at least $minNewLength characters.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                ErrorText(error)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(current, new) }, enabled = ready, modifier = Modifier.testTag("dialog-confirm")) {
                Text(
                    if (working) "Working…" else confirmLabel,
                    fontWeight = FontWeight.SemiBold,
                    color = if (ready) VaultTheme.palette.accent else MaterialTheme.colorScheme.outline,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !working) { Text("Cancel", color = VaultTheme.palette.accent) } },
        containerColor = VaultTheme.palette.card,
        shape = RoundedCornerShape(22.dp),
    )
}
