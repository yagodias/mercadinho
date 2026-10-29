package br.com.listamercado.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.security.SecureRandom

/**
 * Estrutura no Firestore:
 *
 * invites/{codigo}                 -> { groupId }
 * groups/{groupId}                 -> { name, inviteCode, members: [uid], memberNames: {uid: nome} }
 * groups/{groupId}/items/{id}      -> { name, checked, addedBy, addedByUid, createdAt }
 * groups/{groupId}/stores/{id}     -> { name, lat, lng, radius, osmId?, addedBy, createdAt }
 * groups/{groupId}/members/{uid}   -> { name, photo (JPEG Base64)?, updatedAt }
 *
 * Escritas de itens e mercados não esperam o servidor: o Firestore grava no
 * cache local na hora e sincroniza quando houver internet (útil dentro do mercado).
 */
class Repository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) {

    suspend fun ensureSignedIn(): String {
        auth.currentUser?.let { return it.uid }
        val user = auth.signInAnonymously().await().user
            ?: error("Não foi possível entrar")
        return user.uid
    }

    private fun groupRef(gid: String): DocumentReference = db.collection("groups").document(gid)
    private fun itemsRef(gid: String) = groupRef(gid).collection("items")
    private fun storesRef(gid: String) = groupRef(gid).collection("stores")
    private fun membersRef(gid: String) = groupRef(gid).collection("members")

    val currentUid: String? get() = auth.currentUser?.uid

    // ---------- Grupo ----------

    suspend fun createGroup(groupName: String, userName: String): String {
        val uid = ensureSignedIn()
        var lastError: Exception? = null
        // Se o código sorteado já existir, as regras recusam e tentamos outro.
        repeat(5) {
            val code = newInviteCode()
            val group = db.collection("groups").document()
            val batch = db.batch()
            batch.set(
                group,
                mapOf(
                    "name" to groupName,
                    "inviteCode" to code,
                    "members" to listOf(uid),
                    "memberNames" to mapOf(uid to userName),
                    "createdAt" to System.currentTimeMillis(),
                ),
            )
            batch.set(db.collection("invites").document(code), mapOf("groupId" to group.id))
            try {
                batch.commit().await()
                return group.id
            } catch (e: FirebaseFirestoreException) {
                if (e.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) throw e
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("Não foi possível criar o grupo")
    }

    suspend fun joinGroup(rawCode: String, userName: String): String {
        val uid = ensureSignedIn()
        val code = rawCode.trim().uppercase()
        val invite = db.collection("invites").document(code).get(Source.SERVER).await()
        val gid = invite.getString("groupId")
            ?: throw IllegalArgumentException("Código não encontrado. Confira e tente de novo.")
        groupRef(gid).update(
            mapOf<String, Any>(
                "members" to FieldValue.arrayUnion(uid),
                "memberNames.$uid" to userName,
            ),
        ).await()
        return gid
    }

    fun leaveGroup(gid: String) {
        val uid = auth.currentUser?.uid ?: return
        membersRef(gid).document(uid).delete()
        groupRef(gid).update(
            mapOf<String, Any>(
                "members" to FieldValue.arrayRemove(uid),
                "memberNames.$uid" to FieldValue.delete(),
            ),
        )
    }

    /**
     * Emite o grupo sempre que ele muda. Emite `null` quando fica claro que o
     * usuário não tem mais acesso (foi removido ou o grupo não existe).
     */
    fun observeGroup(gid: String): Flow<Group?> = callbackFlow {
        val reg = groupRef(gid).addSnapshotListener { snap, err ->
            if (err != null) {
                if (err.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) trySend(null)
                return@addSnapshotListener
            }
            if (snap == null) return@addSnapshotListener
            if (!snap.exists()) {
                // Cache vazio não quer dizer que o grupo sumiu; só confia na resposta do servidor
                if (!snap.metadata.isFromCache) trySend(null)
                return@addSnapshotListener
            }
            @Suppress("UNCHECKED_CAST")
            val names = (snap.get("memberNames") as? Map<String, String>).orEmpty()
            trySend(
                Group(
                    id = snap.id,
                    name = snap.getString("name").orEmpty(),
                    inviteCode = snap.getString("inviteCode").orEmpty(),
                    memberNames = names,
                ),
            )
        }
        awaitClose { reg.remove() }
    }

    // ---------- Perfis ----------

    /** Perfis de todos do grupo: uid -> perfil */
    fun observeProfiles(gid: String): Flow<Map<String, Profile>> = callbackFlow {
        val reg = membersRef(gid).addSnapshotListener { snap, err ->
            if (err != null || snap == null) return@addSnapshotListener
            trySend(
                snap.documents.associate { d ->
                    d.id to Profile(
                        uid = d.id,
                        name = d.getString("name").orEmpty(),
                        photo = d.getString("photo")?.takeIf { it.isNotBlank() },
                    )
                },
            )
        }
        awaitClose { reg.remove() }
    }

    /** Cria o perfil de quem já estava no grupo antes desta versão. */
    suspend fun ensureProfile(gid: String, name: String) {
        val uid = ensureSignedIn()
        val doc = membersRef(gid).document(uid)
        val exists = runCatching { doc.get(Source.SERVER).await().exists() }.getOrDefault(true)
        if (!exists && name.isNotBlank()) {
            doc.set(mapOf("name" to name, "updatedAt" to System.currentTimeMillis()), SetOptions.merge())
        }
    }

    fun saveProfileName(gid: String, name: String) {
        val uid = auth.currentUser?.uid ?: return
        membersRef(gid).document(uid).set(
            mapOf("name" to name, "updatedAt" to System.currentTimeMillis()),
            SetOptions.merge(),
        )
        // Mantém o nome atualizado também na lista de membros do grupo (tela de convite)
        groupRef(gid).update("memberNames.$uid", name)
    }

    /** [photo] em Base64, ou null para remover a foto. */
    fun saveProfilePhoto(gid: String, name: String, photo: String?) {
        val uid = auth.currentUser?.uid ?: return
        membersRef(gid).document(uid).set(
            mapOf("name" to name, "photo" to photo, "updatedAt" to System.currentTimeMillis()),
            SetOptions.merge(),
        )
    }

    // ---------- Itens ----------

    fun observeItems(gid: String): Flow<List<Item>> = callbackFlow {
        val reg = itemsRef(gid).addSnapshotListener { snap, err ->
            if (err != null || snap == null) return@addSnapshotListener
            trySend(snap.documents.map { it.toItem() }.sortedBy { it.createdAtMillis })
        }
        awaitClose { reg.remove() }
    }

    fun addItem(gid: String, name: String, addedBy: String) {
        itemsRef(gid).add(
            mapOf(
                "name" to name,
                "checked" to false,
                "addedBy" to addedBy,
                "addedByUid" to auth.currentUser?.uid,
                "createdAt" to System.currentTimeMillis(),
            ),
        )
    }

    fun setChecked(gid: String, itemId: String, checked: Boolean) {
        itemsRef(gid).document(itemId).update("checked", checked)
    }

    fun deleteItems(gid: String, itemIds: List<String>) {
        if (itemIds.isEmpty()) return
        // Lotes de até 500 operações (limite do Firestore)
        itemIds.chunked(450).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(itemsRef(gid).document(it)) }
            batch.commit()
        }
    }

    /** Usado pelo aviso de mercado: tenta o servidor e cai para o cache se estiver sem sinal. */
    suspend fun fetchPendingItemNames(gid: String): List<String> {
        val query = itemsRef(gid).whereEqualTo("checked", false)
        val snap = try {
            withTimeout(6_000) { query.get(Source.DEFAULT).await() }
        } catch (e: Exception) {
            query.get(Source.CACHE).await()
        }
        return snap.documents
            .map { it.toItem() }
            .sortedBy { it.createdAtMillis }
            .map { it.name }
    }

    // ---------- Mercados ----------

    fun observeStores(gid: String): Flow<List<Store>> = callbackFlow {
        val reg = storesRef(gid).addSnapshotListener { snap, err ->
            if (err != null || snap == null) return@addSnapshotListener
            trySend(snap.documents.mapNotNull { it.toStore() }.sortedBy { it.name.lowercase() })
        }
        awaitClose { reg.remove() }
    }

    fun addStore(gid: String, name: String, lat: Double, lng: Double, radius: Int, addedBy: String) {
        storesRef(gid).add(
            mapOf(
                "name" to name,
                "lat" to lat,
                "lng" to lng,
                "radius" to radius,
                "addedBy" to addedBy,
                "createdAt" to System.currentTimeMillis(),
            ),
        )
    }

    /** Cadastra vários mercados de uma vez (importação do OpenStreetMap). */
    fun addStores(gid: String, stores: List<OsmStore>, radius: Int, addedBy: String) {
        val now = System.currentTimeMillis()
        stores.chunked(450).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { s ->
                batch.set(
                    storesRef(gid).document(),
                    mapOf(
                        "name" to s.name,
                        "lat" to s.lat,
                        "lng" to s.lng,
                        "radius" to radius,
                        "osmId" to s.osmId,
                        "addedBy" to addedBy,
                        "createdAt" to now,
                    ),
                )
            }
            batch.commit()
        }
    }

    fun deleteStores(gid: String, storeIds: Collection<String>) {
        storeIds.chunked(450).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(storesRef(gid).document(it)) }
            batch.commit()
        }
    }

    fun updateStore(gid: String, storeId: String, name: String, radius: Int) {
        storesRef(gid).document(storeId).update(mapOf<String, Any>("name" to name, "radius" to radius))
    }

    fun updateStoresRadius(gid: String, storeIds: Collection<String>, radius: Int) {
        storeIds.chunked(450).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.update(storesRef(gid).document(it), "radius", radius) }
            batch.commit()
        }
    }

    // ---------- Conversões ----------

    private fun DocumentSnapshot.toItem() = Item(
        id = id,
        name = getString("name").orEmpty(),
        checked = getBoolean("checked") ?: false,
        addedBy = getString("addedBy").orEmpty(),
        addedByUid = getString("addedByUid"),
        createdAtMillis = getLong("createdAt") ?: 0L,
    )

    private fun DocumentSnapshot.toStore(): Store? {
        val lat = getDouble("lat") ?: return null
        val lng = getDouble("lng") ?: return null
        return Store(
            id = id,
            name = getString("name").orEmpty(),
            lat = lat,
            lng = lng,
            radius = getLong("radius")?.toInt() ?: 150,
            osmId = getString("osmId"),
        )
    }

    private companion object {
        // Sem 0/O e 1/I para não confundir na hora de digitar
        const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = SecureRandom()

        fun newInviteCode(): String =
            (1..6).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")
    }
}
