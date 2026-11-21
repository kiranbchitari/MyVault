package com.myvault.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.myvault.app.data.SecuritySettings
import com.myvault.app.ui.theme.VaultTheme

@Composable
fun AutoLockChips(selectedSeconds: Int, onSelected: (Int) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val palette = VaultTheme.palette
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SecuritySettings.AUTO_LOCK_OPTIONS_SECONDS.forEach { seconds ->
            val selected = seconds == selectedSeconds
            val shape = RoundedCornerShape(14.dp)
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = { onSelected(seconds) })
                    .testTag("autolock-$seconds"),
                shape = shape,
                color = if (selected) palette.accent.copy(alpha = 0.14f) else Color.Transparent,
                border = BorderStroke(1.dp, if (selected) palette.accent else MaterialTheme.colorScheme.outline),
            ) {
                Text(
                    text = if (seconds < 60) "$seconds sec" else "${seconds / 60} min",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) palette.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        }
    }
}
