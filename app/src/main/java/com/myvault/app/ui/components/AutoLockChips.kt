package com.myvault.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.myvault.app.data.SecuritySettings

@Composable
fun AutoLockChips(selectedSeconds: Int, onSelected: (Int) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    ChoiceChips(
        options = SecuritySettings.AUTO_LOCK_OPTIONS_SECONDS,
        selected = selectedSeconds,
        label = { if (it < 60) "$it sec" else "${it / 60} min" },
        onSelected = onSelected,
        modifier = modifier,
        tagPrefix = "autolock",
        enabled = enabled,
    )
}
