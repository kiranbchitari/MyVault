package com.myvault.app.vault

import kotlinx.serialization.Serializable

enum class ItemType(val label: String, val plural: String) {
    PASSWORD("Password", "Passwords"),
    APP_PIN("App PIN", "App PINs"),
    BANK_ACCOUNT("Bank account", "Bank Accounts"),
    CREDIT_CARD("Credit card", "Credit Cards"),
    DEBIT_CARD("Debit card", "Debit Cards"),
    WIFI("Wi-Fi network", "Wi-Fi"),
    IDENTITY("Identity document", "Identity Documents"),
    SECURE_NOTE("Secure note", "Secure Notes"),
    DEVELOPER_SECRET("Developer secret", "Developer Secrets"),
    CUSTOM("Custom item", "Custom Items");

    val isCard: Boolean get() = this == CREDIT_CARD || this == DEBIT_CARD
}

enum class FieldKind {
    TEXT,

    /** Masked until revealed, and copied to the clipboard as sensitive. */
    SECRET,
    URL,
    MULTILINE,
}

@Serializable
data class ItemField(
    val key: String,
    val label: String,
    val value: String,
    val kind: FieldKind = FieldKind.TEXT,
    /** True for fields the user added; false for fields from the record type's template. */
    val custom: Boolean = false,
)

/**
 * One vault record. The whole object, including its title, type, tags and timestamps, is
 * serialized and encrypted as a single payload; no part of it is stored in the clear.
 */
@Serializable
data class VaultItem(
    val id: String,
    val type: ItemType,
    val title: String,
    val fields: List<ItemField> = emptyList(),
    val notes: String = "",
    val tags: List<String> = emptyList(),
    val favorite: Boolean = false,
    /** User-defined record type name, for [ItemType.CUSTOM] items. */
    val customTypeName: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val lastAccessedAt: Long = 0,
) {
    fun field(key: String): String = fields.firstOrNull { it.key == key }?.value.orEmpty()

    /** A short non-secret line shown under the title in lists. */
    val subtitle: String
        get() = when (type) {
            ItemType.PASSWORD -> field("username").ifEmpty { field("email") }.ifEmpty { field("url") }
            ItemType.APP_PIN -> field("account").ifEmpty { field("username") }
            ItemType.BANK_ACCOUNT -> listOf(field("accountType"), field("branch")).filter { it.isNotEmpty() }.joinToString(" · ")
            ItemType.CREDIT_CARD, ItemType.DEBIT_CARD ->
                listOf(field("network"), field("last4").takeIf { it.isNotEmpty() }?.let { "•••• $it" }.orEmpty())
                    .filter { it.isNotEmpty() }.joinToString(" ")
            ItemType.WIFI -> field("location").ifEmpty { field("router") }
            ItemType.IDENTITY -> field("holder")
            ItemType.SECURE_NOTE -> ""
            ItemType.DEVELOPER_SECRET -> listOf(field("service"), field("environment")).filter { it.isNotEmpty() }.joinToString(" · ")
            ItemType.CUSTOM -> customTypeName
        }

    /**
     * The one value this record is usually opened for: its password, PIN, key or number. Lists
     * offer it as a single-tap copy. Null when the record holds nothing worth copying that way.
     */
    val quickCopyField: ItemField?
        get() {
            val key = when (type) {
                ItemType.PASSWORD, ItemType.WIFI -> "password"
                ItemType.APP_PIN -> "pin"
                ItemType.BANK_ACCOUNT, ItemType.IDENTITY -> "number"
                ItemType.DEVELOPER_SECRET -> "secret"
                ItemType.SECURE_NOTE -> "body"
                ItemType.CREDIT_CARD, ItemType.DEBIT_CARD, ItemType.CUSTOM -> null
            }
            return fields.firstOrNull { !it.custom && it.key == key && it.value.isNotEmpty() }
                ?: fields.firstOrNull { it.kind == FieldKind.SECRET && it.value.isNotEmpty() }
        }
}

/** Decrypted description of an attachment; the file contents stay encrypted until opened. */
@Serializable
data class AttachmentInfo(
    val id: String,
    val itemId: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val createdAt: Long,
)
