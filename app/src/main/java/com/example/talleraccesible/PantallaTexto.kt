package com.example.talleraccesible

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalClipboardManager
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun PantallaTexto(vm: AppViewModel) {
    val t = vm.texto
    val s = t.simple
    val c = t.capas
    // Vista previa en vivo: se regenera sola, en silencio, un rato después
    // de cada cambio, para que el que ve no tenga que tocar Generar ni
    // cambiar de pestaña. Sin botón para prenderla o apagarla: simplemente
    // funciona.
    LaunchedEffect(t.modo, s.texto, s.estilo, s.pos, s.yPct, s.auto, s.lienzo.snap(), c.capas.toList(), c.lienzo.snap()) {
        delay(400)
        vm.texto.generar(vm.entorno, silencioso = true)
    }
    Column(
        Modifier.fillMaxSize().imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Taller Accesible", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        VistaPreviaEnVivo(vm)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    CampoLista("Modo", MODOS_TEXTO, t.modo, { t.modo = it })
                }
            }
            if (t.modo == "Simple") PanelSimple(vm) else PanelCapas(vm)
        }
    }
}

// Vista previa en vivo del Editor: la imagen que se está armando, siempre
// visible arriba de los controles, con zoom de pellizco.
@Composable
private fun VistaPreviaEnVivo(vm: AppViewModel) {
    val resultado = vm.texto.resultadoActual()
    Card(shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(4.dp)) {
        if (resultado != null) {
            ImagenConZoom(
                bitmap = resultado.vista,
                descripcion = resultado.descripcion,
                modifier = Modifier.fillMaxWidth().height(300.dp),
            )
        } else {
            Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                Text("La vista previa aparece acá sola mientras trabajás.")
            }
        }
    }
}

// Imagen con zoom de pellizco, compartida por la vista previa en vivo del
// Editor y por la pestaña Vista previa. El zoom vuelve al tamaño normal
// cuando llega una imagen nueva.
@Composable
private fun ImagenConZoom(
    bitmap: Bitmap,
    descripcion: String,
    modifier: Modifier = Modifier,
) {
    var escala by remember(bitmap) { mutableStateOf(1f) }
    var desplazamiento by remember(bitmap) { mutableStateOf(Offset.Zero) }
    Box(
        modifier.background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val nuevaEscala = (escala * zoom).coerceIn(1f, 5f)
                    escala = nuevaEscala
                    desplazamiento = if (nuevaEscala <= 1f) Offset.Zero else desplazamiento + pan
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    escala = if (escala > 1f) 1f else 2f
                    desplazamiento = Offset.Zero
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = descripcion,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
                .graphicsLayer(
                    scaleX = escala,
                    scaleY = escala,
                    translationX = desplazamiento.x,
                    translationY = desplazamiento.y,
                ),
        )
    }
}

@Composable
private fun PanelSimple(vm: AppViewModel) {
    val env = vm.entorno
    val s = vm.texto.simple
    val importar = rememberImportadorFuentes(vm)
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current 
    
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp), shape = RoundedCornerShape(16.dp)) {
         Column(Modifier.padding(16.dp)) {
             Row(verticalAlignment = Alignment.Top) {
                 Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                     Text("Texto", fontWeight = FontWeight.Medium, fontSize = 16.sp)
                     OutlinedTextField(
                         value = s.texto, onValueChange = { s.texto = it },
                         modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp)
                             .onPreviewKeyEvent { ev ->
                                 if (ev.type == KeyEventType.KeyDown && ev.isCtrlPressed &&
                                     (ev.key == Key.Enter || ev.key == Key.NumPadEnter)
                                 ) {
                                     s.generar(env); true
                                 } else false
                             },
                         placeholder = { Text("Escribe tu texto aquí...") }, shape = RoundedCornerShape(12.dp)
                     )
                 }
                 Spacer(Modifier.width(8.dp))
                 // Los botones quedan a la altura del cuadro, no de la etiqueta fija de arriba (etiqueta ~20 dp + 4 dp de separación).
                 Column(Modifier.padding(top = 24.dp)) {
                     Button(
                         onClick = { s.texto = clipboard.getText()?.text ?: ""; env.decir("Texto pegado") },
                         modifier = Modifier.padding(top = 8.dp), shape = RoundedCornerShape(8.dp)
                     ) { Text("Pegar") }
                     
                     Spacer(Modifier.height(8.dp))
                     
                     OutlinedButton(
                         onClick = { s.texto = ""; env.decir("Texto borrado") },
                         modifier = Modifier.height(40.dp),
                         shape = RoundedCornerShape(8.dp),
                         contentPadding = PaddingValues(horizontal = 8.dp)
                     ) { 
                         Icon(imageVector = Icons.Default.Delete, contentDescription = "Borrar texto")
                     }
                 }
             }
             Spacer(Modifier.height(20.dp))
             CampoCheck(
                 label = "Ajustar tamaño automáticamente",
                 marcado = s.auto,
                 onChange = { s.auto = it },
             )
         }
     }
     
     LienzoEditorModerno(s.lienzo) { env.decir(it) }
     
     Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp), shape = RoundedCornerShape(16.dp)) {
         Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
             EstiloEditor(
                 e = s.estilo, 
                 onChange = { s.estilo = it }, 
                 fuentes = vm.fuentes, 
                 permitirSin = false,
                 onRecargar = { vm.recargarFuentes() }, 
                 onImportar = importar,
                 autoActivo = s.auto,
                 onBuscarFuente = { vm.buscarFuenteEnGitHub(it) },
                 onDescargarFuente = { url, nombre -> vm.descargarFuenteDeUrl(url, nombre) },
                 resultadoBusquedaEncontrado = vm.resultadoBusqueda,
                 nombreFuenteEncontrada = vm.nombreFuenteEncontrada,
                 buscandoEstado = vm.buscandoFuente
             )
         }
     }
     
     Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp), shape = RoundedCornerShape(16.dp)) {
         Column(Modifier.padding(16.dp)) {
             Seccion("Posición")
             CampoLista("Posición vertical", POSICIONES, s.pos, { s.pos = it })
             if (s.pos.startsWith("Personalizada")) {
                 CampoSlider("Posición vertical (%)", s.yPct, 0..100, { s.yPct = it }, paso = 1)
             }
         }
     }
     
     Row(
         modifier = Modifier.fillMaxWidth(),
         horizontalArrangement = Arrangement.spacedBy(8.dp)
     ) {
         Button(
             onClick = { s.generar(env) },
             modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(12.dp)
         ) { Text("Generar", fontSize = 14.sp) }
         
         Button(
             onClick = { 
                 if (s.resultado?.uri != null) Almacen.compartir(context, s.resultado!!.uri!!)
                 else env.decir("Primero genera una imagen")
             },
             enabled = s.resultado != null,
             modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(12.dp)
         ) { Text("Compartir", fontSize = 14.sp) }

         Button(
             onClick = { vm.pedirGuardar(s.resultado, "Texto") },
             enabled = s.resultado != null,
             modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(12.dp)
         ) { Text("Guardar", fontSize = 14.sp) }
         
         OutlinedButton(
             onClick = { vm.limpiarTodo() },
             modifier = Modifier.height(56.dp), 
             shape = RoundedCornerShape(12.dp)
         ) { Text("Limpiar", fontSize = 14.sp) }
     }
}

@Composable
private fun LienzoEditorModerno(l: LienzoState, decir: (String) -> Unit) {
    val ctx = LocalContext.current
    val selector = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) { l.fondoUri = uri; l.fondoNombre = Imagenes.nombre(ctx, uri); decir("Fondo cargado") }
    }
    val fijo = PRESETS.first { it.nombre == l.preset }.ancho != null
    
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp), shape = RoundedCornerShape(16.dp)) {
         Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { 
             Seccion("Lienzo y fondo")
             CampoLista("Tamaño predefinido", LISTA_PRESETS, l.preset, { l.elegirPreset(it) })
             if (!fijo) {
                 CampoSlider("Ancho (px)", l.ancho, 100..4000, { l.ancho = it }, paso = 10)
                 CampoSlider("Alto (px)", l.alto, 100..4000, { l.alto = it }, paso = 10)
             }
             CampoLista("Tipo de fondo", FONDOS, l.fondoTipo, { l.fondoTipo = it })
             
             CampoSlider("Opacidad del fondo", l.opacidadFondo, 0..100, { l.opacidadFondo = it })

             if (l.fondoTipo == "Imagen") {
                 OutlinedButton(onClick = { selector.launch("image/*") }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Cargar imagen de fondo") }
                 Text(if (l.fondoNombre.isEmpty()) "Sin fondo" else "Fondo: ${l.fondoNombre}")
             } else {
                 CampoLista("Color de fondo", LISTA_COLORES, l.color1, { l.color1 = it })
                 if (l.fondoTipo.contains("Degradado")) {
                     CampoLista("Segundo color del degradado", LISTA_COLORES, l.color2, { l.color2 = it })
                 }
             }
         }
     }
}

@Composable
private fun PanelCapas(vm: AppViewModel) {
    val env = vm.entorno
    val c = vm.texto.capas
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val importar = rememberImportadorFuentes(vm)
    var dlgTexto by remember { mutableStateOf(false) }
    var dlgRenombrar by remember { mutableStateOf(false) }
    var imagenPendiente by remember { mutableStateOf<Uri?>(null) }
    
    val selectorImagen = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            // Permiso persistente: la plantilla guarda la URI y la app la puede
            // volver a leer aunque se cierre y se vuelva a abrir.
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            imagenPendiente = uri
        }
    }
    
    LienzoEditorModerno(c.lienzo) { env.decir(it) }
    
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp), shape = RoundedCornerShape(16.dp)) {
         Column(Modifier.padding(16.dp)) {
             Seccion("Capas")
             if (c.capas.isEmpty()) {
                 Text("Sin capas")
             } else {
                 Text("La primera de la lista queda adelante.")
                 Column(Modifier.selectableGroup()) {
                     c.capas.forEachIndexed { i, capa ->
                         val marcada = i == c.seleccion
                         val tipo = if (capa is CapaTexto) "texto" else "imagen"
                         Row(
                             Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                 .selectable(selected = marcada, role = androidx.compose.ui.semantics.Role.RadioButton, onClick = { c.seleccion = i })
                                 .padding(vertical = 4.dp),
                             verticalAlignment = Alignment.CenterVertically,
                         ) {
                             RadioButton(selected = marcada, onClick = null)
                             Spacer(Modifier.width(12.dp))
                             Text("${i + 1} de ${c.capas.size}: ${capa.nombre}, $tipo")
                         }
                     }
                 }
             }
             val haySeleccion = c.capaActual != null
             BotonAccion("Agregar capa de texto", { dlgTexto = true }, principal = false)
             BotonAccion("Agregar capa de imagen", { selectorImagen.launch(arrayOf("image/*")) }, principal = false)
             BotonAccion("Renombrar capa", { dlgRenombrar = true }, enabled = haySeleccion, principal = false)
             BotonAccion("Subir capa", { c.mover(-1, env) }, enabled = haySeleccion, principal = false)
             BotonAccion("Bajar capa", { c.mover(1, env) }, enabled = haySeleccion, principal = false)
             BotonAccion("Eliminar capa", { c.eliminar(env) }, enabled = haySeleccion, principal = false)
             
             when (val capa = c.capaActual) {
                 null -> Text("Agrega o selecciona una capa")
                 is CapaTexto -> {
                     Seccion("Propiedades de ${capa.nombre}")
                     Row(verticalAlignment = Alignment.Top) {
                         Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                             Text("Contenido", fontWeight = FontWeight.Medium, fontSize = 16.sp)
                             OutlinedTextField(
                                 value = capa.contenido, onValueChange = { c.actualizar(capa.copy(contenido = it)) },
                                 minLines = 3,
                                 modifier = Modifier.fillMaxWidth()
                                     .onPreviewKeyEvent { ev ->
                                         if (ev.type == KeyEventType.KeyDown && ev.isCtrlPressed &&
                                             (ev.key == Key.Enter || ev.key == Key.NumPadEnter)
                                         ) {
                                             c.generar(env); true
                                         } else false
                                     },
                                 shape = RoundedCornerShape(12.dp)
                             )
                         }
                         Spacer(Modifier.width(8.dp))
                         // Los botones quedan a la altura del cuadro, no de la etiqueta fija de arriba (etiqueta ~20 dp + 4 dp de separación).
                         Column(Modifier.padding(top = 24.dp)) {
                             Button(
                                 onClick = { c.actualizar(capa.copy(contenido = clipboard.getText()?.text ?: "")); env.decir("Texto pegado") },
                                 modifier = Modifier.padding(top = 8.dp), shape = RoundedCornerShape(8.dp)
                             ) { Text("Pegar") }

                             Spacer(Modifier.height(8.dp))

                             OutlinedButton(
                                 onClick = { c.actualizar(capa.copy(contenido = "")); env.decir("Texto borrado") },
                                 modifier = Modifier.height(40.dp),
                                 shape = RoundedCornerShape(8.dp),
                                 contentPadding = PaddingValues(horizontal = 8.dp)
                             ) {
                                 Icon(imageVector = Icons.Default.Delete, contentDescription = "Borrar texto")
                             }
                         }
                     }
                     EstiloEditor(
                         e = capa.estilo, 
                         onChange = { c.actualizar(capa.copy(estilo = it)) }, 
                         fuentes = vm.fuentes, 
                         permitirSin = true, 
                         onRecargar = { vm.recargarFuentes() }, 
                         onImportar = importar,
                         onBuscarFuente = { vm.buscarFuenteEnGitHub(it) },
                         onDescargarFuente = { url, nombre -> vm.descargarFuenteDeUrl(url, nombre) },
                         resultadoBusquedaEncontrado = vm.resultadoBusqueda,
                         nombreFuenteEncontrada = vm.nombreFuenteEncontrada,
                         buscandoEstado = vm.buscandoFuente
                     )
                     CampoSlider("Posición X (%)", capa.xPct, -50..150, { c.actualizar(capa.copy(xPct = it)) }, paso = 1)
                     CampoSlider("Posición Y (%)", capa.yPct, -50..150, { c.actualizar(capa.copy(yPct = it)) }, paso = 1)
                 }
                 is CapaImagen -> {
                     Seccion("Propiedades de ${capa.nombre}")
                     CampoSlider("Escala (%)", capa.escala, 10..300, { c.actualizar(capa.copy(escala = it)) }, paso = 5)
                     CampoSlider("Posición X (%)", capa.xPct, -50..150, { c.actualizar(capa.copy(xPct = it)) }, paso = 1)
                     CampoSlider("Posición Y (%)", capa.yPct, -50..150, { c.actualizar(capa.copy(yPct = it)) }, paso = 1)
                 }
             }
         }
     }
     
     Row(
         modifier = Modifier.fillMaxWidth(),
         horizontalArrangement = Arrangement.spacedBy(8.dp)
     ) {
         Button(
             onClick = { c.generar(env) },
             modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(12.dp)
         ) { Text("Generar", fontSize = 14.sp) }
         
         Button(
             onClick = { 
                 val r = c.resultado
                 if (r != null && r.uri != null) Almacen.compartir(ctx, r.uri)
                 else env.decir("Primero genera una imagen")
             },
             enabled = c.resultado != null,
             modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(12.dp)
         ) { Text("Compartir", fontSize = 14.sp) }

         Button(
             onClick = {
                 val r = c.resultado
                 val u = r?.uri
                 if (u != null && Almacen.copiar(ctx, u)) env.decir("Imagen copiada")
                 else env.decir("No se pudo copiar la imagen")
             },
             enabled = c.resultado != null,
             modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(12.dp)
         ) { Text("Copiar", fontSize = 14.sp) }

         Button(
             onClick = { vm.pedirGuardar(c.resultado, "Texto") },
             enabled = c.resultado != null,
             modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(12.dp)
         ) { Text("Guardar", fontSize = 14.sp) }
     }
     
     if (dlgTexto) {
         DialogoNombre("Nueva capa de texto", "", { c.errorNombre(it) }, { c.agregarTexto(it, env); dlgTexto = false }, { dlgTexto = false })
     }
     imagenPendiente?.let { uri ->
         DialogoNombre("Nueva capa de imagen", Imagenes.nombre(ctx, uri).substringBeforeLast('.'), { c.errorNombre(it) }, { c.agregarImagen(it, uri, env); imagenPendiente = null }, { imagenPendiente = null })
     }
     if (dlgRenombrar) {
         val actual = c.capaActual
         if (actual == null) dlgRenombrar = false
         else DialogoNombre("Renombrar capa", actual.nombre, { c.errorNombre(it, ignorar = c.seleccion) }, { c.renombrar(it, env); dlgRenombrar = false }, { dlgRenombrar = false })
     }
}

@Composable
private fun EstiloEditor(
    e: EstiloUi, 
    onChange: (EstiloUi) -> Unit, 
    fuentes: List<String>, 
    permitirSin: Boolean, 
    onRecargar: () -> Unit, 
    onImportar: () -> Unit,
    autoActivo: Boolean = false,
    onBuscarFuente: ((String) -> Unit)? = null,
    onDescargarFuente: (String, String) -> Unit = { _, _ -> },
    resultadoBusquedaEncontrado: String? = null,
    nombreFuenteEncontrada: String? = null,
    buscandoEstado: Boolean = false
) {
    val listaFuentes = if (permitirSin) listOf(SIN) + fuentes else fuentes
    val colores = if (permitirSin) LISTA_COLORES_SIN else LISTA_COLORES
    
    var mostrarDialogoFuente by remember { mutableStateOf(false) }
    var textoBusqueda by remember { mutableStateOf("") }

    Seccion("Estilo del texto")
    CampoLista("Fuente", listaFuentes, e.fuente, { onChange(e.copy(fuente = it)) })
    
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onRecargar, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Recargar") }
        OutlinedButton(onClick = onImportar, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Importar") }
        OutlinedButton(
            onClick = { mostrarDialogoFuente = true },
            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
        ) { Text("Buscar") }
    }

    // DIÁLOGO DE BÚSQUEDA DE FUENTES
    if (mostrarDialogoFuente) {
        AlertDialog(
            onDismissRequest = { mostrarDialogoFuente = false },
            title = { Text("Buscar en Google Fonts") },
            text = {
                Column {
                    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Buscar fuente", fontWeight = FontWeight.Medium, fontSize = 16.sp)
                        OutlinedTextField(
                            value = textoBusqueda,
                            onValueChange = { textoBusqueda = it },
                            placeholder = { Text("Ej: Montserrat, Roboto...") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { onBuscarFuente?.invoke(textoBusqueda) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Buscar") }
                    
                    if (buscandoEstado) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    
                    if (resultadoBusquedaEncontrado != null && nombreFuenteEncontrada != null) {
                        Spacer(Modifier.height(16.dp))
                        Text("Encontrada: $nombreFuenteEncontrada", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                onDescargarFuente(resultadoBusquedaEncontrado, nombreFuenteEncontrada)
                                mostrarDialogoFuente = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Descargar y aplicar") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { mostrarDialogoFuente = false }) { Text("Cerrar") }
            }
        )
    }
    
    CampoSlider(
        etiqueta = "Tamaño de texto", 
        valor = e.tam, 
        rango = 10..300, 
        onValor = { onChange(e.copy(tam = it)) },
        habilitado = !autoActivo 
    )
    
    CampoSlider("Interlineado", e.inter, 0..100, { onChange(e.copy(inter = it)) })
    CampoLista("Color del texto", colores, e.colorTexto, { onChange(e.copy(colorTexto = it)) })
    CampoLista("Alineación", if (permitirSin) ALINEACIONES_SIN else ALINEACIONES, e.align, { onChange(e.copy(align = it)) })

    CampoCheck("Borde", e.borde, { onChange(e.copy(borde = it)) })
    if (e.borde) {
        CampoLista("Color del borde", LISTA_COLORES_SIN, e.colorBorde, { onChange(e.copy(colorBorde = it)) })
        CampoSlider("Grosor", e.grosor, 0..20, { onChange(e.copy(grosor = it)) })
    }
    
    CampoCheck("Sombra", e.sombra, { onChange(e.copy(sombra = it)) })
    if (e.sombra) {
        CampoLista("Color de la sombra", LISTA_COLORES_SIN, e.colorSombra, { onChange(e.copy(colorSombra = it)) })
        CampoSlider("Desplazamiento X", e.sx, -50..50, { onChange(e.copy(sx = it)) })
        CampoSlider("Desplazamiento Y", e.sy, -50..50, { onChange(e.copy(sy = it)) })
        CampoSlider("Desenfoque", e.blur, 0..20, { onChange(e.copy(blur = it)) })
        
        CampoSlider("Opacidad de la sombra (%)", e.sombraOpacidad, 0..100, { onChange(e.copy(sombraOpacidad = it)) })
    }
    
    CampoCheck("Caja de fondo", e.caja, { onChange(e.copy(caja = it)) })
    if (e.caja) {
        CampoLista("Color de la caja", colores, e.colorCaja, { onChange(e.copy(colorCaja = it)) })
        CampoSlider("Relleno", e.padding, 0..300, { onChange(e.copy(padding = it)) }, paso = 5)
        CampoSlider("Radio esquinas", e.radio, 0..200, { onChange(e.copy(radio = it)) })
    }
}

@Composable
private fun rememberImportadorFuentes(vm: AppViewModel): () -> Unit {
    val selector = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? -> if (uri != null) vm.importarFuente(uri) }
    return { selector.launch("*/*") }
}

@Composable
fun CampoSlider(
    etiqueta: String, 
    valor: Int, 
    rango: IntRange, 
    onValor: (Int) -> Unit, 
    paso: Int = 1,
    habilitado: Boolean = true
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            "$etiqueta: $valor" + (if (!habilitado) " (Automático)" else ""), 
            fontWeight = FontWeight.Medium, 
            fontSize = 16.sp,
            color = if (habilitado) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
        val cantidadPasos = ((rango.last - rango.first) / paso) - 1
        Slider(
            value = valor.toFloat(),
            onValueChange = { onValor(it.roundToInt()) },
            valueRange = rango.first.toFloat()..rango.last.toFloat(),
            steps = if (cantidadPasos > 0) cantidadPasos else 0, 
            enabled = habilitado,
            modifier = Modifier.semantics { contentDescription = "$etiqueta, valor actual $valor" }
        )
    }
}

// La vista previa es la imagen en pantalla completa, sin título ni
// botones: Compartir, Copiar y Guardar ya están en el Editor. Se puede
// acercar pellizcando la imagen y moverla con el dedo; un doble toque
// vuelve al tamaño normal.
@Composable
fun PantallaVistaPrevia(vm: AppViewModel) {
    val resultado: Resultado? = vm.texto.resultadoActual()
    if (resultado == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Aún no has generado ninguna imagen.")
        }
        return
    }
    ImagenConZoom(
        bitmap = resultado.vista,
        descripcion = resultado.descripcion,
        modifier = Modifier.fillMaxSize(),
    )
}

// Diálogo con las opciones al guardar: formato, calidad JPG y carpeta.
@Composable
fun DialogoGuardar(vm: AppViewModel) {
    val formatos = listOf("Automático", "PNG", "JPG")
    val carpetas = listOf("Texto", "Recortadas", "Normalizadas", "Collages")
    AlertDialog(
        onDismissRequest = { vm.cancelarGuardar() },
        title = { Text("Guardar imagen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CampoLista("Formato", formatos, vm.dlgGuardarFormato, { vm.dlgGuardarFormato = it })
                if (vm.dlgGuardarFormato != "PNG") {
                    CampoSlider("Calidad JPG (%)", vm.dlgGuardarCalidad, 70..100, { vm.dlgGuardarCalidad = it }, paso = 5)
                }
                CampoLista("Carpeta", carpetas, vm.dlgGuardarCarpeta, { vm.dlgGuardarCarpeta = it })
                Text("Automático guarda PNG si el fondo es transparente y JPG si no.")
            }
        },
        confirmButton = {
            Button(onClick = { vm.confirmarGuardar() }) { Text("Guardar") }
        },
        dismissButton = {
            OutlinedButton(onClick = { vm.cancelarGuardar() }) { Text("Cancelar") }
        },
    )
}

@Composable
fun PantallaAjustes(vm: AppViewModel, onVerManual: () -> Unit) {
    var nombrePlantilla by remember { mutableStateOf("") }
    var expandido by remember { mutableStateOf(false) }
    var plantillaSeleccionada by remember { mutableStateOf("Seleccionar plantilla...") }
    
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
         Text("Configuración", fontSize = 24.sp, fontWeight = FontWeight.Bold)
         
         Card(shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(2.dp)) {
             Column(Modifier.padding(16.dp)) {
                 Text("Tema de la aplicación", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                 Spacer(Modifier.height(8.dp))
                 Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                     Text("Usar tema oscuro")
                     Spacer(Modifier.weight(1f))
                     Switch(checked = vm.forzarOscuro == true, onCheckedChange = { vm.forzarOscuro = it })
                 }
             }
         }
         
         Card(shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(2.dp)) {
             Column(Modifier.padding(16.dp)) {
                 Text("Actualizaciones", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                 Spacer(Modifier.height(8.dp))
                 
                 if (vm.descargando) {
                     if (vm.progresoDescarga < 0f) {
                         LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                     } else {
                         LinearProgressIndicator(
                             progress = { vm.progresoDescarga },
                             modifier = Modifier.fillMaxWidth(),
                         )
                     }
                     Spacer(Modifier.height(8.dp))
                     Text(
                         if (vm.progresoDescarga < 0f) "Descargando..." else "Descargando... ${(vm.progresoDescarga * 100).toInt()}%",
                         fontSize = 14.sp,
                     )
                 } else {
                     Button(
                         onClick = { vm.buscarActualizacion() },
                         modifier = Modifier.fillMaxWidth()
                     ) {
                         Text("Buscar actualizaciones")
                     }
                 }
             }
         }

         Card(shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(2.dp)) {
             Column(Modifier.padding(16.dp)) {
                 Text("Acerca de y ayuda", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                 Spacer(Modifier.height(8.dp))
                 Text("Taller Accesible, versión ${vm.versionInstalada()}")
                 Spacer(Modifier.height(8.dp))
                 Button(
                     onClick = onVerManual,
                     modifier = Modifier.fillMaxWidth()
                 ) {
                     Text("Ver manual")
                 }
             }
         }

         Card(shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(2.dp)) {
             Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                 Text("Plantillas", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                 Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                     Text("Nombre de la plantilla", fontWeight = FontWeight.Medium, fontSize = 16.sp)
                     OutlinedTextField(
                         value = nombrePlantilla, onValueChange = { nombrePlantilla = it },
                         modifier = Modifier.fillMaxWidth()
                     )
                 }
                 Button(
                     onClick = { vm.guardarPlantilla(nombrePlantilla); nombrePlantilla = "" }, 
                     modifier = Modifier.fillMaxWidth()
                 ) {
                     Text("Guardar plantilla")
                 }
                 
                 if (vm.plantillas.isNotEmpty()) {
                     Spacer(Modifier.height(8.dp))
                     Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                         Box(modifier = Modifier.weight(1f)) {
                             OutlinedButton(
                                 onClick = { expandido = true },
                                 modifier = Modifier.fillMaxWidth(),
                                 shape = RoundedCornerShape(8.dp)
                             ) {
                                 Text(plantillaSeleccionada)
                             }
                             DropdownMenu(
                                 expanded = expandido,
                                 onDismissRequest = { expandido = false }
                             ) {
                                 vm.plantillas.forEach { plantilla ->
                                     DropdownMenuItem(
                                         text = { Text(plantilla.nombre) },
                                         onClick = {
                                             plantillaSeleccionada = plantilla.nombre
                                             expandido = false
                                             vm.aplicarPlantilla(plantilla)
                                         }
                                     )
                                 }
                             }
                         }
                         IconButton(
                             onClick = { 
                                 if (plantillaSeleccionada != "Seleccionar plantilla...") {
                                     vm.borrarPlantilla(plantillaSeleccionada)
                                     plantillaSeleccionada = "Seleccionar plantilla..."
                                 } else {
                                     vm.entorno.decir("Selecciona una plantilla primero")
                                 }
                             }
                         ) {
                             Icon(imageVector = Icons.Default.Delete, contentDescription = "Borrar plantilla")
                         }
                     }
                 }
             }
         }
    }
}