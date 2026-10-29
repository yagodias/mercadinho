package br.com.listamercado.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.listamercado.data.Store

@Composable
fun StoresScreen(vm: AppViewModel, contentPadding: PaddingValues) {
    val stores by vm.stores.collectAsStateWithLifecycle()
    var toDelete by remember { mutableStateOf<Store?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        if (stores.isEmpty()) {
            item {
                Text(
                    "Nenhum mercado cadastrado.\nToque em \"Mercado\" e marque no mapa os " +
                        "mercados que vocês frequentam. Todo o grupo recebe o aviso neles.",
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
            ListItem(
                leadingContent = {
                    Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                headlineContent = { Text(store.name) },
                supportingContent = { Text("Aviso num raio de ${store.radius} m") },
                trailingContent = {
                    IconButton(onClick = { toDelete = store }) {
                        Icon(Icons.Default.Delete, contentDescription = "Remover ${store.name}")
                    }
                },
            )
        }
    }

    toDelete?.let { store ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Remover ${store.name}?") },
            text = { Text("Ninguém do grupo vai mais receber aviso neste mercado.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteStore(store)
                    toDelete = null
                }) { Text("Remover") }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text("Cancelar") }
            },
        )
    }
}
