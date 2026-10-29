package br.com.listamercado.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.listamercado.data.Group

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: AppViewModel) {
    val group by vm.group.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val profiles by vm.profiles.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var addingStore by rememberSaveable { mutableStateOf(false) }
    var importingStores by rememberSaveable { mutableStateOf(false) }
    var showInvite by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        vm.consumeMessage()
    }

    if (importingStores) {
        BackHandler { importingStores = false }
        ImportStoresScreen(
            vm = vm,
            onClose = {
                importingStores = false
                tab = 1
            },
        )
        return
    }

    if (addingStore) {
        BackHandler { addingStore = false }
        MapPickerScreen(
            onCancel = { addingStore = false },
            onSave = { name, lat, lng, radius ->
                vm.addStore(name, lat, lng, radius)
                addingStore = false
                tab = 1
            },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(group?.name ?: "Lista de Mercado") },
                actions = {
                    IconButton(onClick = { showInvite = true }, enabled = group != null) {
                        Icon(Icons.Default.Share, contentDescription = "Convidar para o grupo")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Mais opções")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Importar mercados da cidade") },
                                onClick = {
                                    menuOpen = false
                                    importingStores = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Sair do grupo") },
                                onClick = {
                                    menuOpen = false
                                    confirmLeave = true
                                },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.ShoppingCart, contentDescription = null) },
                    label = { Text("Lista") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.Place, contentDescription = null) },
                    label = { Text("Mercados") },
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = {
                        val me = vm.myUid?.let { profiles[it] }
                        Avatar(me?.name ?: vm.userName, me?.photo, 24.dp)
                    },
                    label = { Text("Perfil") },
                )
            }
        },
        floatingActionButton = {
            if (tab == 1) {
                ExtendedFloatingActionButton(
                    onClick = { addingStore = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Mercado") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when (tab) {
            0 -> ListScreen(vm, padding)
            1 -> StoresScreen(vm, padding, onImport = { importingStores = true })
            else -> ProfileScreen(vm, padding, onLeaveGroup = { confirmLeave = true })
        }
    }

    val currentGroup = group
    if (showInvite && currentGroup != null) {
        InviteDialog(currentGroup, onDismiss = { showInvite = false })
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Sair do grupo?") },
            text = {
                Text(
                    "Você deixa de ver esta lista e de receber avisos dos mercados dela. " +
                        "Para voltar, basta usar o código de convite.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    vm.leaveGroup()
                }) { Text("Sair") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun InviteDialog(group: Group, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Person, contentDescription = null) },
        title = { Text("Convidar para \"${group.name}\"") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("No app, a pessoa escolhe \"Tenho um código\" e digita:")
                Text(
                    group.inviteCode,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp,
                    letterSpacing = 6.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (group.memberNames.isNotEmpty()) {
                    Text(
                        "No grupo: " + group.memberNames.values.sorted().joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { shareInvite(context, group) }) { Text("Compartilhar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Fechar") }
        },
    )
}

private fun shareInvite(context: Context, group: Group) {
    val text = "Entra na nossa lista de mercado \"${group.name}\"! " +
        "No app Lista de Mercado, toque em \"Tenho um código\" e use: ${group.inviteCode}"
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Compartilhar convite"))
}
