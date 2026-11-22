package com.myvault.app.ui.items

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.Payment
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import com.myvault.app.vault.FieldKind
import com.myvault.app.vault.ItemType

/** Outline glyph shown at the start of a field's row. */
fun fieldIcon(key: String, kind: FieldKind = FieldKind.TEXT): ImageVector = when (key) {
    "username", "holder" -> Icons.Outlined.Person
    "email", "account" -> Icons.Outlined.AlternateEmail
    "password", "secret" -> Icons.Outlined.Key
    "pin" -> Icons.Outlined.Lock
    "url" -> Icons.Outlined.Language
    "bank" -> Icons.Outlined.AccountBalance
    "network" -> Icons.Outlined.CreditCard
    "last4" -> Icons.Outlined.Payment
    "expiry" -> Icons.Outlined.CalendarToday
    "number", "ifsc" -> Icons.Outlined.Numbers
    "branch", "location" -> Icons.Outlined.LocationOn
    "accountType" -> Icons.Outlined.Category
    "router" -> Icons.Outlined.Router
    "service" -> Icons.Outlined.Cloud
    "environment" -> Icons.Outlined.Layers
    "body", "notes" -> Icons.AutoMirrored.Outlined.Notes
    "tags" -> Icons.Outlined.Sell
    else -> if (kind == FieldKind.SECRET) Icons.Outlined.Lock else Icons.Outlined.Tune
}

/** Outline glyph for the title row of each record type. */
fun titleIcon(type: ItemType): ImageVector = when (type) {
    ItemType.PASSWORD -> Icons.Outlined.Language
    ItemType.BANK_ACCOUNT -> Icons.Outlined.AccountBalance
    ItemType.CREDIT_CARD, ItemType.DEBIT_CARD -> Icons.Outlined.CreditCard
    ItemType.IDENTITY -> Icons.Outlined.Badge
    else -> Icons.Outlined.Description
}

/** "Credit card" -> "Credit Card", for screen titles. */
fun ItemType.titleCase(): String = label.split(' ').joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
