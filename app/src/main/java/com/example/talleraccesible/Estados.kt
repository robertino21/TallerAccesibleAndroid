package com.example.talleraccesible
import android.app.Application
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
                // CAMBIO ACÁ: Guardamos en temporal en lugar de publicar
                val uri = Almacen.guardarTemporal(env.app, s.bitmap)
                Resultado(Imagenes.vista(s.bitmap), uri, "Vista previa generada", s.avisos)
            }
            resultado = r
            env.decir("Vista previa lista. Puedes compartirla o guardarla.")
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
                // CAMBIO ACÁ: Guardamos en temporal
                val uri = Almacen.guardarTemporal(env.app, s.bitmap)
                Resultado(Imagenes.vista(s.bitmap), uri, "Vista previa de capas", s.avisos)
            }
            resultado = r
            env.decir("Vista previa de capas lista.")
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

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val entorno = Entorno(app, viewModelScope)
    var tab by mutableIntStateOf(0)
    var forzarOscuro by mutableStateOf<Boolean?>(null)
    var fuentes by mutableStateOf(Fuentes.listar(app))
        private set
    var plantillas by mutableStateOf<List<String>>(emptyList())
        private set
    val texto = TextoState()

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

    fun guardarPlantilla(nombre: String) {
        if (nombre.isBlank()) return
        plantillas = plantillas + nombre
        entorno.decir("Plantilla $nombre guardada")
    }

    fun guardarActualEnGaleria() {
        val r = texto.simple.resultado ?: texto.capas.resultado
        if (r == null || r.uri == null) {
            entorno.decir("Primero genera una imagen")
            return
        }
        // Ejecutamos en segundo plano para no trabar la pantalla
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val uriNueva = Almacen.guardarEnGaleria(
                    getApplication(), 
                    r.uri, 
                    "Texto", 
                    "imagen_taller"
                )
                withContext(Dispatchers.Main) {
                    entorno.decir("Imagen guardada en la galería")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    entorno.decir("Error al guardar en la galería")
                }
            }
        }
    }
}