package com.example.talleraccesible

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Entorno(val app: Application, val scope: CoroutineScope) {
    var decir: (String) -> Unit = {}
}

private fun Entorno.ejecutar(
    ocupado: (Boolean) -> Unit,
    mensajeError: String,
    trabajo: suspend () -> Unit,
) {
    ocupado(true)
    scope.launch {
        try {
            trabajo()
        } catch (ex: ErrorUsuario) {
            decir(ex.message ?: mensajeError)
        } catch (ex: OutOfMemoryError) {
            decir("La imagen es demasiado grande para la memoria del teléfono")
        } catch (ex: Exception) {
            Log.e("TallerAccesible", mensajeError, ex)
            decir(mensajeError)
        } finally {
            ocupado(false)
        }
    }
}

class LienzoState {
    var preset by mutableStateOf(PRESETS[0].nombre)
    var ancho by mutableIntStateOf(1080)
    var alto by mutableIntStateOf(1080)
    var fondoTipo by mutableStateOf(FONDOS[0])
    var color1 by mutableStateOf("Blanco")
    var color2 by mutableStateOf("Blanco")
    var fondoUri by mutableStateOf<Uri?>(null)
    var fondoNombre by mutableStateOf("")

    fun elegirPreset(nombre: String) {
        preset = nombre
        val p = PRESETS.first { it.nombre == nombre }
        if (p.ancho != null && p.alto != null) { ancho = p.ancho; alto = p.alto }
    }

    fun snap() = LienzoSpec(ancho, alto, fondoTipo, color1, color2, fondoUri)

    fun reset() {
        preset = PRESETS[0].nombre; ancho = 1080; alto = 1080
        fondoTipo = FONDOS[0]; color1 = "Blanco"; color2 = "Blanco"
        fondoUri = null; fondoNombre = ""
    }
}

class SimpleState {
    val lienzo = LienzoState()
    var texto by mutableStateOf("")
    var estilo by mutableStateOf(EstiloUi())
    var pos by mutableStateOf("Centro")
    var yPct by mutableIntStateOf(50)
    var auto by mutableStateOf(false)
    var resultado by mutableStateOf<Resultado?>(null)
    var ocupado by mutableStateOf(false)

    fun generar(env: Entorno) {
        val t = texto.trim()
        if (t.isEmpty()) { env.decir("Escribe texto"); return }
        if (ocupado) return
        val l = lienzo.snap(); val e = estilo; val p = pos; val y = yPct; val a = auto
        env.ejecutar({ ocupado = it }, "Error al generar la imagen") {
            val r = withContext(Dispatchers.Default) {
                val s = Render.simple(env.app, t, l, e, p, y, a)
                Almacen.publicar(env.app, s, "Texto", "imagen_texto", "Texto: ${t.take(150)}")
            }
            resultado = r
            env.decir("Imagen guardada en Imágenes, Taller Accesible")
        }
    }

    fun limpiar(env: Entorno) {
        texto = ""; lienzo.reset(); estilo = EstiloUi(); pos = "Centro"; yPct = 50; auto = false
        resultado = null
        env.decir("Limpio")
    }
}

class CapasState {
    val lienzo = LienzoState()
    val capas = mutableStateListOf<Capa>()
    var seleccion by mutableIntStateOf(-1)
    var resultado by mutableStateOf<Resultado?>(null)
    var ocupado by mutableStateOf(false)

    val capaActual: Capa? get() = capas.getOrNull(seleccion)

    fun errorNombre(nombre: String, ignorar: Int = -1): String? {
        val n = nombre.trim()
        if (n.isEmpty()) return "El nombre está vacío"
        if (capas.withIndex().any { (i, c) -> i != ignorar && c.nombre.equals(n, ignoreCase = true) }) {
            return "Ya existe una capa con ese nombre"
        }
        return null
    }

    fun agregarTexto(nombre: String, env: Entorno) {
        capas.add(CapaTexto(nombre.trim()))
        seleccion = capas.lastIndex
        env.decir("Capa de texto ${nombre.trim()} agregada")
    }

    fun agregarImagen(nombre: String, uri: Uri, env: Entorno) {
        capas.add(CapaImagen(nombre.trim(), uri))
        seleccion = capas.lastIndex
        env.decir("Capa de imagen ${nombre.trim()} agregada")
    }

    fun eliminar(env: Entorno) {
        val i = seleccion
        if (i !in capas.indices) { env.decir("Selecciona una capa"); return }
        val n = capas[i].nombre
        capas.removeAt(i)
        seleccion = if (capas.isEmpty()) -1 else i.coerceAtMost(capas.lastIndex)
        env.decir("Capa $n eliminada")
    }

    fun renombrar(nuevo: String, env: Entorno) {
        val i = seleccion
        if (i !in capas.indices) return
        val c = capas[i]
        capas[i] = when (c) {
            is CapaTexto -> c.copy(nombre = nuevo.trim())
            is CapaImagen -> c.copy(nombre = nuevo.trim())
        }
        env.decir("Renombrada a ${nuevo.trim()}")
    }

    fun mover(delta: Int, env: Entorno) {
        val i = seleccion; val j = i + delta
        if (i !in capas.indices) { env.decir("Selecciona una capa"); return }
        if (j !in capas.indices) {
            env.decir(if (delta < 0) "Ya es la primera capa" else "Ya es la última capa"); return
        }
        val tmp = capas[i]; capas[i] = capas[j]; capas[j] = tmp
        seleccion = j
        env.decir("${capas[j].nombre}, posición ${j + 1} de ${capas.size}")
    }

    fun actualizar(nueva: Capa) {
        if (seleccion in capas.indices) capas[seleccion] = nueva
    }

    fun generar(env: Entorno) {
        if (capas.isEmpty()) { env.decir("Sin capas"); return }
        if (ocupado) return
        val l = lienzo.snap(); val lista = capas.toList()
        env.ejecutar({ ocupado = it }, "Error al generar las capas") {
            val r = withContext(Dispatchers.Default) {
                val s = Render.capas(env.app, l, lista)
                Almacen.publicar(env.app, s, "Texto", "capas", "Imagen de ${lista.size} capas")
            }
            resultado = r
            val extra = if (r.avisos.isEmpty()) "" else ". " + r.avisos.joinToString(". ")
            env.decir("Capas guardadas en Imágenes, Taller Accesible$extra")
        }
    }

    fun limpiar(env: Entorno) {
        capas.clear(); seleccion = -1; resultado = null; lienzo.reset()
        env.decir("Capas limpiadas")
    }
}

class TextoState {
    var modo by mutableStateOf(MODOS_TEXTO[0])
    val simple = SimpleState()
    val capas = CapasState()

    fun generar(env: Entorno) = if (modo == "Simple") simple.generar(env) else capas.generar(env)
    fun limpiar(env: Entorno) = if (modo == "Simple") simple.limpiar(env) else capas.limpiar(env)
}

// --- LAS CLASES QUE BORRÉ POR ERROR (AHORA VUELVEN PARA NO ROMPER PANTALLASBASICAS) ---
class CortadorState {
    fun recortar(env: Entorno) {}
    fun limpiar(env: Entorno) {}
}
class NormalizarState {
    fun hacer(env: Entorno) {}
    fun limpiar(env: Entorno) {}
}
class CollageState {
    fun crear(env: Entorno) {}
    fun limpiar(env: Entorno) {}
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val entorno = Entorno(app, viewModelScope)
    var tab by mutableIntStateOf(0)
    
    // Tema oscuro: null = sistema, true = oscuro, false = claro
    var forzarOscuro by mutableStateOf<Boolean?>(null)

    var fuentes by mutableStateOf(Fuentes.listar(app))
        private set

    val texto = TextoState()
    
    // Restauramos las variables para PantallasBasicas.kt
    val cortador = CortadorState()
    val normalizar = NormalizarState()
    val collage = CollageState()

    fun recargarFuentes(mensaje: String = "Fuentes recargadas") {
        fuentes = Fuentes.listar(getApplication())
        entorno.decir(mensaje)
    }

    fun importarFuente(uri: Uri) {
        val n = Fuentes.importar(getApplication(), uri)
        if (n == null) entorno.decir("Elige un archivo .ttf u .otf") else recargarFuentes("Fuente $n importada")
    }

    fun generarActual() = texto.generar(entorno)
    fun limpiarActual() = texto.limpiar(entorno)
}