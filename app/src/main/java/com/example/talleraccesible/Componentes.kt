package com.example.talleraccesible

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

// ------------------------------------------------------------
// Anuncios directos (para cambios de valor con los botones + / −)
// ------------------------------------------------------------
@Suppress("DEPRECATION")
@Composable
fun rememberAnunciador(): (String) -> Unit {
    val vista = LocalView.current
    return remember(vista) {
        { mensaje: String -> vista.announceForAccessibility(mensaje) }
    }
}

// ------------------------------------------------------------
// Encabezados (TalkBack permite saltar de encabezado en encabezado)
// ------------------------------------------------------------
@Composable
fun Titulo(texto: String) = Text(
    texto,
    style = MaterialTheme.typography.headlineSmall,
    modifier = Modifier.semantics { heading() },
)

@Composable
fun Seccion(texto: String) = Text(
    texto,
    style = MaterialTheme.typography.titleMedium,
    modifier = Modifier.padding(top = 8.dp).semantics { heading() },
)

// ------------------------------------------------------------
// Reemplazo de QSpinBox: campo numérico + botones − / +
// ------------------------------------------------------------
@Composable
fun CampoNumero(
    label: String,
    valor: Int,
    rango: IntRange,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    paso: Int = 1,
) {
    val anunciar = rememberAnunciador()
    var texto by remember(valor) { mutableStateOf(valor.toString()) }
    val parseado = texto.toIntOrNull()
    val invalido = parseado == null || parseado !in rango

    fun cambiarPor(delta: Int) {
        val nuevo = (valor + delta).coerceIn(rango.first, rango.last)
        onChange(nuevo)
        anunciar("$label $nuevo")
    }

    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = texto,
            onValueChange = { nuevo ->
                val limpio = nuevo.filter { it.isDigit() || (it == '-' && rango.first < 0) }.take(6)
                texto = limpio
                limpio.toIntOrNull()?.takeIf { it in rango }?.let(onChange)
            },
            label = { Text(label) },
            singleLine = true,
            enabled = enabled,
            isError = invalido,
            supportingText = { if (invalido) Text("Escribe un valor entre ${rango.first} y ${rango.last}") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .weight(1f)
                .semantics {
                    // También disponibles en el menú de acciones de TalkBack
                    customActions = listOf(
                        CustomAccessibilityAction("Aumentar $label") { cambiarPor(paso); true },
                        CustomAccessibilityAction("Disminuir $label") { cambiarPor(-paso); true },
                    )
                },
        )
        OutlinedButton(
            onClick = { cambiarPor(-paso) },
            enabled = enabled && valor > rango.first,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier
                .padding(top = 8.dp)
                .size(48.dp)
                .semantics { contentDescription = "Disminuir $label" },
        ) { Text("−") }
        OutlinedButton(
            onClick = { cambiarPor(paso) },
            enabled = enabled && valor < rango.last,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier
                .padding(top = 8.dp)
                .size(48.dp)
                .semantics { contentDescription = "Aumentar $label" },
        ) { Text("+") }
    }
}

// ------------------------------------------------------------
// Reemplazo de QComboBox: botón con la opción actual + diálogo de selección
// ------------------------------------------------------------
@Composable
fun CampoLista(
    label: String,
    opciones: List<String>,
    seleccion: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var abierto by remember { mutableStateOf(false) }

    // El texto visible es lo que lee TalkBack: "Color del texto, Blanco, botón".
    OutlinedButton(
        onClick = { abierto = true },
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(seleccion, style = MaterialTheme.typography.bodyLarge)
        }
        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
    }

    if (abierto) {
        AlertDialog(
            onDismissRequest = { abierto = false },
            title = { Text(label) },
            text = {
                LazyColumn(Modifier.selectableGroup()) {
                    items(opciones) { op ->
                        val marcado = op == seleccion
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(
                                    selected = marcado,
                                    role = Role.RadioButton,
                                    onClick = { onSelect(op); abierto = false },
                                )
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = marcado, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(op)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { abierto = false }) { Text("Cancelar") } },
        )
    }
}

// ------------------------------------------------------------
// Reemplazo de QCheckBox: toda la fila es un único control
// ------------------------------------------------------------
@Composable
fun CampoCheck(label: String, marcado: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = marcado, enabled = enabled, role = Role.Checkbox, onValueChange = onChange)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = marcado, onCheckedChange = null, enabled = enabled)
        Spacer(Modifier.width(12.dp))
        Text(label)
    }
}

// ------------------------------------------------------------
// Reemplazo de AccessiblePlainTextEdit (Ctrl+Enter genera)
// ------------------------------------------------------------
@Composable
fun CampoTexto(
    label: String,
    valor: String,
    onChange: (String) -> Unit,
    lineas: Int = 3,
    onCtrlEnter: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = valor,
        onValueChange = onChange,
        label = { Text(label) },
        minLines = lineas,
        modifier = Modifier
            .fillMaxWidth()
            .onPreviewKeyEvent { ev ->
                if (onCtrlEnter != null && ev.type == KeyEventType.KeyDown && ev.isCtrlPressed &&
                    (ev.key == Key.Enter || ev.key == Key.NumPadEnter)
                ) {
                    onCtrlEnter(); true
                } else false
            },
    )
}

@Composable
fun BotonAccion(texto: String, onClick: () -> Unit, enabled: Boolean = true, principal: Boolean = true) {
    val mod = Modifier.fillMaxWidth().heightIn(min = 48.dp)
    if (principal) Button(onClick, mod, enabled) { Text(texto) }
    else OutlinedButton(onClick, mod, enabled) { Text(texto) }
}

// ------------------------------------------------------------
// Vista previa: la descripción permite a un lector de pantalla "ver" el resultado
// ------------------------------------------------------------
@Composable
fun VistaPrevia(r: Resultado?) {
    if (r == null) return
    Image(
        bitmap = r.vista.asImageBitmap(),
        contentDescription = r.descripcion,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
    )
}

/** Botones Generar / Copiar / Compartir / Limpiar compartidos por varias pantallas. */
@Composable
fun BotonesResultado(
    textoGenerar: String,
    resultado: Resultado?,
    ocupado: Boolean,
    onGenerar: () -> Unit,
    onLimpiar: () -> Unit,
    decir: (String) -> Unit,
    textoLimpiar: String = "Limpiar",
) {
    val ctx: Context = LocalContext.current
    BotonAccion(if (ocupado) "Generando, espera" else textoGenerar, onGenerar, enabled = !ocupado)
    BotonAccion(
        "Copiar imagen",
        onClick = {
            val uri = resultado?.uri
            decir(if (uri != null && Almacen.copiar(ctx, uri)) "Copiada" else "No se pudo copiar")
        },
        enabled = resultado?.uri != null, principal = false,
    )
    BotonAccion(
        "Compartir imagen",
        onClick = { resultado?.uri?.let { Almacen.compartir(ctx, it) } },
        enabled = resultado?.uri != null, principal = false,
    )
    BotonAccion(textoLimpiar, onLimpiar, principal = false)
}

// ------------------------------------------------------------
// Diálogo para pedir/editar el nombre de una capa
// ------------------------------------------------------------
@Composable
fun DialogoNombre(
    titulo: String,
    inicial: String,
    validar: (String) -> String?,
    onAceptar: (String) -> Unit,
    onCancelar: () -> Unit,
) {
    var nombre by remember { mutableStateOf(inicial) }
    var error by remember { mutableStateOf<String?>(null) }
    val foco = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { foco.requestFocus() } }

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(titulo) },
        text = {
            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it; error = null },
                label = { Text("Nombre de la capa") },
                singleLine = true,
                isError = error != null,
                supportingText = {
                    error?.let {
                        Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    }
                },
                modifier = Modifier.focusRequester(foco),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val e = validar(nombre)
                if (e == null) onAceptar(nombre) else error = e
            }) { Text("Aceptar") }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } },
    )
}
