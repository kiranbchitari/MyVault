package com.myvault.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myvault.app.ui.theme.VaultTheme
import com.myvault.crypto.PassphraseStrength

/** Full-screen grouped background with a faint cool tint at the top. */
@Composable
fun VaultBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val palette = VaultTheme.palette
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(palette.backgroundTop, palette.backgroundBottom)))
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(palette.glow, Color.Transparent),
                        center = Offset(size.width / 2f, 0f),
                        radius = size.width * 0.9f,
                    ),
                    radius = size.width * 0.9f,
                    center = Offset(size.width / 2f, 0f),
                )
            },
    ) { content() }
}

/**
 * The card every grouped surface is built from: white (or elevated grey in dark mode), softly
 * shadowed in light mode, with continuous-looking rounded corners.
 */
@Composable
fun VaultSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    onClick: (() -> Unit)? = null,
    color: Color = VaultTheme.palette.card,
    elevation: Dp = 10.dp,
    content: @Composable () -> Unit,
) {
    val palette = VaultTheme.palette
    val shadowed = if (palette.isDark) {
        modifier
    } else {
        modifier.shadow(elevation, shape, clip = false, ambientColor = palette.cardShadow, spotColor = palette.cardShadow)
    }
    val border = if (palette.isDark) BorderStroke(0.5.dp, palette.cardBorder) else null
    if (onClick != null) {
        Surface(onClick = onClick, modifier = shadowed, shape = shape, color = color, border = border, content = content)
    } else {
        Surface(modifier = shadowed, shape = shape, color = color, border = border, content = content)
    }
}

/**
 * Standard screen layout before the vault unlocks: optional back button, scrolling content, and
 * actions pinned to the bottom above the keyboard. Content is capped in width for tablets.
 */
@Composable
fun VaultScreen(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    bottomBar: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    VaultBackground(modifier) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 520.dp).fillMaxSize()) {
                Box(Modifier.height(52.dp).padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(VaultIcons.ArrowBack, contentDescription = "Back", tint = VaultTheme.palette.accent, modifier = Modifier.size(30.dp))
                        }
                    }
                }
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                    content = content,
                )
                Column(
                    Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    content = bottomBar,
                )
            }
        }
    }
}

/** The filled blue action button. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
) {
    val palette = VaultTheme.palette
    val active = enabled && !loading
    val shape = RoundedCornerShape(16.dp)
    val content = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .then(if (active && !palette.isDark) Modifier.shadow(12.dp, shape, ambientColor = Color(0x551F7AFF), spotColor = Color(0x551F7AFF)) else Modifier)
            .clip(shape)
            .background(if (active) palette.accentBrush else SolidColor(palette.fill))
            .clickable(
                enabled = active,
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = Color.White),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides content) {
            Row(Modifier.padding(PaddingValues(horizontal = 20.dp, vertical = 14.dp)), verticalAlignment = Alignment.CenterVertically) {
                if (loading) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = content)
                    Spacer(Modifier.width(10.dp))
                } else if (icon != null) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                }
                Text(text, style = MaterialTheme.typography.labelLarge, color = content)
            }
        }
    }
}

/** A text-only action in the accent colour. */
@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) VaultTheme.palette.accent else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun VaultCard(modifier: Modifier = Modifier, contentPadding: Dp = 18.dp, content: @Composable ColumnScope.() -> Unit) {
    VaultSurface(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

/**
 * Masked passphrase input. The caller must hold [value] in plain `remember` state, never
 * `rememberSaveable`: saved instance state is written to disk by the system.
 */
@Composable
fun PassphraseField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    errorText: String? = null,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
    testTag: String = "passphrase",
) {
    var revealed by remember { mutableStateOf(false) }
    val userActivity = LocalUserActivity.current
    val palette = VaultTheme.palette
    OutlinedTextField(
        value = value,
        onValueChange = {
            userActivity()
            onValueChange(it)
        },
        modifier = modifier.fillMaxWidth().testTag(testTag),
        enabled = enabled,
        label = { Text(label) },
        singleLine = true,
        isError = errorText != null,
        supportingText = errorText?.let { { Text(it) } },
        visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Password,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onAny = { onImeAction() }),
        trailingIcon = {
            IconButton(onClick = { revealed = !revealed }) {
                Icon(
                    if (revealed) VaultIcons.VisibilityOff else VaultIcons.Visibility,
                    contentDescription = if (revealed) "Hide passphrase" else "Show passphrase",
                )
            }
        },
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = palette.card,
            unfocusedContainerColor = palette.card,
            disabledContainerColor = palette.card,
            errorContainerColor = palette.card,
            focusedBorderColor = palette.accent,
            focusedLabelColor = palette.accent,
            cursorColor = palette.accent,
            unfocusedBorderColor = palette.cardBorder,
        ),
    )
}

@Composable
fun StrengthMeter(assessment: PassphraseStrength.Assessment, modifier: Modifier = Modifier) {
    val palette = VaultTheme.palette
    val (filled, label, colour) = when (assessment.level) {
        PassphraseStrength.Level.TOO_SHORT -> Triple(0, "Too short", MaterialTheme.colorScheme.onSurfaceVariant)
        PassphraseStrength.Level.WEAK -> Triple(1, "Weak", MaterialTheme.colorScheme.error)
        PassphraseStrength.Level.FAIR -> Triple(2, "Fair", palette.warning)
        PassphraseStrength.Level.STRONG -> Triple(3, "Strong", palette.success)
        PassphraseStrength.Level.EXCELLENT -> Triple(4, "Excellent", palette.success)
    }
    Column(modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(4) { index ->
                val target = if (index < filled) colour else palette.fill
                val animated by animateColorAsState(target, label = "strength")
                Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(animated))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = listOfNotNull(label, assessment.hint).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("strength"),
        )
    }
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null) {
    Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
    if (subtitle != null) {
        Spacer(Modifier.height(10.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ErrorText(message: String?, modifier: Modifier = Modifier) {
    if (message != null) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = modifier.testTag("error"),
        )
    }
}
