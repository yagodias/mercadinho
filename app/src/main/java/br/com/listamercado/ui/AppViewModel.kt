package br.com.listamercado.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.listamercado.data.Group
import br.com.listamercado.data.Item
import br.com.listamercado.data.Prefs
import br.com.listamercado.data.Repository
import br.com.listamercado.data.Store
import br.com.listamercado.geo.GeofenceManager
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException
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

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {

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

    val items: StateFlow<List<Item>> = _groupId
        .flatMapLatest { gid -> if (gid == null) flowOf(emptyList()) else repo.observeItems(gid) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Sem valor inicial: só sincroniza os avisos depois de carregar os mercados de verdade
    private val storesFlow = _groupId
        .flatMapLatest { gid -> if (gid == null) flowOf(emptyList()) else repo.observeStores(gid) }
        .shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)

    val stores: StateFlow<List<Store>> =
        storesFlow.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            _groupId.collectLatest { gid ->
                _group.value = null
                if (gid == null) return@collectLatest
                runCatching { repo.ensureSignedIn() }
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

    fun createGroup(groupName: String, name: String) = runBusy {
        val cleanName = name.trim()
        val gid = repo.createGroup(groupName.trim().ifBlank { "Casa" }, cleanName)
        startSession(gid, cleanName)
    }

    fun joinGroup(code: String, name: String) = runBusy {
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

    private fun startSession(gid: String, name: String) {
        prefs.userName = name
        prefs.groupId = gid
        _groupId.value = gid
    }

    private fun onRemovedFromGroup() {
        prefs.groupId = null
        _groupId.value = null
        _message.value = "Você não faz mais parte deste grupo."
    }

    // ---------- Itens ----------

    fun addItem(name: String) {
        val gid = _groupId.value ?: return
        val clean = name.trim().take(100)
        if (clean.isEmpty()) return
        repo.addItem(gid, clean, userName)
    }

    fun setChecked(item: Item, checked: Boolean) {
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

    fun addStore(name: String, lat: Double, lng: Double, radius: Int) {
        val gid = _groupId.value ?: return
        repo.addStore(gid, name.trim().take(60), lat, lng, radius, userName)
    }

    fun deleteStore(store: Store) {
        val gid = _groupId.value ?: return
        repo.deleteStore(gid, store.id)
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

    private fun friendlyError(e: Exception): String = when {
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
