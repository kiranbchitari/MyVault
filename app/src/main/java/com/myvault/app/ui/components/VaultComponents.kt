package com.myvault.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.myvault.app.ui.theme.VaultTheme
import com.myvault.crypto.PassphraseStrength

/** Full-screen gradient backdrop with a soft accent glow near the top. */
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
                        center = Offset(size.width / 2f, size.height * 0.16f),
                        radius = size.width * 0.75f,
                    ),
                    radius = size.width * 0.75f,
                    center = Offset(size.width / 2f, size.height * 0.16f),
                )
            },
    ) { content() }
}

/**
 * Standard screen layout: optional back button, scrolling content, and actions pinned to the
 * bottom above the keyboard. Content is capped in width so it stays readable on tablets.
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
                Box(Modifier.height(56.dp).padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(VaultIcons.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
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

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(12.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun VaultCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val palette = VaultTheme.palette
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = palette.card,
        border = BorderStroke(1.dp, palette.cardBorder),
    ) {
        Column(Modifier.padding(20.dp), content = content)
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
) {
    var revealed by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().testTag("passphrase"),
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
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = VaultTheme.palette.accent,
            focusedLabelColor = VaultTheme.palette.accent,
            cursorColor = VaultTheme.palette.accent,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
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
                val target = if (index < filled) colour else MaterialTheme.colorScheme.outlineVariant
                val animated by animateColorAsState(target, label = "strength")
                Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(animated))
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
