package com.myvault.app.vault

import com.myvault.app.vault.VaultSearch.Filter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultSearchAndRulesTest {
    private val items = SampleItems.all

    private fun ids(filter: Filter) = VaultSearch.search(items, filter).map { it.id }

    @Test
    fun `an empty query returns everything, alphabetically`() {
        assertEquals(listOf("p1", "w1", "n1", "i1", "c1"), ids(Filter()))
    }

    @Test
    fun `search matches titles, categories, tags, notes and custom fields`() {
        assertEquals(listOf("p1"), ids(Filter("portal")))
        assertEquals(listOf("c1"), ids(Filter("credit card")))
        assertEquals(listOf("n1"), ids(Filter("home")).filter { it == "n1" })
        assertEquals(listOf("p1"), ids(Filter("march")))
        assertEquals(listOf("p1"), ids(Filter("first school")))
        assertEquals(listOf("p1"), ids(Filter("security question")))
        assertEquals(listOf("c1"), ids(Filter("VISA")))
    }

    @Test
    fun `every word must match and title matches rank first`() {
        assertEquals(listOf("p1"), ids(Filter("example portal")))
        assertTrue(ids(Filter("example nothing-like-this")).isEmpty())
        // "Example" is in the password's title and only in the card's bank field.
        assertEquals(listOf("p1", "c1"), ids(Filter("example")))
    }

    @Test
    fun `secret values are never searchable`() {
        assertTrue(ids(Filter("S3cr3t-Horse")).isEmpty())
        assertTrue(ids(Filter("mango-river")).isEmpty())
        assertTrue(ids(Filter("Z1234567")).isEmpty())
    }

    @Test
    fun `results can be narrowed by category and favorites`() {
        assertEquals(listOf("w1"), ids(Filter(type = ItemType.WIFI)))
        assertEquals(listOf("n1"), ids(Filter(favoritesOnly = true)))
        assertTrue(ids(Filter("portal", type = ItemType.WIFI)).isEmpty())
    }

    @Test
    fun `recent, favorites and counts are derived from the items`() {
        val accessed = items.map { if (it.id == "c1") it.copy(lastAccessedAt = 50) else if (it.id == "w1") it.copy(lastAccessedAt = 90) else it }
        assertEquals(listOf("w1", "c1"), VaultSearch.recentlyAccessed(accessed).map { it.id })
        assertEquals(listOf("n1"), VaultSearch.favorites(items).map { it.id })
        assertEquals(1, VaultSearch.countsByType(items)[ItemType.PASSWORD])
    }

    @Test
    fun `a title is required`() {
        assertNotNull(ItemValidator.validate(SampleItems.password().copy(title = "")))
        assertNull(ItemValidator.validate(SampleItems.password()))
    }

    @Test
    fun `cards never accept CVV, PIN, OTP or a full card number`() {
        val card = SampleItems.card()
        assertNull(ItemValidator.validate(card))
        for (label in listOf("CVV", "cvc code", "Card PIN", "ATM pin", "OTP", "Card number")) {
            val withField = card.copy(fields = card.fields + ItemField("x", label, "123", FieldKind.SECRET, custom = true))
            assertNotNull(label, ItemValidator.validate(withField))
        }
        val fullNumber = card.copy(fields = card.fields + ItemField("x", "Reference", "4242 4242 4242 4242", custom = true))
        assertNotNull(ItemValidator.validate(fullNumber))
        // A harmless custom field is fine, and "pin" inside another word is not a false positive.
        assertNull(ItemValidator.validate(card.copy(fields = card.fields + ItemField("x", "Shipping address", "12 Park Lane", custom = true))))
    }

    @Test
    fun `card last four digits and expiry are checked`() {
        fun card(last4: String, expiry: String) = SampleItems.card().let { c ->
            c.copy(fields = c.fields.map { if (it.key == "last4") it.copy(value = last4) else if (it.key == "expiry") it.copy(value = expiry) else it })
        }
        assertNull(ItemValidator.validate(card("4242", "08/29")))
        assertNull(ItemValidator.validate(card("", "")))
        assertNotNull(ItemValidator.validate(card("424", "08/29")))
        assertNotNull(ItemValidator.validate(card("42424", "08/29")))
        assertNotNull(ItemValidator.validate(card("4242", "13/29")))
        assertNotNull(ItemValidator.validate(card("4242", "8/2029")))
    }

    @Test
    fun `bank accounts refuse transaction secrets`() {
        val bank = VaultItem(id = "b", type = ItemType.BANK_ACCOUNT, title = "Example Bank", createdAt = 0, updatedAt = 0)
        assertNull(ItemValidator.validate(bank))
        for (label in listOf("UPI PIN", "Transaction password", "OTP", "MPIN")) {
            assertNotNull(label, ItemValidator.validate(bank.copy(fields = listOf(ItemField("x", label, "1", custom = true)))))
        }
    }

    @Test
    fun `card and bank templates do not offer forbidden fields`() {
        for (type in listOf(ItemType.CREDIT_CARD, ItemType.DEBIT_CARD)) {
            val labels = ItemTemplates.of(type).fields.joinToString(" ") { it.label.lowercase() }
            for (word in listOf("cvv", "cvc", "pin", "otp", "card number")) assertFalse("$type offers $word", labels.contains(word))
        }
        assertTrue(ItemType.entries.all { ItemTemplates.of(it).type == it })
    }

    @Test
    fun `the card number check uses the Luhn checksum`() {
        assertTrue(ItemValidator.looksLikeCardNumber("4242424242424242"))
        assertTrue(ItemValidator.looksLikeCardNumber("4242-4242-4242-4242"))
        assertFalse(ItemValidator.looksLikeCardNumber("4242424242424241"))
        assertFalse(ItemValidator.looksLikeCardNumber("4242"))
        assertFalse(ItemValidator.looksLikeCardNumber("Visa ending 4242"))
    }
}
