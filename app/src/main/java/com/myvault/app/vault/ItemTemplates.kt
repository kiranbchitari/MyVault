package com.myvault.app.vault

enum class InputKind { TEXT, EMAIL, URL, NUMBER, PASSWORD }

/** One field of a record type's dedicated form. */
data class FieldSpec(
    val key: String,
    val label: String,
    val kind: FieldKind = FieldKind.TEXT,
    val input: InputKind = InputKind.TEXT,
    val maxLength: Int = 512,
    val placeholder: String = "",
    /** Offer the password generator next to this field. */
    val generator: Boolean = false,
)

data class ItemTemplate(
    val type: ItemType,
    val titleLabel: String,
    val fields: List<FieldSpec>,
    /** Shown on the form to steer people away from storing things they should not. */
    val guidance: String? = null,
    val allowsAttachments: Boolean = false,
)

object ItemTemplates {
    private const val CARD_GUIDANCE =
        "For your safety MyVault never stores a full card number, CVV, card PIN or OTP. Save only what helps you recognise the card."

    private fun cardFields() = listOf(
        FieldSpec("holder", "Cardholder name"),
        FieldSpec("bank", "Bank"),
        FieldSpec("network", "Card network", placeholder = "Visa, Mastercard, RuPay…"),
        FieldSpec("last4", "Last four digits", input = InputKind.NUMBER, maxLength = 4),
        FieldSpec("expiry", "Expiry date", maxLength = 5, placeholder = "MM/YY"),
    )

    private val templates: Map<ItemType, ItemTemplate> = listOf(
        ItemTemplate(
            ItemType.PASSWORD, "Website or app name",
            listOf(
                FieldSpec("username", "Username"),
                FieldSpec("email", "Email", input = InputKind.EMAIL),
                FieldSpec("password", "Password", FieldKind.SECRET, InputKind.PASSWORD, generator = true),
                FieldSpec("url", "Website URL", FieldKind.URL, InputKind.URL),
            ),
        ),
        ItemTemplate(
            ItemType.APP_PIN, "Application name",
            listOf(
                FieldSpec("pin", "PIN", FieldKind.SECRET, InputKind.PASSWORD, maxLength = 32),
                FieldSpec("username", "Username"),
                FieldSpec("account", "Associated account"),
            ),
            guidance = "Meant for app lock and login PINs. Don't store ATM, card or UPI transaction PINs here.",
        ),
        ItemTemplate(
            ItemType.BANK_ACCOUNT, "Bank name",
            listOf(
                FieldSpec("holder", "Account holder"),
                FieldSpec("number", "Account number", FieldKind.SECRET, InputKind.NUMBER, maxLength = 34),
                FieldSpec("ifsc", "IFSC", maxLength = 11),
                FieldSpec("branch", "Branch"),
                FieldSpec("accountType", "Account type", placeholder = "Savings, Current…"),
            ),
            guidance = "Store reference details only. Never save transaction passwords, UPI PINs, OTPs or net-banking authorisation secrets.",
        ),
        ItemTemplate(ItemType.CREDIT_CARD, "Card nickname", cardFields(), guidance = CARD_GUIDANCE),
        ItemTemplate(ItemType.DEBIT_CARD, "Card nickname", cardFields(), guidance = CARD_GUIDANCE),
        ItemTemplate(
            ItemType.WIFI, "Network name",
            listOf(
                FieldSpec("password", "Password", FieldKind.SECRET, InputKind.PASSWORD, generator = true),
                FieldSpec("router", "Router name"),
                FieldSpec("location", "Location"),
            ),
        ),
        ItemTemplate(
            ItemType.IDENTITY, "Document type",
            listOf(
                FieldSpec("number", "Document number", FieldKind.SECRET),
                FieldSpec("holder", "Holder name"),
                FieldSpec("expiry", "Expiry date", placeholder = "DD/MM/YYYY"),
            ),
            allowsAttachments = true,
        ),
        ItemTemplate(ItemType.SECURE_NOTE, "Title", listOf(FieldSpec("body", "Note", FieldKind.MULTILINE, maxLength = 20_000))),
        ItemTemplate(
            ItemType.DEVELOPER_SECRET, "Secret name",
            listOf(
                FieldSpec("service", "Service name"),
                FieldSpec("secret", "API key or token", FieldKind.SECRET, InputKind.PASSWORD, maxLength = 8_192),
                FieldSpec("environment", "Environment", placeholder = "Production, staging…"),
            ),
        ),
        ItemTemplate(ItemType.CUSTOM, "Title", emptyList()),
    ).associateBy { it.type }

    fun of(type: ItemType): ItemTemplate = templates.getValue(type)
}

/** Rules that keep dangerous data out of the vault and catch obvious mistakes. */
object ItemValidator {
    const val MAX_TAGS = 20

    private val forbiddenOnCards = listOf("cvv", "cvc", "cvn", "pin", "otp", "magstripe", "track 1", "track 2", "card number")
    private val forbiddenOnBank = listOf("otp", "upi pin", "mpin", "transaction password", "tpin", "atm pin")
    private val expiryPattern = Regex("""(0[1-9]|1[0-2])/\d{2}""")

    /** Returns a message describing the first problem, or null when the item may be saved. */
    fun validate(item: VaultItem): String? {
        val template = ItemTemplates.of(item.type)
        if (item.title.isBlank()) return "${template.titleLabel} is required."
        if (item.type == ItemType.CUSTOM && item.customTypeName.isBlank()) return "Give this record type a name."
        if (item.tags.size > MAX_TAGS) return "Use at most $MAX_TAGS tags."
        item.fields.filter { it.custom }.forEach { field ->
            if (field.label.isBlank()) return "Every custom field needs a name."
        }
        if (item.type.isCard) {
            val last4 = item.field("last4")
            if (last4.isNotEmpty() && !Regex("""\d{4}""").matches(last4)) return "Last four digits must be exactly 4 digits."
            val expiry = item.field("expiry")
            if (expiry.isNotEmpty() && !expiryPattern.matches(expiry)) return "Expiry date must look like MM/YY."
            forbidden(item, forbiddenOnCards)?.let { return "MyVault doesn't store \"$it\" for cards. Remove that field to continue." }
            if (item.fields.any { looksLikeCardNumber(it.value) }) return "That looks like a full card number. Store only the last four digits."
        }
        if (item.type == ItemType.BANK_ACCOUNT) {
            forbidden(item, forbiddenOnBank)?.let { return "Don't store \"$it\" here. MyVault is not meant for transaction authorisation secrets." }
        }
        return null
    }

    private fun forbidden(item: VaultItem, words: List<String>): String? =
        item.fields.filter { it.custom }.firstNotNullOfOrNull { field ->
            val label = field.label.lowercase()
            words.firstOrNull { word -> Regex("""(^|[^a-z])${Regex.escape(word)}([^a-z]|$)""").containsMatchIn(label) }?.let { field.label }
        }

    /** 13 to 19 digits (spaces or dashes allowed) that pass the Luhn checksum. */
    internal fun looksLikeCardNumber(value: String): Boolean {
        val trimmed = value.trim()
        if (!Regex("""[\d -]{13,23}""").matches(trimmed)) return false
        val digits = trimmed.filter(Char::isDigit)
        if (digits.length !in 13..19) return false
        var sum = 0
        digits.reversed().forEachIndexed { index, c ->
            var d = c - '0'
            if (index % 2 == 1) {
                d *= 2
                if (d > 9) d -= 9
            }
            sum += d
        }
        return sum % 10 == 0
    }
}
