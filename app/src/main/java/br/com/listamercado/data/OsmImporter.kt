package br.com.listamercado.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Um mercado encontrado no OpenStreetMap. */
data class OsmStore(
    /** ex.: "node/123456" — usado para não importar o mesmo mercado duas vezes */
    val osmId: String,
    val name: String,
    val kind: String,
    val lat: Double,
    val lng: Double,
)

/**
 * Busca mercados de uma cidade no OpenStreetMap (API Overpass, gratuita e sem chave).
 * Roda no celular, na hora em que a pessoa toca em "Buscar".
 */
object OsmImporter {

    private val ENDPOINTS = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
    )

    /** Tipos de loja do OSM considerados "mercado" */
    private val KINDS = mapOf(
        "supermarket" to "Supermercado",
        "convenience" to "Mercearia",
        "wholesale" to "Atacado",
        "greengrocer" to "Hortifrúti",
    )

    suspend fun searchCity(city: String, uf: String): List<OsmStore> = withContext(Dispatchers.IO) {
        val query = buildQuery(city.trim(), uf.trim().uppercase())
        var lastError: Exception? = null
        for (endpoint in ENDPOINTS) {
            try {
                return@withContext parse(post(endpoint, query))
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw IllegalStateException(
            "Não consegui consultar o OpenStreetMap agora. Confira a internet e tente de novo.",
            lastError,
        )
    }

    /** Para "Sao Carlos" achar "São Carlos": cada vogal/c vira uma classe com os acentos */
    private val ACCENTS = listOf("aáàâãä", "eéèêë", "iíìîï", "oóòôõö", "uúùûü", "cç")

    private fun cityPattern(city: String): String = buildString {
        for (ch in city.replace("\"", "").replace("\\", "")) {
            val group = ACCENTS.firstOrNull { ch.lowercaseChar() in it }
            when {
                group != null -> append("[").append(group).append(group.uppercase()).append("]")
                // Caractere especial de regex: escapado (\\ no texto da consulta = \ na regex)
                ch in ".^$|?*+()[]{}" -> append("\\\\").append(ch)
                else -> append(ch)
            }
        }
    }

    private fun buildQuery(city: String, uf: String): String {
        val safeCity = cityPattern(city)
        val kinds = KINDS.keys.joinToString("|")
        return """
            [out:json][timeout:60];
            area["ISO3166-2"="BR-$uf"]->.uf;
            rel["boundary"="administrative"]["admin_level"="8"]["name"~"^$safeCity$",i](area.uf);
            map_to_area->.cidade;
            nwr["shop"~"^($kinds)$"](area.cidade);
            out center tags;
        """.trimIndent()
    }

    private fun post(endpoint: String, query: String): String {
        val conn = URL(endpoint).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 70_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            // O Overpass pede para os apps se identificarem
            conn.setRequestProperty("User-Agent", "ListaMercado-Android/1.0")
            conn.outputStream.use { it.write(("data=" + URLEncoder.encode(query, "UTF-8")).toByteArray()) }
            if (conn.responseCode != 200) error("Overpass respondeu ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(json: String): List<OsmStore> {
        val elements = JSONObject(json).optJSONArray("elements") ?: return emptyList()
        return (0 until elements.length()).mapNotNull { i ->
            val el = elements.getJSONObject(i)
            val tags = el.optJSONObject("tags") ?: return@mapNotNull null
            // "node" traz lat/lon direto; "way"/"relation" (prédios) trazem o centro
            val point = if (el.has("lat")) el else el.optJSONObject("center") ?: return@mapNotNull null
            val shop = tags.optString("shop")
            OsmStore(
                osmId = "${el.getString("type")}/${el.getLong("id")}",
                name = tags.optString("name").ifBlank { tags.optString("brand") }.trim(),
                kind = KINDS[shop] ?: shop,
                lat = point.getDouble("lat"),
                lng = point.getDouble("lon"),
            )
        }.sortedWith(compareBy({ it.name.isBlank() }, { it.name.lowercase() }))
    }
}
