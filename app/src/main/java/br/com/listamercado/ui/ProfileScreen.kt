package br.com.listamercado.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ProfileScreen(vm: AppViewModel, contentPadding: PaddingValues, onLeaveGroup: () -> Unit) {
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val group by vm.group.collectAsStateWithLifecycle()
    val savingPhoto by vm.savingPhoto.collectAsStateWithLifecycle()
    val photoToEdit by vm.photoToEdit.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current

    val myUid = vm.myUid
    val me = myUid?.let { profiles[it] }
    val currentName = me?.name?.ifBlank { null } ?: vm.userName
    var name by rememberSaveable { mutableStateOf(currentName) }
    // Quando o perfil chega do servidor, preenche o campo (se a pessoa não estiver editando)
    LaunchedEffect(currentName) { if (name.isBlank()) name = currentName }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.pickPhoto(uri)
    }
    fun openPicker() = pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    fun save() {
        focus.clearFocus()
        vm.saveName(name)
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Foto: toque para trocar
        Box(contentAlignment = Alignment.BottomEnd) {
            Avatar(
                name = currentName,
                photo = me?.photo,
                size = 120.dp,
                modifier = Modifier.clickable(enabled = !savingPhoto) { openPicker() },
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (savingPhoto) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Trocar foto",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { openPicker() },
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { openPicker() }, enabled = !savingPhoto) {
                Text(if (me?.photo == null) "Adicionar foto" else "Trocar foto")
            }
            if (me?.photo != null) {
                TextButton(onClick = vm::removePhoto, enabled = !savingPhoto) { Text("Remover foto") }
            }
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(30) },
            label = { Text("Seu nome") },
            supportingText = { Text("É o nome que aparece nos itens que você adiciona.") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { save() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { save() },
            enabled = name.isNotBlank() && name.trim() != currentName,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Salvar nome") }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        // Quem está no grupo
        Text(
            group?.name?.let { "Grupo \"$it\"" } ?: "Grupo",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth(),
        )
        val members = group?.memberNames.orEmpty().keys
            .map { uid ->
                val profile = profiles[uid]
                Triple(uid, profile?.name?.ifBlank { null } ?: group?.memberNames?.get(uid).orEmpty(), profile?.photo)
            }
            .sortedWith(compareBy({ it.first != myUid }, { it.second.lowercase() }))
        members.forEach { (uid, memberName, photo) ->
            ListItem(
                leadingContent = { Avatar(memberName, photo, 40.dp) },
                headlineContent = { Text(memberName.ifBlank { "Sem nome" }) },
                supportingContent = if (uid == myUid) {
                    { Text("Você") }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        group?.inviteCode?.let {
            Text(
                "Código de convite: $it",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        TextButton(
            onClick = onLeaveGroup,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Sair do grupo", color = MaterialTheme.colorScheme.error) }
    }

    photoToEdit?.let { bitmap ->
        PhotoCropDialog(
            source = bitmap,
            onCancel = vm::cancelPhotoEdit,
            onConfirm = vm::confirmPhotoCrop,
        )
    }
}
