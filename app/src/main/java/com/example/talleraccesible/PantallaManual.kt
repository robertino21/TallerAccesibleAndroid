package com.example.talleraccesible

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// El manual viene en assets/manual.txt (copia del manual de TalkBack).
// Se muestra con encabezados para que TalkBack pueda saltar de sección
// en sección, igual que en el resto de la aplicación.
@Composable
fun PantallaManual(onVolver: () -> Unit) {
    val contexto = LocalContext.current
    BackHandler { onVolver() }
    val texto by produceState(initialValue = "", contexto) {
        value = withContext(Dispatchers.IO) {
            try {
                contexto.assets.open("manual.txt").bufferedReader().readText()
            } catch (e: Exception) {
                ""
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onVolver) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
            }
            Text("Manual de Taller Accesible", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
        if (texto.isBlank()) {
            Text("Cargando manual...", Modifier.padding(16.dp))
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(bloquesManual(texto)) { bloque ->
                    when (bloque) {
                        is BloqueManual.Titulo -> Seccion(bloque.texto)
                        is BloqueManual.Parrafo -> Text(bloque.texto, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

// Un bloque es un título de sección (empieza con "N. " y no termina en
// punto, por ejemplo "3. Editor, Modo Simple") o un párrafo.
private sealed class BloqueManual {
    class Titulo(val texto: String) : BloqueManual()
    class Parrafo(val texto: String) : BloqueManual()
}

private fun bloquesManual(texto: String): List<BloqueManual> {
    val titulo = Regex("^\\d+\\.\\s.*")
    return texto.split("\n\n")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { bloque ->
            val lineas = bloque.lines()
            val juntado = lineas.joinToString(" ") { it.trim() }.trim()
            if (titulo.matches(lineas.first()) && !juntado.endsWith(".")) {
                BloqueManual.Titulo(juntado)
            } else {
                BloqueManual.Parrafo(juntado)
            }
        }
}
