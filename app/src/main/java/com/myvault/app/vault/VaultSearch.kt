package com.myvault.app.vault

/**
 * In-memory search over decrypted items. Nothing here is persisted: there is no search index on
 * disk, and results exist only while the vault is unlocked.
 */
object VaultSearch {

    data class Filter(val query: String = "", val type: ItemType? = null, val favoritesOnly: Boolean = false)

    /**
     * Every word of the query must match somewhere in the item: its title, type, tags, notes,
     * field names, or the values of non-secret fields. Secret values are deliberately not
     * searchable, so typing into the search box can never reveal that a guess matches a password.
     */
    fun search(items: List<VaultItem>, filter: Filter): List<VaultItem> {
        val words = filter.query.trim().lowercase().split(Regex("""\s+""")).filter { it.isNotEmpty() }
        return items.asSequence()
            .filter { filter.type == null || it.type == filter.type }
            .filter { !filter.favoritesOnly || it.favorite }
            .mapNotNull { item -> score(item, words)?.let { item to it } }
            .sortedWith(compareByDescending<Pair<VaultItem, Int>> { it.second }.thenBy { it.first.title.lowercase() })
            .map { it.first }
            .toList()
    }

    private fun score(item: VaultItem, words: List<String>): Int? {
        if (words.isEmpty()) return 0
        val title = item.title.lowercase()
        val tags = item.tags.map { it.lowercase() }
        val other = buildList {
            add(item.type.label.lowercase())
            add(item.type.plural.lowercase())
            add(item.customTypeName.lowercase())
            add(item.notes.lowercase())
            item.fields.forEach { field ->
                add(field.label.lowercase())
                if (field.kind != FieldKind.SECRET) add(field.value.lowercase())
            }
        }
        var total = 0
        for (word in words) {
            total += when {
                title.startsWith(word) -> 100
                title.split(' ', '-', '_', '.').any { it.startsWith(word) } -> 80
                title.contains(word) -> 60
                tags.any { it == word } -> 50
                tags.any { it.contains(word) } -> 40
                other.any { it.contains(word) } -> 20
                else -> return null
            }
        }
        return total
    }

    fun recentlyAccessed(items: List<VaultItem>, limit: Int = 5): List<VaultItem> =
        items.filter { it.lastAccessedAt > 0 }.sortedByDescending { it.lastAccessedAt }.take(limit)

    fun favorites(items: List<VaultItem>): List<VaultItem> = items.filter { it.favorite }.sortedBy { it.title.lowercase() }

    fun countsByType(items: List<VaultItem>): Map<ItemType, Int> = items.groupingBy { it.type }.eachCount()
}
