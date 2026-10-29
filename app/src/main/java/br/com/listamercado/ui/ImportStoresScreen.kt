package br.com.listamercado.ui

import android.location.Location
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.listamercado.data.OsmStore
import br.com.listamercado.data.Store

private const val MAX_GEOFENCES = 100

/** Distância abaixo da qual consideramos que é o mesmo mercado já cadastrado */
private const val SAME_PLACE_METERS = 60f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportStoresScreen(vm: AppViewModel, onClose: () -> Unit) {
    val state by vm.importState.collectAsStateWithLifecycle()
    val existing by vm.stores.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current

    var city by rememberSaveable { mutableStateOf("Descalvado") }
    var uf by rememberSaveable { mutableStateOf("SP") }
    var radius by rememberSaveable { mutableIntStateOf(150) }

    val results = (state as? ImportState.Results)?.stores.orEmpty()
    val selected = remember { mutableStateMapOf<String, Boolean>() }

    fun isRegistered(s: OsmStore) = existing.any { it.osmId == s.osmId || distance(it, s) < SAME_PLACE_METERS }

    // Ao chegar um resultado novo: já marca os que têm nome e ainda não estão cadastrados
    LaunchedEffect(results) {
        selected.clear()
        results.forEach { selected[it.osmId] = it.name.isNotBlank() && !isRegistered(it) }
    }

    fun search() {
        focus.clearFocus()
        vm.searchCityStores(city, uf)
    }

    val toImport = results.filter { selected[it.osmId] == true && !isRegistered(it) }
    val total = existing.size + toImport.size

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Importar mercados") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
            )
        },
        bottomBar = {
            if (results.isNotEmpty()) {
                Column(
                    Modifier
                        .padding(16.dp)
                        .imePadding(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Avisar num raio de", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(100, 150, 250).forEach { option ->
                            FilterChip(
                                selected = radius == option,
                                onClick = { radius = option },
                                label = { Text("$option m") },
                            )
                        }
                    }
                    if (total > MAX_GEOFENCES) {
                        Text(
                            "O Android monitora no máximo $MAX_GEOFENCES mercados. " +
                                "Desmarque alguns (ficariam $total).",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Button(
                        onClick = {
                            vm.importStores(toImport.map { it.withDisplayName() }, radius)
                            onClose()
                        },
                        enabled = toImport.isNotEmpty() && total <= MAX_GEOFENCES,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            when (toImport.size) {
                                0 -> "Nenhum selecionado"
                                1 -> "Importar 1 mercado"
                                else -> "Importar ${toImport.size} mercados"
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it.take(60) },
                    label = { Text("Cidade") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Search,
                    ),
                    keyboardActions = KeyboardActions(onSearch = { search() }),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = uf,
                    onValueChange = { input -> uf = input.filter { it.isLetter() }.uppercase().take(2) },
                    label = { Text("UF") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Search,
                    ),
                    keyboardActions = KeyboardActions(onSearch = { search() }),
                    modifier = Modifier.width(76.dp),
                )
            }
            Button(
                onClick = { search() },
                enabled = city.isNotBlank() && uf.length == 2 && state !is ImportState.Loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                if (state is ImportState.Loading) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Buscar mercados")
                }
            }

            when (val s = state) {
                is ImportState.Error -> Text(
                    s.message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )
                is ImportState.Results -> ResultsList(
                    results = s.stores,
                    selected = selected,
                    isRegistered = ::isRegistered,
                    modifier = Modifier.weight(1f),
                )
                ImportState.Loading -> Text(
                    "Buscando no OpenStreetMap… pode levar até um minuto.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
                ImportState.Idle -> Text(
                    "Os mercados vêm do OpenStreetMap, um mapa colaborativo. Algum pode estar " +
                        "faltando ou desatualizado; esses você cadastra pelo mapa, no botão \"Mercado\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun ResultsList(
    results: List<OsmStore>,
    selected: MutableMap<String, Boolean>,
    isRegistered: (OsmStore) -> Boolean,
    modifier: Modifier = Modifier,
) {
    if (results.isEmpty()) {
        Text(
            "Nenhum mercado encontrado. Confira o nome da cidade e a UF " +
                "(ex.: Descalvado / SP). Se estiver certo, a cidade ainda não tem mercados " +
                "no OpenStreetMap; cadastre pelo mapa.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier.padding(16.dp),
        )
        return
    }

    val selectable = results.filterNot(isRegistered)
    LazyColumn(modifier.padding(top = 8.dp)) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${results.size} encontrados",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { selectable.forEach { selected[it.osmId] = true } }) { Text("Todos") }
                TextButton(onClick = { selectable.forEach { selected[it.osmId] = false } }) { Text("Nenhum") }
            }
            HorizontalDivider()
        }
        items(results, key = { it.osmId }) { store ->
            val registered = isRegistered(store)
            val checked = registered || selected[store.osmId] == true
            ListItem(
                modifier = Modifier.clickable(enabled = !registered) {
                    selected[store.osmId] = !checked
                },
                leadingContent = {
                    Checkbox(
                        checked = checked,
                        enabled = !registered,
                        onCheckedChange = { selected[store.osmId] = it },
                    )
                },
                headlineContent = {
                    Text(store.name.ifBlank { "Sem nome" })
                },
                supportingContent = {
                    Text(if (registered) "${store.kind} · já cadastrado" else store.kind)
                },
            )
        }
    }
}

private fun OsmStore.withDisplayName() = if (name.isBlank()) copy(name = kind) else this

private fun distance(a: Store, b: OsmStore): Float {
    val out = FloatArray(1)
    Location.distanceBetween(a.lat, a.lng, b.lat, b.lng, out)
    return out[0]
}
