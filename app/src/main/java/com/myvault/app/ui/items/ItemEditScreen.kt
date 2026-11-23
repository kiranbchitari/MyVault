package com.myvault.app.ui.items

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myvault.app.ui.components.CardPreview
import com.myvault.app.ui.components.ConfirmDialog
import com.myvault.app.ui.components.FormGroup
import com.myvault.app.ui.components.FormRow
import com.myvault.app.ui.components.RowDivider
import com.myvault.app.ui.components.SuggestionChips
import com.myvault.app.ui.components.TagRow
import com.myvault.app.ui.components.VaultSwitch
import com.myvault.app.ui.components.ErrorText
import com.myvault.app.ui.components.PrimaryButton
import com.myvault.app.ui.components.QuietButton
import com.myvault.app.ui.components.ScreenColumn
import com.myvault.app.ui.theme.VaultTheme
import com.myvault.app.vault.FieldInput
import com.myvault.app.vault.FieldKind
import com.myvault.app.vault.FieldSpec
import com.myvault.app.vault.InputKind
import com.myvault.app.vault.InvalidItemException
import com.myvault.app.vault.ItemField
import com.myvault.app.vault.ItemTemplate
import com.myvault.app.vault.ItemTemplates
import com.myvault.app.vault.ItemType
import com.myvault.app.vault.VaultItem
import com.myvault.app.vault.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CustomFieldDraft(val key: String, val label: String = "", val value: String = "", val secret: Boolean = false)

/**
 * The item being written. Held in memory only, and cleared together with the unlocked session,
 * so a half-typed secret never outlives a lock or reaches saved instance state.
 */
@HiltViewModel
class ItemEditViewModel @Inject constructor(private val repository: VaultRepository) : ViewModel() {
    private var original: VaultItem? = null
    private var started = false

    /** Whether a new item is being created rather than an existing one edited. */
    var isNew by mutableStateOf(true)
        private set

    var type by mutableStateOf(ItemType.PASSWORD)
        private set
    var title by mutableStateOf("")
    var customTypeName by mutableStateOf("")
    var notes by mutableStateOf("")
    var tags by mutableStateOf("")
    var favorite by mutableStateOf(false)

    /** Notes and tags are optional extras: their rows appear once asked for, or when already filled in. */
    var showNotes by mutableStateOf(false)
        private set
    var showTags by mutableStateOf(false)
        private set
    val values = mutableStateMapOf<String, String>()
    val customFields = mutableStateListOf<CustomFieldDraft>()
    var error by mutableStateOf<String?>(null)
        private set
    var saving by mutableStateOf(false)
        private set
    var dirty by mutableStateOf(false)
        private set

    val template: ItemTemplate get() = ItemTemplates.of(type)

    fun start(itemId: String?, newType: ItemType?) {
        if (started) return
        started = true
        val existing = itemId?.let(repository::find)
        original = existing
        isNew = existing == null
        type = existing?.type ?: newType ?: ItemType.PASSWORD
        if (existing != null) {
            title = existing.title
            customTypeName = existing.customTypeName
            notes = existing.notes
            tags = existing.tags.joinToString(", ")
            favorite = existing.favorite
            existing.fields.filterNot { it.custom }.forEach { values[it.key] = it.value }
            customFields += existing.fields.filter { it.custom }.map { CustomFieldDraft(it.key, it.label, it.value, it.kind == FieldKind.SECRET) }
            showNotes = existing.notes.isNotBlank()
            showTags = existing.tags.isNotEmpty()
        }
    }

    fun addNotes() {
        showNotes = true
    }

    fun addTags() {
        showTags = true
    }

    /** Puts the clipboard's text into [spec]'s field, or says why it could not. */
    fun paste(spec: FieldSpec, clip: String?) {
        if (clip.isNullOrBlank()) {
            error = "There's nothing on the clipboard to paste."
        } else {
            setValue(spec, clip.trim())
        }
    }

    fun edited() {
        dirty = true
        error = null
    }

    fun setValue(spec: FieldSpec, value: String) {
        values[spec.key] = FieldInput.clean(spec, value)
        edited()
    }

    fun addCustomField() {
        customFields += CustomFieldDraft(key = "custom-${repository.newId()}")
        edited()
    }

    fun updateCustomField(index: Int, draft: CustomFieldDraft) {
        customFields[index] = draft
        edited()
    }

    fun removeCustomField(index: Int) {
        customFields.removeAt(index)
        edited()
    }

    fun save(onSaved: (String) -> Unit) {
        if (saving) return
        val base = original
        val fields = template.fields.map { spec -> ItemField(spec.key, spec.label, values[spec.key].orEmpty().trim(), spec.kind) } +
            customFields.filter { it.label.isNotBlank() || it.value.isNotBlank() }.map {
                ItemField(it.key, it.label.trim(), it.value, if (it.secret) FieldKind.SECRET else FieldKind.TEXT, custom = true)
            }
        val item = VaultItem(
            id = base?.id ?: repository.newId(),
            type = type,
            title = title,
            fields = fields,
            notes = notes.trim(),
            tags = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() },
            favorite = favorite,
            customTypeName = customTypeName.trim(),
            createdAt = base?.createdAt ?: 0,
            updatedAt = base?.updatedAt ?: 0,
            lastAccessedAt = base?.lastAccessedAt ?: 0,
        )
        saving = true
        viewModelScope.launch {
            try {
                val saved = repository.save(item)
                dirty = false
                onSaved(saved.id)
            } catch (e: InvalidItemException) {
                error = e.message
            } catch (_: Exception) {
                error = "The item could not be saved. Please try again."
            } finally {
                saving = false
            }
        }
    }

    override fun onCleared() {
        values.clear()
        customFields.clear()
        title = ""
        notes = ""
    }
}

@Composable
fun ItemEditScreen(itemId: String?, newType: ItemType?, onBack: () -> Unit, onSaved: (String) -> Unit, viewModel: ItemEditViewModel = hiltViewModel()) {
    val titleFocus = remember { FocusRequester() }
    val notesFocus = remember { FocusRequester() }
    val tagsFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        viewModel.start(itemId, newType)
        // A new record starts with the cursor in its first field and the keyboard up.
        if (viewModel.isNew) runCatching { titleFocus.requestFocus() }
    }
    val template = viewModel.template
    val context = LocalContext.current
    var confirmDiscard by remember { mutableStateOf(false) }
    var generatorFor by remember { mutableStateOf<FieldSpec?>(null) }
    // Which optional row was just added, so the cursor can follow it.
    var justAdded by remember { mutableStateOf<String?>(null) }
    val visible = remember { mutableStateMapOf<String, Boolean>() }
    val leave = { if (viewModel.dirty) confirmDiscard = true else onBack() }

    BackHandler(enabled = viewModel.dirty) { confirmDiscard = true }
    LaunchedEffect(justAdded) {
        when (justAdded) {
            "notes" -> runCatching { notesFocus.requestFocus() }
            "tags" -> runCatching { tagsFocus.requestFocus() }
        }
    }

    val parsedTags = viewModel.tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    ScreenColumn(
        title = "${if (viewModel.isNew) "New" else "Edit"} ${viewModel.type.titleCase()}",
        modifier = Modifier.testTag("item-edit"),
        onBack = leave,
        actions = {
            IconButton(onClick = {
                viewModel.favorite = !viewModel.favorite
                viewModel.edited()
            }) {
                Icon(
                    if (viewModel.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = "Favorite",
                    tint = if (viewModel.favorite) VaultTheme.palette.favorite else MaterialTheme.colorScheme.onBackground,
                )
            }
        },
        bottomBar = {
            // Save stays in reach however long the form is, and sits just above the keyboard.
            ErrorText(viewModel.error, Modifier.padding(top = 8.dp, start = 4.dp))
            Spacer(Modifier.height(8.dp))
            PrimaryButton(
                text = if (viewModel.saving) "Encrypting…" else "Save",
                onClick = { viewModel.save(onSaved) },
                loading = viewModel.saving,
                modifier = Modifier.testTag("save"),
            )
            Spacer(Modifier.height(12.dp))
        },
    ) {
        if (viewModel.type.isCard) {
            // The card fills in as its details are typed.
            CardPreview(
                nickname = viewModel.title,
                network = viewModel.values["network"].orEmpty(),
                lastFour = viewModel.values["last4"].orEmpty(),
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp).widthIn(max = 300.dp),
                holder = viewModel.values["holder"].orEmpty(),
                bank = viewModel.values["bank"].orEmpty(),
                expiry = viewModel.values["expiry"].orEmpty(),
                credit = viewModel.type == ItemType.CREDIT_CARD,
                placeholders = true,
            )
            Spacer(Modifier.height(22.dp))
        }

        FormGroup {
            FormRow(
                icon = titleIcon(viewModel.type),
                label = template.titleLabel,
                value = viewModel.title,
                onValueChange = {
                    viewModel.title = it.take(120)
                    viewModel.edited()
                },
                fieldModifier = Modifier.focusRequester(titleFocus).testTag("field-title"),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = if (template.fields.isEmpty() && viewModel.type != ItemType.CUSTOM) ImeAction.Done else ImeAction.Next,
                ),
            )
            if (template.titleSuggestions.offeredFor(viewModel.title)) {
                SuggestionChips(
                    options = template.titleSuggestions,
                    selected = viewModel.title,
                    onPick = {
                        viewModel.title = it
                        viewModel.edited()
                    },
                    modifier = Modifier.padding(start = 54.dp, end = 16.dp, bottom = 12.dp),
                    tagPrefix = "suggest-title",
                )
            }
            if (viewModel.type == ItemType.CUSTOM) {
                RowDivider()
                FormRow(
                    icon = fieldIcon("accountType"),
                    label = "Record type",
                    value = viewModel.customTypeName,
                    onValueChange = {
                        viewModel.customTypeName = it.take(40)
                        viewModel.edited()
                    },
                    fieldModifier = Modifier.testTag("field-customType"),
                    placeholder = "Membership, Insurance, Licence…",
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                )
            }
            template.fields.forEachIndexed { index, spec ->
                RowDivider()
                val value = viewModel.values[spec.key].orEmpty()
                val secret = spec.kind == FieldKind.SECRET
                val multiline = spec.kind == FieldKind.MULTILINE
                val shown = !secret || visible[spec.key] == true
                FormRow(
                    icon = fieldIcon(spec.key, spec.kind),
                    label = spec.label,
                    value = value,
                    onValueChange = { viewModel.setValue(spec, it) },
                    fieldModifier = Modifier.testTag("field-${spec.key}"),
                    placeholder = spec.placeholder,
                    singleLine = !multiline,
                    minLines = if (multiline) 5 else 1,
                    monospace = secret && shown,
                    visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = multiline,
                        keyboardType = when {
                            // A keypad for PINs, unless this one already holds letters.
                            spec.input == InputKind.PIN -> if (value.all(Char::isDigit)) KeyboardType.NumberPassword else KeyboardType.Password
                            secret -> if (spec.input == InputKind.NUMBER) KeyboardType.NumberPassword else KeyboardType.Password
                            spec.input == InputKind.EMAIL -> KeyboardType.Email
                            spec.input == InputKind.URL -> KeyboardType.Uri
                            spec.input == InputKind.NUMBER || spec.input == InputKind.EXPIRY -> KeyboardType.Number
                            else -> KeyboardType.Text
                        },
                        imeAction = when {
                            multiline -> ImeAction.Default
                            index < template.fields.lastIndex -> ImeAction.Next
                            else -> ImeAction.Done
                        },
                    ),
                    trailing = if (secret) {
                        {
                            if (value.isEmpty()) {
                                // Keys and tokens are nearly always copied from somewhere else.
                                Text(
                                    "Paste",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = VaultTheme.palette.accent,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .clickable(role = Role.Button) {
                                            viewModel.paste(spec, clipboardText(context))
                                            if (viewModel.values[spec.key].orEmpty().isNotEmpty()) visible[spec.key] = true
                                        }
                                        .testTag("paste-${spec.key}")
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                )
                            }
                            if (spec.generator) {
                                IconButton(onClick = { generatorFor = spec }, modifier = Modifier.testTag("generate-${spec.key}")) {
                                    Icon(Icons.Rounded.AutoAwesome, contentDescription = "Generate a password", tint = VaultTheme.palette.accent)
                                }
                            }
                            if (value.isNotEmpty()) {
                                IconButton(onClick = { visible[spec.key] = !shown }) {
                                    Icon(
                                        if (shown) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                        contentDescription = if (shown) "Hide" else "Show",
                                        tint = VaultTheme.palette.accent,
                                    )
                                }
                            }
                        }
                    } else {
                        null
                    },
                )
                if (spec.suggestions.offeredFor(value)) {
                    SuggestionChips(
                        options = spec.suggestions,
                        selected = value,
                        onPick = { viewModel.setValue(spec, it) },
                        modifier = Modifier.padding(start = 54.dp, end = 16.dp, bottom = 12.dp),
                        tagPrefix = "suggest-${spec.key}",
                    )
                }
            }
        }

        template.guidance?.let { guidance ->
            Text(
                guidance,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 10.dp),
            )
        }

        if (viewModel.showNotes || viewModel.showTags) {
            Spacer(Modifier.height(16.dp))
            FormGroup {
                if (viewModel.showNotes) {
                    FormRow(
                        icon = fieldIcon("notes"),
                        label = "Notes",
                        value = viewModel.notes,
                        onValueChange = {
                            viewModel.notes = it.take(10_000)
                            viewModel.edited()
                        },
                        fieldModifier = Modifier.focusRequester(notesFocus).testTag("field-notes"),
                        singleLine = false,
                        minLines = 2,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                }
                if (viewModel.showNotes && viewModel.showTags) RowDivider()
                if (viewModel.showTags) {
                    FormRow(
                        icon = fieldIcon("tags"),
                        label = "Tags",
                        value = viewModel.tags,
                        onValueChange = {
                            viewModel.tags = it.take(300)
                            viewModel.edited()
                        },
                        fieldModifier = Modifier.focusRequester(tagsFocus).testTag("field-tags"),
                        placeholder = "work, travel, family",
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Done),
                    )
                    if (parsedTags.isNotEmpty()) {
                        TagRow(
                            tags = parsedTags,
                            modifier = Modifier.padding(start = 54.dp, end = 16.dp, bottom = 14.dp),
                            onRemove = { removed ->
                                viewModel.tags = parsedTags.filterNot { it == removed }.joinToString(", ")
                                viewModel.edited()
                            },
                        )
                    }
                }
            }
        }

        viewModel.customFields.forEachIndexed { index, draft ->
            Spacer(Modifier.height(16.dp))
            FormGroup {
                FormRow(
                    icon = Icons.Outlined.Tune,
                    label = "Field name",
                    value = draft.label,
                    onValueChange = { viewModel.updateCustomField(index, draft.copy(label = it.take(60))) },
                    fieldModifier = Modifier.testTag("custom-label-$index"),
                    placeholder = "Member number, Recovery code…",
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                    trailing = {
                        IconButton(onClick = { viewModel.removeCustomField(index) }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Remove field", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                )
                RowDivider()
                val shown = !draft.secret || visible[draft.key] == true
                FormRow(
                    icon = if (draft.secret) Icons.Outlined.Lock else Icons.Outlined.Description,
                    label = "Value",
                    value = draft.value,
                    onValueChange = { viewModel.updateCustomField(index, draft.copy(value = it.take(2_000))) },
                    fieldModifier = Modifier.testTag("custom-value-$index"),
                    visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = if (draft.secret) KeyboardType.Password else KeyboardType.Text, imeAction = ImeAction.Done),
                    trailing = if (draft.secret) {
                        {
                            IconButton(onClick = { visible[draft.key] = !shown }) {
                                Icon(if (shown) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, contentDescription = if (shown) "Hide" else "Show", tint = VaultTheme.palette.accent)
                            }
                        }
                    } else {
                        null
                    },
                )
                RowDivider()
                Row(Modifier.fillMaxWidth().padding(start = 54.dp, end = 14.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Keep it hidden", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    VaultSwitch(checked = draft.secret, onCheckedChange = { viewModel.updateCustomField(index, draft.copy(secret = it)) })
                }
            }
        }

        // Optional extras, one tap each, instead of empty rows on every form.
        Row(
            Modifier.padding(top = 16.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!viewModel.showNotes) {
                AddChip("Notes", Modifier.testTag("add-notes")) {
                    viewModel.addNotes()
                    justAdded = "notes"
                }
            }
            if (!viewModel.showTags) {
                AddChip("Tags", Modifier.testTag("add-tags")) {
                    viewModel.addTags()
                    justAdded = "tags"
                }
            }
            AddChip("Custom field", Modifier.testTag("add-custom-field"), viewModel::addCustomField)
        }
        Spacer(Modifier.height(20.dp))
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = "Discard changes?",
            body = "What you've typed here hasn't been saved.",
            confirmLabel = "Discard",
            destructive = true,
            onDismiss = { confirmDiscard = false },
            onConfirm = {
                confirmDiscard = false
                onBack()
            },
        )
    }

    generatorFor?.let { spec ->
        GeneratorSheet(
            onDismiss = { generatorFor = null },
            onUse = { password ->
                viewModel.setValue(spec, password)
                visible[spec.key] = true
                generatorFor = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeneratorSheet(onDismiss: () -> Unit, onUse: (String) -> Unit, viewModel: GeneratorViewModel = hiltViewModel()) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            GeneratorContent(viewModel)
            Spacer(Modifier.height(18.dp))
            PrimaryButton("Use this password", { onUse(viewModel.password) }, modifier = Modifier.testTag("use-generated"))
            QuietButton("Cancel", onDismiss)
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Suggestions stay on offer while the field is empty or holds one of them, and step aside once something else is typed. */
private fun List<String>.offeredFor(value: String): Boolean =
    isNotEmpty() && (value.isBlank() || any { it.equals(value.trim(), ignoreCase = true) })

private fun clipboardText(context: Context): String? = runCatching {
    context.getSystemService(ClipboardManager::class.java).primaryClip
        ?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)
        ?.coerceToText(context)
        ?.toString()
}.getOrNull()

/** A small "+ Something" pill that adds an optional part to the form. */
@Composable
private fun AddChip(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val palette = VaultTheme.palette
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(palette.card)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Add, contentDescription = "Add", tint = palette.accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, color = palette.accent, maxLines = 1)
    }
}
