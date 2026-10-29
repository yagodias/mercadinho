package br.com.listamercado.ui

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val MAX_ZOOM = 5f

/**
 * Tela para ajustar a foto de perfil: arrastar posiciona, pinça (ou a barra) dá zoom.
 * O que fica dentro do círculo é o que vira a foto.
 */
@Composable
fun PhotoCropDialog(source: Bitmap, onCancel: () -> Unit, onConfirm: (Rect) -> Unit) {
    val image = remember(source) { source.asImageBitmap() }

    // Lado do quadrado de recorte na tela, em px
    var viewport by remember { mutableIntStateOf(0) }
    // 1 = foto inteira cobrindo o quadrado; até MAX_ZOOM vezes mais perto
    var zoom by remember(source) { mutableFloatStateOf(1f) }
    // Deslocamento do centro da foto em relação ao centro do quadrado, em px
    var offset by remember(source) { mutableStateOf(Offset.Zero) }

    val baseScale = if (viewport > 0) viewport.toFloat() / min(source.width, source.height) else 1f
    val scale = baseScale * zoom

    // Não deixa a foto sair de dentro do quadrado (sem bordas vazias)
    fun clamp(o: Offset, s: Float): Offset {
        val maxX = max(0f, (source.width * s - viewport) / 2f)
        val maxY = max(0f, (source.height * s - viewport) / 2f)
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    fun cropArea(): Rect {
        val imgLeft = viewport / 2f + offset.x - source.width * scale / 2f
        val imgTop = viewport / 2f + offset.y - source.height * scale / 2f
        val left = -imgLeft / scale
        val top = -imgTop / scale
        val side = viewport / scale
        return Rect(
            left.roundToInt(),
            top.roundToInt(),
            (left + side).roundToInt(),
            (top + side).roundToInt(),
        )
    }

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                Text("Ajustar foto", color = Color.White, style = MaterialTheme.typography.titleLarge)
                Text(
                    "Arraste para posicionar. Use dois dedos ou a barra para aproximar.",
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodyMedium,
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .onSizeChanged { viewport = it.width }
                        .clipToBounds()
                        .pointerInput(source, viewport) {
                            detectTransformGestures { _, pan, gestureZoom, _ ->
                                zoom = (zoom * gestureZoom).coerceIn(1f, MAX_ZOOM)
                                offset = clamp(offset + pan, baseScale * zoom)
                            }
                        },
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val w = source.width * scale
                        val h = source.height * scale
                        drawImage(
                            image = image,
                            dstOffset = IntOffset(
                                (size.width / 2f + offset.x - w / 2f).roundToInt(),
                                (size.height / 2f + offset.y - h / 2f).roundToInt(),
                            ),
                            dstSize = IntSize(w.roundToInt(), h.roundToInt()),
                            filterQuality = FilterQuality.Medium,
                        )
                        // Escurece o que fica fora do círculo
                        val circle = Path().apply {
                            addOval(androidx.compose.ui.geometry.Rect(Offset.Zero, size))
                        }
                        clipPath(circle, clipOp = ClipOp.Difference) {
                            drawRect(Color.Black.copy(alpha = 0.6f))
                        }
                        drawCircle(
                            color = Color.White,
                            radius = size.minDimension / 2f - 1.dp.toPx(),
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    }
                }

                Slider(
                    value = zoom,
                    onValueChange = {
                        zoom = it
                        offset = clamp(offset, baseScale * it)
                    },
                    valueRange = 1f..MAX_ZOOM,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onCancel) { Text("Cancelar", color = Color.White) }
                    Button(onClick = { onConfirm(cropArea()) }, enabled = viewport > 0) { Text("Salvar foto") }
                }
            }
        }
    }
}
