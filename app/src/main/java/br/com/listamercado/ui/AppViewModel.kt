package br.com.listamercado.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.listamercado.Notifier
import br.com.listamercado.data.AvatarCodec
import br.com.listamercado.data.Group
import br.com.listamercado.data.Item
import br.com.listamercado.data.OsmImporter
import br.com.listamercado.data.OsmStore
import br.com.listamercado.data.Prefs
import br.com.listamercado.data.Profile
import br.com.listamercado.data.Repository
import br.com.listamercado.data.Store
import br.com.listamercado.geo.GeofenceManager
import br.com.listamercado.geo.GeofenceStatus
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ImportState {
    data object Idle : ImportState

    data object Loading : ImportState

    data class Results(
        val stores: List<OsmStore>,
    ) : ImportState

    data class Error(
        val message: String,
    ) : ImportState
}

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(
    app: Application,
) : AndroidViewModel(app) {
    private val prefs = Prefs(app)
    private val repo = Repository()

    private val _groupId = MutableStateFlow(prefs.groupId)
    val groupId: StateFlow<String?> = _groupId.asStateFlow()

    private val _group = MutableStateFlow<Group?>(null)
    val group: StateFlow<Group?> = _group.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val userName: String get() = prefs.userName.orEmpty()

    val items: StateFlow<List<Item>> =
        _groupId
            .flatMapLatest { gid -> if (gid == null) flowOf(emptyList()) else repo.observeItems(gid) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Sem valor inicial: só sincroniza os avisos depois de carregar os mercados de verdade
    private val storesFlow =
        _groupId
            .flatMapLatest { gid -> if (gid == null) flowOf(emptyList()) else repo.observeStores(gid) }
            .shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)

    val stores: StateFlow<List<Store>> =
        storesFlow.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Perfis do grupo (uid -> perfil), usados para mostrar nome e foto de quem adicionou cada item */
    val profiles: StateFlow<Map<String, Profile>> =
        _groupId
            .flatMapLatest { gid -> if (gid == null) flowOf(emptyMap()) else repo.observeProfiles(gid) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val myUid: String? get() = repo.currentUid

    private val _savingPhoto = MutableStateFlow(false)
    val savingPhoto: StateFlow<Boolean> = _savingPhoto.asStateFlow()

    /** Foto escolhida aguardando o ajuste de posição/zoom; null = tela de ajuste fechada */
    private val _photoToEdit = MutableStateFlow<Bitmap?>(null)
    val photoToEdit: StateFlow<Bitmap?> = _photoToEdit.asStateFlow()

    init {
        viewModelScope.launch {
            _groupId.collectLatest { gid ->
                _group.value = null
                if (gid == null) return@collectLatest
                runCatching { repo.ensureSignedIn() }
                // Em paralelo, para não atrasar a lista se a internet estiver lenta
                viewModelScope.launch { runCatching { repo.ensureProfile(gid, userName) } }
                repo.observeGroup(gid).collect { g ->
                    if (g == null) onRemovedFromGroup() else _group.value = g
                }
            }
        }
        viewModelScope.launch {
            storesFlow.distinctUntilChanged().collect { GeofenceManager.sync(getApplication(), it) }
        }
    }

    // ---------- Grupo ----------

    fun createGroup(
        groupName: String,
        name: String,
    ) = runBusy {
        val cleanName = name.trim()
        val gid = repo.createGroup(groupName.trim().ifBlank { "Casa" }, cleanName)
        startSession(gid, cleanName)
    }

    fun joinGroup(
        code: String,
        name: String,
    ) = runBusy {
        val cleanName = name.trim()
        val gid = repo.joinGroup(code, cleanName)
        startSession(gid, cleanName)
    }

    fun leaveGroup() {
        val gid = _groupId.value ?: return
        repo.leaveGroup(gid)
        prefs.groupId = null
        _groupId.value = null
    }

    private fun startSession(
        gid: String,
        name: String,
    ) {
        prefs.userName = name
        prefs.groupId = gid
        _groupId.value = gid
    }

    private fun onRemovedFromGroup() {
        prefs.groupId = null
        _groupId.value = null
        _message.value = "Você não faz mais parte deste grupo."
    }

    // ---------- Perfil ----------

    fun saveName(name: String) {
        val gid = _groupId.value ?: return
        val clean = name.trim().take(30)
        if (clean.isEmpty()) return
        prefs.userName = clean
        repo.saveProfileName(gid, clean)
        _message.value = "Nome atualizado"
    }

    /** Abre a foto escolhida na tela de ajuste (posição e zoom). */
    fun pickPhoto(uri: Uri) {
        if (_savingPhoto.value) return
        _savingPhoto.value = true
        viewModelScope.launch {
            try {
                _photoToEdit.value = withContext(Dispatchers.Default) {
                    AvatarCodec.loadForEditing(getApplication(), uri)
                }
            } catch (e: Exception) {
                _message.value = "Não consegui abrir essa imagem. Tente outra."
            } finally {
                _savingPhoto.value = false
            }
        }
    }

    fun cancelPhotoEdit() {
        _photoToEdit.value = null
    }

    /** Salva a área escolhida na tela de ajuste ([area] em pixels da foto aberta). */
    fun confirmPhotoCrop(area: Rect) {
        val gid = _groupId.value ?: return
        val source = _photoToEdit.value ?: return
        _photoToEdit.value = null
        _savingPhoto.value = true
        viewModelScope.launch {
            try {
                val encoded = withContext(Dispatchers.Default) { AvatarCodec.encodeCrop(source, area) }
                repo.saveProfilePhoto(gid, userName, encoded)
            } catch (e: Exception) {
                _message.value = "Não consegui salvar a foto. Tente de novo."
            } finally {
                _savingPhoto.value = false
            }
        }
    }

    fun removePhoto() {
        val gid = _groupId.value ?: return
        repo.saveProfilePhoto(gid, userName, null)
    }

    // ---------- Itens ----------

    fun addItem(name: String) {
        val gid = _groupId.value ?: return
        val clean = name.trim().take(100)
        if (clean.isEmpty()) return
        repo.addItem(gid, clean, userName)
    }

    fun setChecked(
        item: Item,
        checked: Boolean,
    ) {
        val gid = _groupId.value ?: return
        repo.setChecked(gid, item.id, checked)
    }

    fun deleteItem(item: Item) {
        val gid = _groupId.value ?: return
        repo.deleteItems(gid, listOf(item.id))
    }

    fun clearChecked() {
        val gid = _groupId.value ?: return
        repo.deleteItems(gid, items.value.filter { it.checked }.map { it.id })
    }

    // ---------- Mercados ----------

    fun addStore(
        name: String,
        lat: Double,
        lng: Double,
        radius: Int,
    ) {
        val gid = _groupId.value ?: return
        repo.addStore(gid, name.trim().take(60), lat, lng, radius, userName)
    }

    // ---------- Importar do OpenStreetMap ----------

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    fun searchCityStores(
        city: String,
        uf: String,
    ) {
        if (_importState.value is ImportState.Loading) return
        _importState.value = ImportState.Loading
        viewModelScope.launch {
            _importState.value =
                try {
                    ImportState.Results(OsmImporter.searchCity(city, uf))
                } catch (e: Exception) {
                    ImportState.Error(e.message ?: "Algo deu errado.")
                }
        }
    }

    fun importStores(
        selected: List<OsmStore>,
        radius: Int,
    ) {
        val gid = _groupId.value ?: return
        if (selected.isEmpty()) return
        repo.addStores(gid, selected, radius, userName)
        _message.value = if (selected.size == 1) "1 mercado importado" else "${selected.size} mercados importados"
        _importState.value = ImportState.Idle
    }

    fun resetImport() {
        _importState.value = ImportState.Idle
    }

    fun deleteStores(storeIds: Collection<String>) {
        val gid = _groupId.value ?: return
        if (storeIds.isEmpty()) return
        repo.deleteStores(gid, storeIds)
        _message.value = if (storeIds.size == 1) "Mercado removido" else "${storeIds.size} mercados removidos"
    }

    fun updateStore(store: Store, name: String, radius: Int) {
        val gid = _groupId.value ?: return
        val clean = name.trim().take(60).ifBlank { store.name }
        repo.updateStore(gid, store.id, clean, radius)
    }

    fun setStoresRadius(storeIds: Collection<String>, radius: Int) {
        val gid = _groupId.value ?: return
        if (storeIds.isEmpty()) return
        repo.updateStoresRadius(gid, storeIds, radius)
        _message.value = "Raio de ${storeIds.size} mercado(s) alterado para $radius m"
    }

    // ---------- Diagnóstico dos avisos ----------

    val geofenceStatus: StateFlow<GeofenceStatus> = GeofenceManager.status

    /** Mostra agora a notificação que apareceria no mercado, com os itens atuais. */
    fun testAlert() {
        val gid = _groupId.value ?: return
        val app = getApplication<Application>()
        if (!Notifier.canNotify(app)) {
            _message.value = "As notificações do app estão bloqueadas. Libere no cartão da aba Lista " +
                "ou em Configurações > Apps > Lista de Mercado > Notificações."
            return
        }
        viewModelScope.launch {
            val items = runCatching { repo.fetchPendingItemNames(gid) }.getOrDefault(emptyList())
            val storeName = stores.value.firstOrNull()?.name ?: "Mercado"
            Notifier.showStoreAlert(app, storeName, items, test = true)
            _message.value = "Aviso de teste enviado. Puxe a barra de notificações para ver."
        }
    }

    /** Chamado quando as permissões mudam, para (re)ativar os avisos. */
    fun refreshGeofences() {
        val current = storesFlow.replayCache.firstOrNull() ?: return
        GeofenceManager.sync(getApplication(), current)
    }

    fun consumeMessage() {
        _message.value = null
    }

    // ---------- Util ----------

    private fun runBusy(block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                _message.value = friendlyError(e)
            } finally {
                _busy.value = false
            }
        }
    }

    private fun friendlyError(e: Exception): String =
        when {
            e is FirebaseNetworkException ->
                "Sem conexão com a internet. Tente de novo."
            e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.UNAVAILABLE ->
                "Sem conexão com a internet. Tente de novo."
            e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "O Firestore recusou o acesso. Confira se as regras de segurança foram publicadas (veja o README)."
            e is FirebaseAuthException ->
                "Falha no login. Confira se o login anônimo está ativado no Firebase."
            e is IllegalArgumentException ->
                e.message ?: "Dados inválidos."
            else -> e.message ?: "Algo deu errado."
        }
}
