package com.example.talleraccesible

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
private fun ColumnaPantalla(contenido: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { contenido() }
}

// ============================================================
// CORTADOR
// ============================================================
@Composable
fun PantallaCortador(vm: AppViewModel) {
    val c = vm.cortador
    val env = vm.entorno
    val selector = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) c.cargar(env, uri)
    }
    ColumnaPantalla {
        Titulo("Cortador")
        BotonAccion("Cargar foto", { selector.launch("image/*") })
        Text(if (c.nombre.isEmpty()) "Sin foto" else "${c.nombre}, ${c.ancho} por ${c.alto} píxeles")
        CampoNumero("Recorte izquierdo, por ciento", c.izq, 0..90, { c.izq = it }, paso = 5)
        CampoNumero("Recorte derecho, por ciento", c.der, 0..90, { c.der = it }, paso = 5)
        CampoNumero("Recorte superior, por ciento", c.arr, 0..90, { c.arr = it }, paso = 5)
        CampoNumero("Recorte inferior, por ciento", c.aba, 0..90, { c.aba = it }, paso = 5)
        BotonAccion(if (c.ocupado) "Procesando, espera" else "Recortar", { c.recortar(env) }, enabled = !c.ocupado)
        BotonAccion("Describir recorte", { c.describir(env) }, principal = false)
        BotonAccion("Limpiar", { c.limpiar(env) }, principal = false)
        BotonesCompartir(c.mostrada, env)
        VistaPrevia(c.mostrada)
    }
}

/** Copiar / compartir solo si ya hay un archivo guardado (resultado con URI). */
@Composable
private fun BotonesCompartir(r: Resultado?, env: Entorno) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    if (r?.uri == null) return
    BotonAccion("Copiar imagen", {
        env.decir(if (Almacen.copiar(ctx, r.uri)) "Copiada" else "No se pudo copiar")
    }, principal = false)
    BotonAccion("Compartir imagen", { Almacen.compartir(ctx, r.uri) }, principal = false)
}

// ============================================================
// NORMALIZAR
// ============================================================
@Composable
fun PantallaNormalizar(vm: AppViewModel) {
    val n = vm.normalizar
    val env = vm.entorno
    val selector = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        n.agregar(env, uris)
    }
    ColumnaPantalla {
        Titulo("Normalizar")
        BotonAccion("Agregar fotos", { selector.launch("image/*") })
        Text("${n.fotos.size} fotos")
        CampoLista("Modo", MODOS_NORMALIZAR, n.modo, { n.modo = it })
        BotonAccion(if (n.ocupado) "Normalizando, espera" else "Normalizar", { n.hacer(env) }, enabled = !n.ocupado)
        BotonAccion("Limpiar", { n.limpiar(env) }, principal = false)
        n.resumen?.let { Text(it) }
    }
}

// ============================================================
// COLLAGE
// ============================================================
@Composable
fun PantallaCollage(vm: AppViewModel) {
    val c = vm.collage
    val env = vm.entorno
    val selector = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        c.agregar(env, uris)
    }
    ColumnaPantalla {
        Titulo("Collage")
        BotonAccion("Agregar fotos", { selector.launch("image/*") })
        Text(
            "${c.fotos.size} fotos" +
                if (c.fotos.size > 4) ". Solo se usarán las primeras 4" else "",
        )
        CampoLista("Tipo", TIPOS_COLLAGE, c.tipo, { c.tipo = it })
        BotonesResultado(
            textoGenerar = "Crear collage", resultado = c.resultado, ocupado = c.ocupado,
            onGenerar = { c.crear(env) }, onLimpiar = { c.limpiar(env) }, decir = { env.decir(it) },
            textoLimpiar = "Limpiar collage",
        )
        VistaPrevia(c.resultado)
    }
}
