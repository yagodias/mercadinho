package br.com.listamercado.data

data class Group(
    val id: String,
    val name: String,
    val inviteCode: String,
    /** uid -> nome de quem está no grupo */
    val memberNames: Map<String, String>,
)

data class Item(
    val id: String,
    val name: String,
    val checked: Boolean,
    /** Nome gravado na hora (usado em itens antigos, de antes dos perfis) */
    val addedBy: String,
    /** Quem adicionou; o nome e a foto exibidos vêm do perfil dessa pessoa */
    val addedByUid: String?,
    val createdAtMillis: Long,
)

data class Profile(
    val uid: String,
    val name: String,
    /** Foto JPEG pequena (256 px) em Base64, ou null */
    val photo: String?,
)

data class Store(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    /** raio do aviso, em metros */
    val radius: Int,
    /** preenchido quando o mercado veio do OpenStreetMap */
    val osmId: String? = null,
)
