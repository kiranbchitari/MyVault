package com.myvault.app.ui.items

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.platform.testTag
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
import com.myvault.app.ui.components.TagRow
import com.myvault.app.ui.components.VaultSwitch
import com.myvault.app.ui.components.tint
import com.myvault.app.ui.components.ErrorText
import com.myvault.app.ui.components.PrimaryButton
import com.myvault.app.ui.components.QuietButton
import com.myvault.app.ui.components.ScreenColumn
import com.myvault.app.ui.theme.VaultTheme
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
        }
    }

    fun edited() {
        dirty = true
        error = null
    }

    fun setValue(spec: FieldSpec, value: String) {
        val filtered = if (spec.input == InputKind.NUMBER) value.filter { it.isDigit() || it == ' ' } else value
        values[spec.key] = filtered.take(spec.maxLength)
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
    LaunchedEffect(Unit) { viewModel.start(itemId, newType) }
    val template = viewModel.template
    var confirmDiscard by remember { mutableStateOf(false) }
    var generatorFor by remember { mutableStateOf<FieldSpec?>(null) }
    val visible = remember { mutableStateMapOf<String, Boolean>() }
    val leave = { if (viewModel.dirty) confirmDiscard = true else onBack() }

    BackHandler(enabled = viewModel.dirty) { confirmDiscard = true }

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
    ) {
        if (viewModel.type.isCard) {
            CardPreview(
                nickname = viewModel.title,
                network = viewModel.values["network"].orEmpty(),
                lastFour = viewModel.values["last4"].orEmpty(),
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp),
                tint = viewModel.type.tint(),
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
                fieldModifier = Modifier.testTag("field-title"),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
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
                )
            }
            template.fields.forEach { spec ->
                RowDivider()
                val secret = spec.kind == FieldKind.SECRET
                val shown = !secret || visible[spec.key] == true
                FormRow(
                    icon = fieldIcon(spec.key, spec.kind),
                    label = spec.label,
                    value = viewModel.values[spec.key].orEmpty(),
                    onValueChange = { viewModel.setValue(spec, it) },
                    fieldModifier = Modifier.testTag("field-${spec.key}"),
                    placeholder = spec.placeholder,
                    singleLine = spec.kind != FieldKind.MULTILINE,
                    minLines = if (spec.kind == FieldKind.MULTILINE) 5 else 1,
                    monospace = secret && shown,
                    visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = spec.kind == FieldKind.MULTILINE,
                        keyboardType = when {
                            secret -> if (spec.input == InputKind.NUMBER) KeyboardType.NumberPassword else KeyboardType.Password
                            spec.input == InputKind.EMAIL -> KeyboardType.Email
                            spec.input == InputKind.URL -> KeyboardType.Uri
                            spec.input == InputKind.NUMBER -> KeyboardType.Number
                            else -> KeyboardType.Text
                        },
                    ),
                    trailing = if (secret) {
                        {
                            if (spec.generator) {
                                IconButton(onClick = { generatorFor = spec }, modifier = Modifier.testTag("generate-${spec.key}")) {
                                    Icon(Icons.Rounded.AutoAwesome, contentDescription = "Generate a password", tint = VaultTheme.palette.accent)
                                }
                            }
                            IconButton(onClick = { visible[spec.key] = !shown }) {
                                Icon(
                                    if (shown) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (shown) "Hide" else "Show",
                                    tint = VaultTheme.palette.accent,
                                )
                            }
                        }
                    } else {
                        null
                    },
                )
            }
            RowDivider()
            FormRow(
                icon = fieldIcon("notes"),
                label = "Notes",
                value = viewModel.notes,
                onValueChange = {
                    viewModel.notes = it.take(10_000)
                    viewModel.edited()
                },
                fieldModifier = Modifier.testTag("field-notes"),
                singleLine = false,
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            RowDivider()
            FormRow(
                icon = fieldIcon("tags"),
                label = "Tags",
                value = viewModel.tags,
                onValueChange = {
                    viewModel.tags = it.take(300)
                    viewModel.edited()
                },
                fieldModifier = Modifier.testTag("field-tags"),
                placeholder = "work, travel, family",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
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

        template.guidance?.let { guidance ->
            Text(
                guidance,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 10.dp),
            )
        }

        viewModel.customFields.forEachIndexed { index, draft ->
            Spacer(Modifier.height(16.dp))
            FormGroup {
                FormRow(
                    icon = Icons.Outlined.Tune,
                    label = "Custom field name",
                    value = draft.label,
                    onValueChange = { viewModel.updateCustomField(index, draft.copy(label = it.take(60))) },
                    fieldModifier = Modifier.testTag("custom-label-$index"),
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
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = if (draft.secret) KeyboardType.Password else KeyboardType.Text),
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
                    Text("Treat as secret", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    VaultSwitch(checked = draft.secret, onCheckedChange = { viewModel.updateCustomField(index, draft.copy(secret = it)) })
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        QuietButton("Add a custom field", viewModel::addCustomField, modifier = Modifier.testTag("add-custom-field"))

        Spacer(Modifier.height(6.dp))
        ErrorText(viewModel.error, Modifier.padding(bottom = 10.dp, start = 4.dp))
        PrimaryButton(
            text = if (viewModel.saving) "Encrypting…" else "Save",
            onClick = { viewModel.save(onSaved) },
            loading = viewModel.saving,
            modifier = Modifier.testTag("save"),
        )
        Spacer(Modifier.height(24.dp))
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
