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
    val addedBy: String,
    val createdAtMillis: Long,
)

data class Store(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    /** raio do aviso, em metros */
    val radius: Int,
)
