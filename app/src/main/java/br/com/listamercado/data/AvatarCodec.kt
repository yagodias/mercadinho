package br.com.listamercado.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Fotos de perfil ficam no próprio Firestore (o Cloud Storage do Firebase exige plano pago).
 * Fluxo: [loadForEditing] abre a foto num tamanho bom para a tela de ajuste e
 * [encodeCrop] recorta a área escolhida num JPEG quadrado de 256 px (~15-30 KB) em Base64.
 */
object AvatarCodec {

    private const val SIZE = 256
    private const val QUALITY = 80

    /** Lado menor da foto usada na tela de ajuste: nítida para dar zoom, leve para a memória */
    private const val EDIT_SIDE = 1080

    fun loadForEditing(context: Context, uri: Uri): Bitmap = decode(context, uri, EDIT_SIDE)

    /** Recorta [area] (em pixels de [source]) e gera o JPEG quadrado da foto de perfil. */
    fun encodeCrop(source: Bitmap, area: Rect): String {
        val left = area.left.coerceIn(0, source.width - 1)
        val top = area.top.coerceIn(0, source.height - 1)
        val side = min(area.width(), area.height())
            .coerceAtMost(min(source.width - left, source.height - top))
            .coerceAtLeast(1)
        val cropped = Bitmap.createBitmap(source, left, top, side, side)
        val scaled = Bitmap.createScaledBitmap(cropped, SIZE, SIZE, true)
        val bytes = ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            out.toByteArray()
        }
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun decodeToBitmap(base64: String): Bitmap? = runCatching {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()

    private fun decode(context: Context, uri: Uri, targetShortSide: Int): Bitmap {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder já respeita a rotação da foto (EXIF)
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val shortest = min(info.size.width, info.size.height)
                if (shortest > targetShortSide) {
                    val scale = targetShortSide.toFloat() / shortest
                    decoder.setTargetSize(
                        (info.size.width * scale).toInt(),
                        (info.size.height * scale).toInt(),
                    )
                }
            }
        }
        // Android 8/8.1: lê reduzido para não estourar a memória com fotos grandes
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (min(bounds.outWidth, bounds.outHeight) / (sample * 2) >= targetShortSide) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = max(1, sample) }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("Não consegui abrir a imagem")
    }
}
