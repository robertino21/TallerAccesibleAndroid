package com.example.talleraccesible

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ------------------------------------------------------------
// Encabezados (TalkBack permite saltar de encabezado en encabezado)
// ------------------------------------------------------------
@Composable
fun Seccion(texto: String) = Text(
    texto,
    style = MaterialTheme.typography.titleMedium,
    modifier = Modifier.padding(top = 8.dp).semantics { heading() },
)

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

// Muestras de color: se ve el color en vez de leer su nombre en una lista.
// La opción "Sin seleccionar" es un botón con texto, porque no tiene color.
@Composable
fun CampoColor(
    label: String,
    opciones: List<String>,
    seleccion: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("$label: $seleccion", style = MaterialTheme.typography.labelMedium)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            opciones.forEach { nombre ->
                if (nombre == SIN) {
                    OutlinedButton(
                        onClick = { onSelect(nombre) },
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(48.dp),
                    ) { Text("Sin", fontSize = 12.sp) }
                } else {
                    val entero = colorDe(nombre)
                    val fondo = if (entero != null) Color(entero) else Color.Transparent
                    Button(
                        onClick = { onSelect(nombre) },
                        colors = ButtonDefaults.buttonColors(containerColor = fondo),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(48.dp)
                            .border(
                                width = if (seleccion == nombre) 3.dp else 1.dp,
                                color = if (seleccion == nombre) MaterialTheme.colorScheme.primary else Color.Gray,
                                shape = RoundedCornerShape(12.dp),
                            )
                            .semantics {
                                contentDescription = nombre
                                selected = seleccion == nombre
                            },
                    ) {
                        if (seleccion == nombre) {
                            // Luminancia relativa (fórmula WCAG) para elegir
                            // el color de la tilde según el fondo.
                            val lum = 0.2126f * fondo.red + 0.7152f * fondo.green + 0.0722f * fondo.blue
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = if (fondo.alpha < 0.5f || lum > 0.5f) Color.Black else Color.White,
                            )
                        }
                    }
                }
            }
        }
    }
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
            Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Nombre de la capa", fontWeight = FontWeight.Medium, fontSize = 16.sp)
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it; error = null },
                    singleLine = true,
                    isError = error != null,
                    supportingText = {
                        error?.let {
                            Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        }
                    },
                    modifier = Modifier.fillMaxWidth().focusRequester(foco),
                )
            }
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
