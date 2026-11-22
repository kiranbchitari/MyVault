package com.myvault.app.util

import com.myvault.app.vault.FieldKind
import com.myvault.app.vault.ItemField
import com.myvault.app.vault.ItemType
import com.myvault.app.vault.VaultItem

/**
 * Obviously fictional records for trying the app in debug builds. Loaded only when the developer
 * taps "Load demo data"; nothing here is seeded automatically or reachable in release builds.
 */
object DemoData {
    private fun item(type: ItemType, title: String, vararg fields: ItemField, notes: String = "", tags: List<String> = emptyList(), favorite: Boolean = false, customType: String = "") =
        VaultItem(id = "", type = type, title = title, fields = fields.toList(), notes = notes, tags = tags, favorite = favorite, customTypeName = customType, createdAt = 0, updatedAt = 0)

    private fun text(key: String, label: String, value: String) = ItemField(key, label, value)
    private fun secret(key: String, label: String, value: String) = ItemField(key, label, value, FieldKind.SECRET)

    fun items(): List<VaultItem> = listOf(
        item(ItemType.PASSWORD, "Northwind Mail", text("username", "Username", "alex.rivera"), text("email", "Email", "alex.rivera@example.com"), secret("password", "Password", "Demo-Only!Quartz-Lantern-58"), ItemField("url", "Website URL", "https://mail.example.com", FieldKind.URL), tags = listOf("email", "personal"), favorite = true),
        item(ItemType.PASSWORD, "Streamly", text("email", "Email", "alex.rivera@example.com"), secret("password", "Password", "Demo-Only!Velvet-Harbor-12"), ItemField("url", "Website URL", "https://streamly.example.com", FieldKind.URL), tags = listOf("entertainment")),
        item(ItemType.PASSWORD, "City Library", text("username", "Username", "member-204418"), secret("password", "Password", "Demo-Only!Paper-Crane-77"), tags = listOf("personal")),
        item(ItemType.APP_PIN, "Notes app lock", secret("pin", "PIN", "0000"), text("account", "Associated account", "alex.rivera@example.com")),
        item(ItemType.BANK_ACCOUNT, "Example National Bank", text("holder", "Account holder", "Alex Rivera"), secret("number", "Account number", "000123456789"), text("ifsc", "IFSC", "EXMP0001234"), text("branch", "Branch", "Riverside"), text("accountType", "Account type", "Savings"), tags = listOf("finance"), favorite = true),
        item(ItemType.CREDIT_CARD, "Travel Rewards", text("holder", "Cardholder name", "ALEX RIVERA"), text("bank", "Bank", "Example National Bank"), text("network", "Card network", "Visa"), text("last4", "Last four digits", "0000"), text("expiry", "Expiry date", "09/29"), tags = listOf("finance", "travel")),
        item(ItemType.DEBIT_CARD, "Everyday Debit", text("holder", "Cardholder name", "ALEX RIVERA"), text("bank", "Bank", "Example National Bank"), text("network", "Card network", "RuPay"), text("last4", "Last four digits", "1111"), text("expiry", "Expiry date", "03/28")),
        item(ItemType.WIFI, "Home Fibre", secret("password", "Password", "demo-only-maple-river-42"), text("router", "Router name", "Hallway router"), text("location", "Location", "Home"), tags = listOf("home")),
        item(ItemType.IDENTITY, "Passport", secret("number", "Document number", "X0000000"), text("holder", "Holder name", "Alex Rivera"), text("expiry", "Expiry date", "14/02/2034"), tags = listOf("travel")),
        item(ItemType.SECURE_NOTE, "Bike lock", ItemField("body", "Note", "Combination 0-0-0-0 (demo)\nSpare key is in the blue box.", FieldKind.MULTILINE), tags = listOf("home"), favorite = true),
        item(ItemType.DEVELOPER_SECRET, "Staging API key", text("service", "Service name", "Example Cloud"), secret("secret", "API key or token", "demo_only_sk_test_0000000000000000"), text("environment", "Environment", "Staging"), tags = listOf("work")),
        item(ItemType.CUSTOM, "Gym membership", ItemField("f1", "Member number", "M-000-204", custom = true), ItemField("f2", "Locker code", "0000", FieldKind.SECRET, custom = true), customType = "Membership"),
    )
}
