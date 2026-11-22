package com.myvault.app.ui.items

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.myvault.app.ui.components.FormGroup
import com.myvault.app.ui.components.PrimaryButton
import com.myvault.app.ui.components.RowDivider
import com.myvault.app.ui.components.ScreenColumn
import com.myvault.app.ui.components.VaultSurface
import com.myvault.app.ui.components.VaultSwitch
import com.myvault.app.ui.theme.VaultTheme
import com.myvault.crypto.PasswordGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.ln
import kotlin.math.roundToInt

@HiltViewModel
class GeneratorViewModel @Inject constructor(private val generator: PasswordGenerator) : ViewModel() {
    var options by mutableStateOf(PasswordGenerator.Options())
        private set
    var password by mutableStateOf(generator.generate(PasswordGenerator.Options()))
        private set

    /** Bits of entropy for a uniformly random password of this length and alphabet. */
    val entropyBits: Int
        get() = (options.length * ln(options.classes.joinToString("").length.toDouble().coerceAtLeast(1.0)) / ln(2.0)).roundToInt()

    fun update(next: PasswordGenerator.Options) {
        // The last remaining character type cannot be switched off.
        if (!next.isValid) return
        options = next
        regenerate()
    }

    fun regenerate() {
        password = generator.generate(options)
    }

    override fun onCleared() {
        password = ""
    }
}

@Composable
fun GeneratorScreen(onBack: () -> Unit, onCopy: (String) -> Unit, viewModel: GeneratorViewModel = hiltViewModel()) {
    ScreenColumn(title = "Password Generator", modifier = Modifier.testTag("generator"), onBack = onBack) {
        GeneratorContent(viewModel, onCopy = onCopy)
        Spacer(Modifier.height(22.dp))
        PrimaryButton("Copy Password", { onCopy(viewModel.password) }, modifier = Modifier.testTag("copy-generated"), icon = Icons.Outlined.ContentCopy)
        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratorContent(viewModel: GeneratorViewModel, onCopy: ((String) -> Unit)? = null) {
    val options = viewModel.options
    val palette = VaultTheme.palette
    val bits = viewModel.entropyBits
    val (strength, strengthColour) = when {
        bits < 50 -> "Weak password" to MaterialTheme.colorScheme.error
        bits < 72 -> "Fair password" to palette.warning
        bits < 110 -> "Strong password" to palette.success
        else -> "Very strong password" to palette.success
    }

    VaultSurface(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(start = 18.dp, end = 6.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                viewModel.password,
                style = MaterialTheme.typography.titleLarge,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).testTag("generated-password"),
            )
            IconButton(onClick = viewModel::regenerate, modifier = Modifier.testTag("regenerate")) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Generate another", tint = palette.accent)
            }
            if (onCopy != null) {
                IconButton(onClick = { onCopy(viewModel.password) }) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy password", tint = palette.accent, modifier = Modifier.size(22.dp))
                }
            }
        }
    }

    Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.VerifiedUser, contentDescription = null, tint = strengthColour, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(palette.fill)) {
                Box(Modifier.fillMaxWidth((bits / 128f).coerceIn(0.06f, 1f)).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(strengthColour))
            }
            Spacer(Modifier.height(8.dp))
            Row {
                Text(strength, style = MaterialTheme.typography.titleSmall, color = strengthColour, modifier = Modifier.weight(1f))
                Text("$bits bits of entropy", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
    Spacer(Modifier.height(18.dp))

    FormGroup {
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Length", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                Text(
                    "${options.length}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(palette.fill).padding(horizontal = 14.dp, vertical = 5.dp).testTag("generator-length"),
                )
            }
            Slider(
                value = options.length.toFloat(),
                onValueChange = { viewModel.update(options.copy(length = it.roundToInt())) },
                valueRange = PasswordGenerator.MIN_LENGTH.toFloat()..PasswordGenerator.MAX_LENGTH.toFloat(),
                modifier = Modifier.testTag("generator-slider"),
                thumb = {
                    Box(
                        Modifier
                            .size(28.dp)
                            .shadow(6.dp, CircleShape, ambientColor = Color(0x80000000), spotColor = Color(0x80000000))
                            .background(Color.White, CircleShape)
                            .border(0.5.dp, Color(0x24000000), CircleShape),
                    )
                },
                track = { state ->
                    val span = state.valueRange.endInclusive - state.valueRange.start
                    val fraction = if (span <= 0f) 0f else ((state.value - state.valueRange.start) / span).coerceIn(0f, 1f)
                    Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(palette.fill)) {
                        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(palette.accent))
                    }
                },
            )
        }
        RowDivider(startIndent = 18.dp)
        Toggle("Uppercase letters", "A – Z", options.uppercase) { viewModel.update(options.copy(uppercase = it)) }
        RowDivider(startIndent = 18.dp)
        Toggle("Lowercase letters", "a – z", options.lowercase) { viewModel.update(options.copy(lowercase = it)) }
        RowDivider(startIndent = 18.dp)
        Toggle("Numbers", "0 – 9", options.digits) { viewModel.update(options.copy(digits = it)) }
        RowDivider(startIndent = 18.dp)
        Toggle("Symbols", "!@#$%", options.symbols) { viewModel.update(options.copy(symbols = it)) }
        RowDivider(startIndent = 18.dp)
        Toggle("Exclude look-alikes", "0 O 1 l I and similar", options.excludeAmbiguous) { viewModel.update(options.copy(excludeAmbiguous = it)) }
    }
}

@Composable
private fun Toggle(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        VaultSwitch(checked = checked, onCheckedChange = onChange, modifier = Modifier.testTag("toggle-$title"))
    }
}
