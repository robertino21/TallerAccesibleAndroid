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
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: AppViewModel = viewModel()
            // Si querés que respete el tema del sistema, dejalo así.
            // Si agregás el switch más adelante, le pasás vm.forzarOscuro.
            TemaTaller(forzarOscuro = null) { Raiz(vm) }
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

// TUS 4 PESTAÑAS ORIGINALES
private val TITULOS = listOf("Cortador", "Normalizar", "Collage", "Texto")
private val ICONOS = listOf(Icons.Default.Create, Icons.Default.Settings, Icons.Default.Build, Icons.Default.Edit)

@Composable
fun Raiz(vm: AppViewModel = viewModel()) {
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    SideEffect {
        vm.entorno.decir = { mensaje ->
            scope.launch {
                snack.currentSnackbarData?.dismiss()
                snack.showSnackbar(mensaje)
            }
        }
    }

    Scaffold(
        modifier = Modifier.onPreviewKeyEvent { atajo(it, vm) },
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
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
        },
    ) { margen ->
        Box(Modifier.padding(margen)) {
            when (vm.tab) {
                0 -> PantallaCortador(vm)
                1 -> PantallaNormalizar(vm)
                2 -> PantallaCollage(vm)
                else -> PantallaTexto(vm) // Esta es la que rediseñamos
            }
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
        Key.G -> { vm.generarActual(); true }
        Key.L -> { vm.limpiarActual(); true }
        else -> false
    }
}