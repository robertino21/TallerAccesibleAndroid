package com.example.talleraccesible

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PantallaTexto(vm: AppViewModel) {
    val t = vm.texto
    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "ImageType",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                CampoLista("Modo", MODOS_TEXTO, t.modo, { t.modo = it })
            }
        }
        
        if (t.modo == "Simple") PanelSimple(vm) else PanelCapas(vm)
    }
}

@Composable
private fun PanelSimple(vm: AppViewModel) {
    val env = vm.entorno
    val s = vm.texto.simple
    val importar = rememberImportadorFuentes(vm)
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                OutlinedTextField(
                    value = s.texto,
                    onValueChange = { s.texto = it },
                    modifier = Modifier.weight(1f).heightIn(min = 120.dp).semantics { contentDescription = "Escribe tu texto aquí" },
                    placeholder = { Text("Escribe tu texto aquí...") },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary)
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val textoPortapapeles = clipboard.getText()?.text ?: ""
                        s.texto = textoPortapapeles
                        env.decir("Texto pegado")
                    },
                    modifier = Modifier.padding(top = 8.dp),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("Pegar") }
            }
            Spacer(Modifier.height(12.dp))
            CampoCheck("Ajustar tamaño automáticamente", s.auto, { s.auto = it })
        }
    }

    LienzoEditor(s.lienzo) { env.decir(it) }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            EstiloEditor(
                e = s.estilo, onChange = { s.estilo = it }, fuentes = vm.fuentes, permitirSin = false,
                onRecargar = { vm.recargarFuentes() }, onImportar = importar,
            )
            
            Text("Tamaño del texto: ${s.estilo.tam}pt", fontWeight = FontWeight.Medium)
            Slider(
                value = s.estilo.tam.toFloat(),
                onValueChange = { s.estilo = s.estilo.copy(tam = it.toInt()) },
                valueRange = 10f..200f,
                modifier = Modifier.semantics { contentDescription = "Tamaño del texto, valor actual ${s.estilo.tam}" }
            )
        }
    }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Seccion("Posición")
            CampoLista("Posición vertical", POSICIONES, s.pos, { s.pos = it })
            if (s.pos.startsWith("Personalizada")) {
                CampoNumero("Posición vertical, por ciento", s.yPct, 0..100, { s.yPct = it }, paso = 5)
            }
        }
    }

    Button(
        onClick = { 
            s.generar(env)
            vm.tab = 1 
        },
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text("Generar Imagen", fontSize = 18.sp)
    }
}

// ============================================================
// PANTALLAS NUEVAS
// ============================================================

@Composable
fun PantallaVistaPrevia(vm: AppViewModel) {
    val resultado = vm.texto.simple.resultado ?: vm.texto.capas.resultado

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Vista Previa", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        
        if (resultado != null) {
            Card(shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(4.dp)) {
                Image(
                    bitmap = resultado.bitmap.asImageBitmap(),
                    contentDescription = resultado.descripcion,
                    modifier = Modifier.fillMaxWidth().padding(8.dp)
                )
            }
            Text(resultado.descripcion, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            
            Button(onClick = { vm.compartirActual() }, modifier = Modifier.fillMaxWidth()) {
                Text("Compartir Imagen")
            }
        } else {
            Text("Aún no has generado ninguna imagen.")
        }
    }
}

@Composable
fun PantallaAjustes(vm: AppViewModel) {
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Configuración", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        
        Card(shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(2.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Tema de la aplicación", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Usar tema oscuro")
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = vm.forzarOscuro == true,
                        onCheckedChange = { vm.forzarOscuro = it }
                    )
                }
            }
        }
    }
}

// ============================================================
// COMPONENTES REUTILIZABLES (TUS FUNCIONES ORIGINALES)
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

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Seccion("Lienzo y fondo")
            CampoLista("Tamaño predefinido", LISTA_PRESETS, l.preset, { l.elegirPreset(it) })
            CampoNumero("Ancho en píxeles", l.ancho, 100..4000, { l.ancho = it }, enabled = !fijo, paso = 10)
            CampoNumero("Alto en píxeles", l.alto, 100..4000, { l.alto = it }, enabled = !fijo, paso = 10)
            CampoLista("Tipo de fondo", FONDOS, l.fondoTipo, { l.fondoTipo = it })
            if (l.fondoTipo == "Imagen") {
                OutlinedButton(onClick = { selector.launch("image/*") }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Cargar imagen de fondo")
                }
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
private fun EstiloEditor(
    e: EstiloUi, onChange: (EstiloUi) -> Unit, fuentes: List<String>, permitirSin: Boolean, onRecargar: () -> Unit, onImportar: () -> Unit,
) {
    val listaFuentes = if (permitirSin) listOf(SIN) + fuentes else fuentes
    val colores = if (permitirSin) LISTA_COLORES_SIN else LISTA_COLORES

    Seccion("Estilo del texto")
    CampoLista("Fuente", listaFuentes, e.fuente, { onChange(e.copy(fuente = it)) })
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onRecargar, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Recargar fuentes") }
        OutlinedButton(onClick = onImportar, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Importar fuente") }
    }
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

@Composable
private fun rememberImportadorFuentes(vm: AppViewModel): () -> Unit {
    val selector = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) vm.importarFuente(uri)
    }
    return { selector.launch("*/*") }
}

// (El resto de tus funciones como PanelCapas, BotonesResultado, etc., déjalas tal cual estaban)