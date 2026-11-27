package com.example.talleraccesible

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: AppViewModel = viewModel()
            TemaTaller(forzarOscuro = vm.forzarOscuro) { Raiz(vm) }
        }
    }
}

@Composable
fun TemaTaller(forzarOscuro: Boolean?, contenido: @Composable () -> Unit) {
    val sistemaOscuro = isSystemInDarkTheme()
    val oscuro = forzarOscuro ?: sistemaOscuro 
    val ctx = LocalContext.current
    val esquema = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (oscuro) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        oscuro -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = esquema) {
        Surface(color = MaterialTheme.colorScheme.background) { contenido() }
    }
}

// LAS 4 PESTAÑAS (Usando iconos que ya sabemos que existen)
private val TITULOS = listOf("Editor", "Herramientas", "Vista previa", "Ajustes")
private val ICONOS = listOf(Icons.Default.Edit, Icons.Default.Build, Icons.Default.Home, Icons.Default.Settings)

@Composable
fun Raiz(vm: AppViewModel = viewModel()) {
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val vista = LocalView.current
    var mostrarManual by remember { mutableStateOf(false) }

    // Al abrir la aplicación se revisa si hay una versión nueva, sin
    // molestar: solo avisa cuando la hay.
    LaunchedEffect(Unit) {
        vm.buscarActualizacion(silencioso = true)
    }

    SideEffect {
        vm.entorno.decir = { mensaje ->
            scope.launch {
                // Jieshuo no anuncia los Snackbar (por eso no se oía ni
                // "Ya estás al día" ni "Imagen guardada en la galería"),
                // así que el mensaje también se anuncia como evento de
                // accesibilidad, que Jieshuo sí lee. El Snackbar queda
                // para quien ve la pantalla.
                vista.announceForAccessibility(mensaje)
                snack.currentSnackbarData?.dismiss()
                snack.showSnackbar(mensaje)
            }
        }
    }

    Scaffold(
        modifier = Modifier.onPreviewKeyEvent { atajo(it, vm) },
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            if (!mostrarManual) {
                NavigationBar {
                    TITULOS.forEachIndexed { i, titulo ->
                        NavigationBarItem(
                            selected = vm.tab == i,
                            onClick = { vm.tab = i },
                            icon = { Icon(ICONOS[i], contentDescription = null) },
                            label = { Text(titulo) },
                        )
                    }
                }
            }
        },
    ) { margen ->
        Box(Modifier.padding(margen)) {
            if (mostrarManual) {
                PantallaManual(onVolver = { mostrarManual = false })
            } else {
                when (vm.tab) {
                    0 -> PantallaTexto(vm)
                    1 -> PantallaHerramientas(vm)
                    2 -> PantallaVistaPrevia(vm)
                    3 -> PantallaAjustes(vm, onVerManual = { mostrarManual = true })
                }
            }
        }
        if (vm.dlgGuardarVisible) DialogoGuardar(vm)
        // Aviso de actualización: solo se descarga si el usuario lo confirma.
        vm.actualizacionPendiente?.let { actualizacion ->
            AlertDialog(
                onDismissRequest = { vm.descartarActualizacion() },
                title = { Text("Actualización disponible") },
                text = { Text("Hay una nueva versión: ${actualizacion.version}. La instalada es ${vm.versionInstalada()}. ¿Descargar e instalar?") },
                confirmButton = {
                    TextButton(onClick = { vm.descargarActualizacion() }) { Text("Descargar") }
                },
                dismissButton = {
                    TextButton(onClick = { vm.descartarActualizacion() }) { Text("Ahora no") }
                }
            )
        }
    }
}

private fun atajo(ev: KeyEvent, vm: AppViewModel): Boolean {
    if (ev.type != KeyEventType.KeyDown || !ev.isCtrlPressed) return false
    return when (ev.key) {
        Key.One -> { vm.tab = 0; true }
        Key.Two -> { vm.tab = 1; true }
        Key.Three -> { vm.tab = 2; true }
        Key.Four -> { vm.tab = 3; true }
        Key.G -> {
            if (vm.tab == 0) { vm.generarActual(); vm.tab = 2 }
            true
        }
        Key.L -> {
            if (vm.tab == 0) vm.limpiarActual()
            else if (vm.tab == 1) vm.herramientas.limpiar(vm.entorno)
            true
        }
        else -> false
    }
}