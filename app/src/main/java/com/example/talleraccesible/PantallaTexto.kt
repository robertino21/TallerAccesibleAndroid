package com.example.talleraccesible

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
fun PantallaTexto(vm: AppViewModel) {
    val t = vm.texto
    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Titulo("Generador de imagen")
        CampoLista("Modo", MODOS_TEXTO, t.modo, { t.modo = it })
        if (t.modo == "Simple") PanelSimple(vm) else PanelCapas(vm)
    }
}

// ============================================================
// Lienzo: tamaño + fondo (común a los dos modos)
// ============================================================
@Composable
private fun LienzoEditor(l: LienzoState, decir: (String) -> Unit) {
    val ctx = LocalContext.current
    val selector = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            l.fondoUri = uri
            l.fondoNombre = Imagenes.nombre(ctx, uri)
            decir("Fondo cargado")
        }
    }
    val fijo = PRESETS.first { it.nombre == l.preset }.ancho != null

    Seccion("Lienzo y fondo")
    CampoLista("Tamaño predefinido", LISTA_PRESETS, l.preset, { l.elegirPreset(it) })
    CampoNumero("Ancho en píxeles", l.ancho, 100..4000, { l.ancho = it }, enabled = !fijo, paso = 10)
    CampoNumero("Alto en píxeles", l.alto, 100..4000, { l.alto = it }, enabled = !fijo, paso = 10)
    CampoLista("Tipo de fondo", FONDOS, l.fondoTipo, { l.fondoTipo = it })
    if (l.fondoTipo == "Imagen") {
        OutlinedButton(
            onClick = { selector.launch("image/*") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text("Cargar imagen de fondo") }
        Text(if (l.fondoNombre.isEmpty()) "Sin fondo" else "Fondo: ${l.fondoNombre}")
    } else {
        CampoLista("Color de fondo", LISTA_COLORES, l.color1, { l.color1 = it })
        if (l.fondoTipo.contains("Degradado")) {
            CampoLista("Segundo color del degradado", LISTA_COLORES, l.color2, { l.color2 = it })
        }
    }
}

// ============================================================
// Estilo de texto (compartido entre modo simple y capas de texto)
// ============================================================
@Composable
private fun EstiloEditor(
    e: EstiloUi,
    onChange: (EstiloUi) -> Unit,
    fuentes: List<String>,
    permitirSin: Boolean,
    onRecargar: () -> Unit,
    onImportar: () -> Unit,
) {
    val listaFuentes = if (permitirSin) listOf(SIN) + fuentes else fuentes
    val colores = if (permitirSin) LISTA_COLORES_SIN else LISTA_COLORES

    Seccion("Estilo del texto")
    CampoLista("Fuente", listaFuentes, e.fuente, { onChange(e.copy(fuente = it)) })
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onRecargar, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
            Text("Recargar fuentes")
        }
        OutlinedButton(onClick = onImportar, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
            Text("Importar fuente")
        }
    }
    CampoNumero("Tamaño del texto", e.tam, (if (permitirSin) 0 else 10)..500, { onChange(e.copy(tam = it)) }, paso = 5)
    CampoNumero("Interlineado", e.inter, 0..100, { onChange(e.copy(inter = it)) })
    CampoLista("Color del texto", colores, e.colorTexto, { onChange(e.copy(colorTexto = it)) })
    CampoLista("Alineación", if (permitirSin) ALINEACIONES_SIN else ALINEACIONES, e.align, { onChange(e.copy(align = it)) })

    CampoCheck("Borde", e.borde, { onChange(e.copy(borde = it)) })
    if (e.borde) {
        CampoLista("Color del borde", LISTA_COLORES_SIN, e.colorBorde, { onChange(e.copy(colorBorde = it)) })
        CampoNumero("Grosor del borde", e.grosor, 0..20, { onChange(e.copy(grosor = it)) })
    }

    CampoCheck("Sombra", e.sombra, { onChange(e.copy(sombra = it)) })
    if (e.sombra) {
        CampoLista("Color de la sombra", LISTA_COLORES_SIN, e.colorSombra, { onChange(e.copy(colorSombra = it)) })
        CampoNumero("Desplazamiento horizontal de la sombra", e.sx, -50..50, { onChange(e.copy(sx = it)) })
        CampoNumero("Desplazamiento vertical de la sombra", e.sy, -50..50, { onChange(e.copy(sy = it)) })
        CampoNumero("Desenfoque de la sombra", e.blur, 0..20, { onChange(e.copy(blur = it)) })
    }

    CampoCheck("Caja detrás del texto", e.caja, { onChange(e.copy(caja = it)) })
    if (e.caja) {
        CampoLista("Color de la caja", colores, e.colorCaja, { onChange(e.copy(colorCaja = it)) })
        CampoNumero("Relleno de la caja", e.padding, 0..200, { onChange(e.copy(padding = it)) }, paso = 5)
        CampoNumero("Radio de las esquinas de la caja", e.radio, 0..100, { onChange(e.copy(radio = it)) })
    }
}

/** Botones de fuentes + selector para importar .ttf/.otf. */
@Composable
private fun rememberImportadorFuentes(vm: AppViewModel): () -> Unit {
    val selector = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) vm.importarFuente(uri)
    }
    return { selector.launch("*/*") }
}

// ============================================================
// MODO SIMPLE
// ============================================================
@Composable
private fun PanelSimple(vm: AppViewModel) {
    val env = vm.entorno
    val s = vm.texto.simple
    val importar = rememberImportadorFuentes(vm)

    CampoTexto("Texto", s.texto, { s.texto = it }, lineas = 4, onCtrlEnter = { s.generar(env) })
    LienzoEditor(s.lienzo) { env.decir(it) }
    EstiloEditor(
        e = s.estilo, onChange = { s.estilo = it }, fuentes = vm.fuentes, permitirSin = false,
        onRecargar = { vm.recargarFuentes() }, onImportar = importar,
    )
    CampoCheck("Ajustar tamaño automáticamente", s.auto, { s.auto = it })

    Seccion("Posición")
    CampoLista("Posición vertical", POSICIONES, s.pos, { s.pos = it })
    if (s.pos.startsWith("Personalizada")) {
        CampoNumero("Posición vertical, por ciento", s.yPct, 0..100, { s.yPct = it }, paso = 5)
    }

    BotonesResultado(
        textoGenerar = "Generar", resultado = s.resultado, ocupado = s.ocupado,
        onGenerar = { s.generar(env) }, onLimpiar = { s.limpiar(env) }, decir = { env.decir(it) },
    )
    VistaPrevia(s.resultado)
}

// ============================================================
// MODO CAPAS
// ============================================================
@Composable
private fun PanelCapas(vm: AppViewModel) {
    val env = vm.entorno
    val c = vm.texto.capas
    val ctx = LocalContext.current
    val importar = rememberImportadorFuentes(vm)

    var dlgTexto by remember { mutableStateOf(false) }
    var dlgRenombrar by remember { mutableStateOf(false) }
    var imagenPendiente by remember { mutableStateOf<Uri?>(null) }

    val selectorImagen = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) imagenPendiente = uri
    }

    LienzoEditor(c.lienzo) { env.decir(it) }

    // ---- Lista de capas (equivale a QListWidget) ----
    Seccion("Capas")
    if (c.capas.isEmpty()) {
        Text("Sin capas")
    } else {
        Text("El orden de la lista es el orden de dibujo: la primera capa queda al fondo.")
        Column(Modifier.selectableGroup()) {
            c.capas.forEachIndexed { i, capa ->
                val marcada = i == c.seleccion
                val tipo = if (capa is CapaTexto) "capa de texto" else "capa de imagen"
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .selectable(selected = marcada, role = Role.RadioButton, onClick = { c.seleccion = i })
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
    BotonAccion("Agregar capa de imagen", { selectorImagen.launch("image/*") }, principal = false)
    BotonAccion("Renombrar capa", { dlgRenombrar = true }, enabled = haySeleccion, principal = false)
    BotonAccion("Eliminar capa", { c.eliminar(env) }, enabled = haySeleccion, principal = false)
    BotonAccion("Subir capa en la lista", { c.mover(-1, env) }, enabled = haySeleccion, principal = false)
    BotonAccion("Bajar capa en la lista", { c.mover(1, env) }, enabled = haySeleccion, principal = false)

    // ---- Propiedades de la capa seleccionada ----
    when (val capa = c.capaActual) {
        null -> Text("Agrega o selecciona una capa para editar sus propiedades")
        is CapaTexto -> {
            Seccion("Propiedades de la capa ${capa.nombre}")
            CampoTexto(
                "Contenido de la capa", capa.contenido,
                { c.actualizar(capa.copy(contenido = it)) }, lineas = 3,
            )
            EstiloEditor(
                e = capa.estilo, onChange = { c.actualizar(capa.copy(estilo = it)) },
                fuentes = vm.fuentes, permitirSin = true,
                onRecargar = { vm.recargarFuentes() }, onImportar = importar,
            )
            CampoNumero("Posición horizontal, por ciento", capa.xPct, 0..100, { c.actualizar(capa.copy(xPct = it)) }, paso = 5)
            CampoNumero("Posición vertical, por ciento", capa.yPct, 0..100, { c.actualizar(capa.copy(yPct = it)) }, paso = 5)
        }
        is CapaImagen -> {
            Seccion("Propiedades de la capa ${capa.nombre}")
            CampoNumero("Escala, por ciento", capa.escala, 10..300, { c.actualizar(capa.copy(escala = it)) }, paso = 10)
            CampoNumero("Posición horizontal, por ciento", capa.xPct, 0..100, { c.actualizar(capa.copy(xPct = it)) }, paso = 5)
            CampoNumero("Posición vertical, por ciento", capa.yPct, 0..100, { c.actualizar(capa.copy(yPct = it)) }, paso = 5)
        }
    }

    BotonesResultado(
        textoGenerar = "Generar capas", resultado = c.resultado, ocupado = c.ocupado,
        onGenerar = { c.generar(env) }, onLimpiar = { c.limpiar(env) }, decir = { env.decir(it) },
        textoLimpiar = "Limpiar capas",
    )
    VistaPrevia(c.resultado)

    // ---- Diálogos ----
    if (dlgTexto) {
        DialogoNombre(
            titulo = "Nueva capa de texto", inicial = "",
            validar = { c.errorNombre(it) },
            onAceptar = { c.agregarTexto(it, env); dlgTexto = false },
            onCancelar = { dlgTexto = false },
        )
    }
    imagenPendiente?.let { uri ->
        DialogoNombre(
            titulo = "Nueva capa de imagen",
            inicial = Imagenes.nombre(ctx, uri).substringBeforeLast('.'),
            validar = { c.errorNombre(it) },
            onAceptar = { c.agregarImagen(it, uri, env); imagenPendiente = null },
            onCancelar = { imagenPendiente = null },
        )
    }
    if (dlgRenombrar) {
        val actual = c.capaActual
        if (actual == null) {
            dlgRenombrar = false
        } else {
            DialogoNombre(
                titulo = "Renombrar capa", inicial = actual.nombre,
                validar = { c.errorNombre(it, ignorar = c.seleccion) },
                onAceptar = { c.renombrar(it, env); dlgRenombrar = false },
                onCancelar = { dlgRenombrar = false },
            )
        }
    }
}
