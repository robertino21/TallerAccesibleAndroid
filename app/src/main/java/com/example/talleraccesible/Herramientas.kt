package com.example.talleraccesible

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PantallaHerramientas(vm: AppViewModel) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Herramientas", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        SeccionCortador(vm)
        SeccionNormalizar(vm)
        SeccionCollage(vm)
    }
}

// Botones que aparecen bajo el resultado de una herramienta.
@Composable
private fun AccionesResultado(r: Resultado, vm: AppViewModel, carpeta: String) {
    val ctx = LocalContext.current
    val env = vm.entorno
    Spacer(Modifier.height(8.dp))
    VistaPrevia(r)
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { if (r.uri != null) Almacen.compartir(ctx, r.uri) },
            modifier = Modifier.weight(1f).height(48.dp)
        ) { Text("Compartir", fontSize = 14.sp) }
        Button(
            onClick = {
                if (r.uri != null && Almacen.copiar(ctx, r.uri)) env.decir("Imagen copiada")
                else env.decir("No se pudo copiar la imagen")
            },
            modifier = Modifier.weight(1f).height(48.dp)
        ) { Text("Copiar", fontSize = 14.sp) }
        Button(
            onClick = { vm.pedirGuardar(r, carpeta) },
            modifier = Modifier.weight(1f).height(48.dp)
        ) { Text("Guardar", fontSize = 14.sp) }
    }
}

@Composable
private fun SeccionCortador(vm: AppViewModel) {
    val c = vm.herramientas.cortador
    val env = vm.entorno
    val ctx = LocalContext.current
    val selector = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            c.imagenUri = uri
            c.imagenNombre = Imagenes.nombre(ctx, uri)
            c.resultado = null
        }
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Seccion("Cortador")
            Text("Recorta los bordes de una foto por porcentaje.")
            OutlinedButton(onClick = { selector.launch("image/*") }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text(if (c.imagenUri == null) "Elegir imagen" else "Cambiar imagen")
            }
            if (c.imagenNombre.isNotEmpty()) Text("Imagen: ${c.imagenNombre}")
            if (c.imagenUri != null) {
                // La foto en chiquito para recortar con el dedo.
                val bitmap by produceState<Bitmap?>(initialValue = null, c.imagenUri) {
                    value = withContext(Dispatchers.IO) {
                        runCatching { Imagenes.cargar(ctx, c.imagenUri!!, 1024).bitmap }.getOrNull()
                    }
                }
                bitmap?.let { RecorteTactil(it, c) }
                Text("Arrastrá las esquinas del marco para elegir el recorte. Los deslizadores de abajo hacen lo mismo.")
            }
            CampoSlider("Recorte izquierda (%)", c.recIzq, 0..80, { c.recIzq = it })
            CampoSlider("Recorte derecha (%)", c.recDer, 0..80, { c.recDer = it })
            CampoSlider("Recorte arriba (%)", c.recArr, 0..80, { c.recArr = it })
            CampoSlider("Recorte abajo (%)", c.recAba, 0..80, { c.recAba = it })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { c.generar(env) },
                    enabled = !c.ocupado,
                    modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(12.dp)
                ) { Text(if (c.ocupado) "Recortando..." else "Recortar", fontSize = 14.sp) }
                OutlinedButton(
                    onClick = { c.limpiar(); env.decir("Cortador limpio") },
                    modifier = Modifier.height(56.dp), shape = RoundedCornerShape(12.dp)
                ) { Text("Limpiar") }
            }
            c.resultado?.let { AccionesResultado(it, vm, "Recortadas") }
        }
    }
}

// Recorte táctil: el marco se arrastra con el dedo sobre la foto y mueve
// los mismos porcentajes que los deslizadores de abajo, que quedan como
// la forma de recortar con lector de pantalla.
private enum class ManijaRecorte { SUP_IZQ, SUP_DER, INF_IZQ, INF_DER, IZQ, DER, SUP, INF, MOVER, NADA }

private class InicioRecorte(val izq: Int, val der: Int, val arr: Int, val aba: Int)

@Composable
private fun RecorteTactil(bitmap: Bitmap, c: CortadorState) {
    val densidad = LocalDensity.current
    var manija by remember { mutableStateOf(ManijaRecorte.NADA) }
    var inicio by remember { mutableStateOf<InicioRecorte?>(null) }
    var acumulado by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(
        Modifier.fillMaxWidth().height(320.dp)
            .semantics { contentDescription = "Recorte táctil. Arrastrá las esquinas del marco para elegir qué parte de la foto queda. También podés ajustar el recorte con los deslizadores de abajo." }
    ) {
        val anchoCaja = with(densidad) { maxWidth.toPx() }
        val altoCaja = with(densidad) { maxHeight.toPx() }
        val escala = minOf(anchoCaja / bitmap.width, altoCaja / bitmap.height)
        val anchoImg = bitmap.width * escala
        val altoImg = bitmap.height * escala
        val offX = (anchoCaja - anchoImg) / 2f
        val offY = (altoCaja - altoImg) / 2f

        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(bitmap, anchoCaja, altoCaja, offX, offY, anchoImg, altoImg) {
                    detectDragGestures(
                        onDragStart = { pos ->
                            val izqPx = offX + anchoImg * c.recIzq / 100f
                            val derPx = offX + anchoImg * (100 - c.recDer) / 100f
                            val arrPx = offY + altoImg * c.recArr / 100f
                            val abaPx = offY + altoImg * (100 - c.recAba) / 100f
                            val tolEsq = with(densidad) { 28.dp.toPx() }
                            val tolBorde = with(densidad) { 20.dp.toPx() }
                            inicio = InicioRecorte(c.recIzq, c.recDer, c.recArr, c.recAba)
                            acumulado = Offset.Zero
                            manija = when {
                                pos.x < izqPx + tolEsq && pos.y < arrPx + tolEsq -> ManijaRecorte.SUP_IZQ
                                pos.x > derPx - tolEsq && pos.y < arrPx + tolEsq -> ManijaRecorte.SUP_DER
                                pos.x < izqPx + tolEsq && pos.y > abaPx - tolEsq -> ManijaRecorte.INF_IZQ
                                pos.x > derPx - tolEsq && pos.y > abaPx - tolEsq -> ManijaRecorte.INF_DER
                                pos.x < izqPx + tolBorde -> ManijaRecorte.IZQ
                                pos.x > derPx - tolBorde -> ManijaRecorte.DER
                                pos.y < arrPx + tolBorde -> ManijaRecorte.SUP
                                pos.y > abaPx - tolBorde -> ManijaRecorte.INF
                                pos.x in izqPx..derPx && pos.y in arrPx..abaPx -> ManijaRecorte.MOVER
                                else -> ManijaRecorte.NADA
                            }
                        },
                        onDragEnd = { manija = ManijaRecorte.NADA },
                        onDragCancel = { manija = ManijaRecorte.NADA },
                        onDrag = { _, cantidad ->
                            val ini = inicio ?: return@detectDragGestures
                            acumulado += cantidad
                            val dxPct = acumulado.x / anchoImg * 100f
                            val dyPct = acumulado.y / altoImg * 100f
                            fun lim(v: Float, max: Int) = v.toInt().coerceIn(0, max)
                            when (manija) {
                                ManijaRecorte.SUP_IZQ -> {
                                    c.recIzq = lim(ini.izq + dxPct, 90 - ini.der)
                                    c.recArr = lim(ini.arr + dyPct, 90 - ini.aba)
                                }
                                ManijaRecorte.SUP_DER -> {
                                    c.recDer = lim(ini.der - dxPct, 90 - ini.izq)
                                    c.recArr = lim(ini.arr + dyPct, 90 - ini.aba)
                                }
                                ManijaRecorte.INF_IZQ -> {
                                    c.recIzq = lim(ini.izq + dxPct, 90 - ini.der)
                                    c.recAba = lim(ini.aba - dyPct, 90 - ini.arr)
                                }
                                ManijaRecorte.INF_DER -> {
                                    c.recDer = lim(ini.der - dxPct, 90 - ini.izq)
                                    c.recAba = lim(ini.aba - dyPct, 90 - ini.arr)
                                }
                                ManijaRecorte.IZQ -> c.recIzq = lim(ini.izq + dxPct, 90 - ini.der)
                                ManijaRecorte.DER -> c.recDer = lim(ini.der - dxPct, 90 - ini.izq)
                                ManijaRecorte.SUP -> c.recArr = lim(ini.arr + dyPct, 90 - ini.aba)
                                ManijaRecorte.INF -> c.recAba = lim(ini.aba - dyPct, 90 - ini.arr)
                                ManijaRecorte.MOVER -> {
                                    val dx = dxPct.coerceIn(-ini.izq.toFloat(), ini.der.toFloat()).toInt()
                                    val dy = dyPct.coerceIn(-ini.arr.toFloat(), ini.aba.toFloat()).toInt()
                                    c.recIzq = ini.izq + dx
                                    c.recDer = ini.der - dx
                                    c.recArr = ini.arr + dy
                                    c.recAba = ini.aba - dy
                                }
                                ManijaRecorte.NADA -> {}
                            }
                        },
                    )
                }
        ) {
            val izqPx = offX + anchoImg * c.recIzq / 100f
            val derPx = offX + anchoImg * (100 - c.recDer) / 100f
            val arrPx = offY + altoImg * c.recArr / 100f
            val abaPx = offY + altoImg * (100 - c.recAba) / 100f
            val marco = Rect(izqPx, arrPx, derPx, abaPx)
            val sombra = Color.Black.copy(alpha = 0.5f)
            drawRect(sombra, topLeft = Offset.Zero, size = Size(size.width, marco.top))
            drawRect(sombra, topLeft = Offset(0f, marco.bottom), size = Size(size.width, size.height - marco.bottom))
            drawRect(sombra, topLeft = Offset(0f, marco.top), size = Size(marco.left, marco.height))
            drawRect(sombra, topLeft = Offset(marco.right, marco.top), size = Size(size.width - marco.right, marco.height))
            drawRect(Color.White, topLeft = Offset(marco.left, marco.top), size = Size(marco.width, marco.height), style = Stroke(width = 3f))
            val m = with(densidad) { 14.dp.toPx() }
            listOf(
                Offset(marco.left, marco.top),
                Offset(marco.right, marco.top),
                Offset(marco.left, marco.bottom),
                Offset(marco.right, marco.bottom),
            ).forEach { p ->
                drawRect(Color.White, topLeft = Offset(p.x - m / 2, p.y - m / 2), size = Size(m, m))
            }
        }
    }
}

@Composable
private fun SeccionNormalizar(vm: AppViewModel) {
    val n = vm.herramientas.normalizar
    val env = vm.entorno
    val ctx = LocalContext.current
    val selector = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            n.imagenUri = uri
            n.imagenNombre = Imagenes.nombre(ctx, uri)
            n.resultado = null
        }
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Seccion("Normalizar")
            Text("Deja una foto en tamaño cuadrado o en proporción, sin deformarla.")
            OutlinedButton(onClick = { selector.launch("image/*") }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text(if (n.imagenUri == null) "Elegir imagen" else "Cambiar imagen")
            }
            if (n.imagenNombre.isNotEmpty()) Text("Imagen: ${n.imagenNombre}")
            CampoLista("Modo", MODOS_NORMALIZAR, n.modo, { n.modo = it })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { n.generar(env) },
                    enabled = !n.ocupado,
                    modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(12.dp)
                ) { Text(if (n.ocupado) "Normalizando..." else "Normalizar", fontSize = 14.sp) }
                OutlinedButton(
                    onClick = { n.limpiar(); env.decir("Normalizar limpio") },
                    modifier = Modifier.height(56.dp), shape = RoundedCornerShape(12.dp)
                ) { Text("Limpiar") }
            }
            n.resultado?.let { AccionesResultado(it, vm, "Normalizadas") }
        }
    }
}

@Composable
private fun SeccionCollage(vm: AppViewModel) {
    val c = vm.herramientas.collage
    val env = vm.entorno
    val ctx = LocalContext.current
    val selector = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        for (uri in uris) {
            if (c.imagenes.size >= 4) {
                env.decir("El collage lleva 4 imágenes como máximo")
                break
            }
            c.agregar(uri, Imagenes.nombre(ctx, uri))
        }
        c.resultado = null
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Seccion("Collage")
            Text("Une hasta 4 fotos en una sola imagen.")
            OutlinedButton(onClick = { selector.launch("image/*") }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text("Elegir imágenes (máx. 4)")
            }
            if (c.nombres.isEmpty()) {
                Text("Sin imágenes")
            } else {
                c.nombres.forEachIndexed { i, nombre -> Text("${i + 1} de ${c.nombres.size}: $nombre") }
                OutlinedButton(onClick = { c.quitarUltima(); env.decir("Última imagen quitada") }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text("Quitar última")
                }
            }
            CampoLista("Tipo", TIPOS_COLLAGE, c.tipo, { c.tipo = it })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { c.generar(env) },
                    enabled = !c.ocupado,
                    modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(12.dp)
                ) { Text(if (c.ocupado) "Creando..." else "Crear collage", fontSize = 14.sp) }
                OutlinedButton(
                    onClick = { c.limpiar(); env.decir("Collage limpio") },
                    modifier = Modifier.height(56.dp), shape = RoundedCornerShape(12.dp)
                ) { Text("Limpiar") }
            }
            c.resultado?.let { AccionesResultado(it, vm, "Collages") }
        }
    }
}
