package br.com.listamercado.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.listamercado.data.Item
import br.com.listamercado.data.Profile

@Composable
fun ListScreen(vm: AppViewModel, contentPadding: PaddingValues) {
    val allItems by vm.items.collectAsStateWithLifecycle()
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val pending = allItems.filter { !it.checked }
    val bought = allItems.filter { it.checked }
    var text by rememberSaveable { mutableStateOf("") }

    fun submit() {
        if (text.isBlank()) return
        vm.addItem(text)
        text = ""
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        AlertsSetupCard(
            onStatusChanged = vm::refreshGeofences,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(100) },
                placeholder = { Text("Adicionar item…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.weight(1f),
            )
            FilledIconButton(onClick = { submit() }, enabled = text.isNotBlank()) {
                Icon(Icons.Default.Add, contentDescription = "Adicionar")
            }
        }

        LazyColumn(Modifier.weight(1f)) {
            if (allItems.isEmpty()) {
                item {
                    Text(
                        "A lista está vazia.\nO que for adicionado aqui aparece para todo o grupo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                    )
                }
            }

            items(pending, key = { it.id }) { item ->
                ItemRow(
                    item = item,
                    author = item.addedByUid?.let { profiles[it] },
                    onToggle = { vm.setChecked(item, true) },
                    onDelete = { vm.deleteItem(item) },
                )
            }

            if (bought.isNotEmpty()) {
                item(key = "header_bought") {
                    HorizontalDivider(Modifier.padding(top = 8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Comprados (${bought.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = vm::clearChecked) { Text("Limpar") }
                    }
                }
                items(bought, key = { it.id }) { item ->
                    ItemRow(
                        item = item,
                        author = item.addedByUid?.let { profiles[it] },
                        onToggle = { vm.setChecked(item, false) },
                        onDelete = { vm.deleteItem(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ItemRow(item: Item, author: Profile?, onToggle: () -> Unit, onDelete: () -> Unit) {
    // Nome atual do perfil; itens antigos (sem perfil) usam o nome gravado na hora
    val authorName = author?.name?.ifBlank { null } ?: item.addedBy
    ListItem(
        modifier = Modifier.clickable(onClick = onToggle),
        leadingContent = {
            Checkbox(checked = item.checked, onCheckedChange = { onToggle() })
        },
        headlineContent = {
            Text(
                item.name,
                textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                color = if (item.checked) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        },
        supportingContent = if (authorName.isNotBlank()) {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(authorName, author?.photo, 18.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(authorName)
                }
            }
        } else {
            null
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Close, contentDescription = "Remover ${item.name}")
            }
        },
    )
}
