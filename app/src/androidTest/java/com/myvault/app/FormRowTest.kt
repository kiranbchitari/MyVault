package com.myvault.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.myvault.app.ui.components.FormRow
import com.myvault.app.ui.theme.MyVaultTheme
import com.myvault.app.vault.FieldInput
import com.myvault.app.vault.ItemTemplates
import com.myvault.app.vault.ItemType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Typing into a form row whose value is reshaped as it is typed. */
@RunWith(AndroidJUnit4::class)
class FormRowTest {
    @get:Rule
    val compose = createComposeRule()

    private val expiry = ItemTemplates.of(ItemType.CREDIT_CARD).fields.first { it.key == "expiry" }
    private var value by mutableStateOf("")

    private fun shown(): String =
        compose.onNodeWithTag("field").fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()

    private fun type(keys: String) = keys.forEach { key ->
        compose.onNodeWithTag("field").performTextInput(key.toString())
        compose.waitForIdle()
    }

    @Test
    fun anExpiryTypedOneDigitAtATimeGainsItsSlashAndStaysInOrder() {
        compose.setContent {
            MyVaultTheme {
                FormRow(Icons.Outlined.CalendarToday, "Expiry date", value, { value = FieldInput.clean(expiry, it) }, fieldModifier = Modifier.testTag("field"))
            }
        }
        // The slash arrives with the third digit. The cursor must follow it, or the fourth
        // digit would be inserted in front of the third.
        type("092")
        assertEquals("09/2", value)
        type("9")
        assertEquals("09/29", value)
        assertEquals("09/29", shown())
        // Nothing more fits.
        type("5")
        assertEquals("09/29", value)

        // Replacing the text from outside the field is picked up too.
        compose.onNodeWithTag("field").performTextReplacement("1230")
        assertEquals("12/30", value)
        assertEquals("12/30", shown())
    }
}
