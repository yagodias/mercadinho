package br.com.listamercado.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.listamercado.data.Store

private val RADIUS_CHOICES = listOf(100, 150, 250, 400)

/**
 * Lista de mercados do grupo.
 * - Toque num mercado: edita nome e raio.
 * - Toque e segure (ou "Selecionar"): marca vários para excluir ou mudar o raio de uma vez.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StoresScreen(
    vm: AppViewModel,
    contentPadding: PaddingValues,
    onImport: () -> Unit,
) {
    val stores by vm.stores.collectAsStateWithLifecycle()

    val selected = remember { mutableStateListOf<String>() }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Store?>(null) }
    var confirmDelete by remember { mutableStateOf<List<String>?>(null) }
    var bulkRadius by remember { mutableStateOf(false) }

    // Mercado apagado por outra pessoa sai da seleção
    LaunchedEffect(stores) {
        val ids = stores.map { it.id }.toSet()
        val keep = selected.filter { it in ids }
        if (keep.size != selected.size) {
            selected.clear()
            selected.addAll(keep)
        }
    }

    fun exitSelection() {
        selecting = false
        selected.clear()
    }

    BackHandler(enabled = selecting) { exitSelection() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        if (selecting) {
            item(key = "selection_bar") {
                SelectionBar(
                    count = selected.size,
                    allSelected = selected.size == stores.size && stores.isNotEmpty(),
                    onToggleAll = {
                        if (selected.size == stores.size) {
                            selected.clear()
                        } else {
                            selected.clear()
                            selected.addAll(stores.map { it.id })
                        }
                    },
                    onRadius = { bulkRadius = true },
                    onDelete = { confirmDelete = selected.toList() },
                    onClose = ::exitSelection,
                )
            }
        } else {
            item(key = "status") {
                AlertsStatusCard(vm, Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp))
            }
            item(key = "import") {
                OutlinedButton(
                    onClick = onImport,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Importar mercados da cidade")
                }
            }
            if (stores.isNotEmpty()) {
                item(key = "header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (stores.size == 1) "1 mercado" else "${stores.size} mercados",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { selecting = true }) { Text("Selecionar") }
                    }
                }
            }
        }

        if (stores.isEmpty()) {
            item(key = "empty") {
                Text(
                    "Nenhum mercado cadastrado.\nImporte os mercados da sua cidade ou toque em " +
                        "\"Mercado\" para marcar um no mapa. Todo o grupo recebe o aviso neles.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                )
            }
        }

        items(stores, key = { it.id }) { store ->
            val isSelected = store.id in selected
            fun toggle() {
                if (isSelected) selected.remove(store.id) else selected.add(store.id)
            }
            ListItem(
                modifier = Modifier.combinedClickable(
                    onClick = { if (selecting) toggle() else editing = store },
                    onLongClick = {
                        if (!selecting) selecting = true
                        toggle()
                    },
                ),
                leadingContent = {
                    if (selecting) {
                        Checkbox(checked = isSelected, onCheckedChange = { toggle() })
                    } else {
                        Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                },
                headlineContent = { Text(store.name) },
                supportingContent = { Text("Aviso num raio de ${store.radius} m") },
                trailingContent = if (selecting) {
                    null
                } else {
                    {
                        Row {
                            IconButton(onClick = { editing = store }) {
                                Icon(Icons.Default.Edit, contentDescription = "Editar ${store.name}")
                            }
                            IconButton(onClick = { confirmDelete = listOf(store.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remover ${store.name}")
                            }
                        }
                    }
                },
            )
        }
    }

    editing?.let { store ->
        EditStoreDialog(
            store = store,
            onDismiss = { editing = null },
            onSave = { name, radius ->
                vm.updateStore(store, name, radius)
                editing = null
            },
        )
    }

    if (bulkRadius) {
        RadiusDialog(
            count = selected.size,
            onDismiss = { bulkRadius = false },
            onApply = { radius ->
                vm.setStoresRadius(selected.toList(), radius)
                bulkRadius = false
                exitSelection()
            },
        )
    }

    confirmDelete?.let { ids ->
        val single = ids.singleOrNull()?.let { id -> stores.firstOrNull { it.id == id } }
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(if (single != null) "Remover ${single.name}?" else "Remover ${ids.size} mercados?") },
            text = {
                Text(
                    if (single != null) {
                        "Ninguém do grupo vai mais receber aviso neste mercado."
                    } else {
                        "Ninguém do grupo vai mais receber aviso nesses mercados."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteStores(ids)
                    confirmDelete = null
                    if (selecting) exitSelection()
                }) { Text("Remover") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    allSelected: Boolean,
    onToggleAll: () -> Unit,
    onRadius: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Cancelar seleção")
                }
                Text(
                    when (count) {
                        0 -> "Nenhum selecionado"
                        1 -> "1 selecionado"
                        else -> "$count selecionados"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onToggleAll) { Text(if (allSelected) "Nenhum" else "Todos") }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
            ) {
                OutlinedButton(onClick = onRadius, enabled = count > 0) { Text("Mudar raio") }
                OutlinedButton(onClick = onDelete, enabled = count > 0) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Excluir")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RadiusChips(radius: Int, onChange: (Int) -> Unit) {
    val options = (RADIUS_CHOICES + radius).distinct().sorted()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = radius == option,
                onClick = { onChange(option) },
                label = { Text("$option m") },
            )
        }
    }
}

@Composable
private fun EditStoreDialog(store: Store, onDismiss: () -> Unit, onSave: (String, Int) -> Unit) {
    var name by rememberSaveable(store.id) { mutableStateOf(store.name) }
    var radius by rememberSaveable(store.id) { mutableIntStateOf(store.radius) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar mercado") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    label = { Text("Nome") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Avisar num raio de", style = MaterialTheme.typography.labelLarge)
                RadiusChips(radius) { radius = it }
                Text(
                    "Raio maior avisa mais cedo, ainda no caminho; menor evita avisos de quem só passa perto.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, radius) }, enabled = name.isNotBlank()) { Text("Salvar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

@Composable
private fun RadiusDialog(count: Int, onDismiss: () -> Unit, onApply: (Int) -> Unit) {
    var radius by rememberSaveable { mutableIntStateOf(250) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (count == 1) "Raio de 1 mercado" else "Raio de $count mercados") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Avisar num raio de", style = MaterialTheme.typography.labelLarge)
                RadiusChips(radius) { radius = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(radius) }) { Text("Aplicar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}
