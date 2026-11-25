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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
