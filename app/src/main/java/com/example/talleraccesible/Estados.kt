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

/**
 * Reemplazo de `hablar()` del original: [decir] muestra un Snackbar,
 * que TalkBack anuncia solo (es una región "live") y que además se ve en pantalla.
 */
class Entorno(val app: Application, val scope: CoroutineScope) {
    var decir: (String) -> Unit = {}
}

/** Ejecuta [trabajo] en segundo plano y traduce los errores a mensajes legibles. */
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

// ============================================================
// LIENZO (tamaño + fondo), común al modo simple y al de capas
// ============================================================
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

// ============================================================
// TEXTO · modo simple
// ============================================================
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

// ============================================================
// TEXTO · modo capas
// ============================================================
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

// ============================================================
// CORTADOR
// ============================================================
class CortadorState {
    var nombre by mutableStateOf("")
    var ancho by mutableIntStateOf(0)
    var alto by mutableIntStateOf(0)
    var izq by mutableIntStateOf(0)
    var der by mutableIntStateOf(0)
    var arr by mutableIntStateOf(0)
    var aba by mutableIntStateOf(0)
    var mostrada by mutableStateOf<Resultado?>(null)
    var ocupado by mutableStateOf(false)
    private var bitmap: Bitmap? = null

    fun cargar(env: Entorno, uri: Uri) {
        env.ejecutar({ ocupado = it }, "No se pudo cargar la foto") {
            val (bmp, n) = withContext(Dispatchers.IO) {
                Imagenes.cargar(env.app, uri, 6000).bitmap to Imagenes.nombre(env.app, uri)
            }
            bitmap = bmp; nombre = n; ancho = bmp.width; alto = bmp.height
            mostrada = Resultado(Imagenes.vista(bmp), null, "Foto cargada, ${bmp.width} por ${bmp.height} píxeles")
            env.decir("Foto cargada, ${bmp.width} por ${bmp.height} píxeles")
        }
    }

    private fun rectangulo(): IntArray? {
        val bmp = bitmap ?: return null
        val x0 = bmp.width * izq / 100
        val x1 = bmp.width - bmp.width * der / 100
        val y0 = bmp.height * arr / 100
        val y1 = bmp.height - bmp.height * aba / 100
        return intArrayOf(x0, y0, x1 - x0, y1 - y0)
    }

    fun recortar(env: Entorno) {
        val bmp = bitmap ?: run { env.decir("Carga una foto"); return }
        val r = rectangulo()!!
        if (r[2] < 1 || r[3] < 1) {
            env.decir("Los recortes se superponen. Reduce algún porcentaje"); return
        }
        if (ocupado) return
        env.ejecutar({ ocupado = it }, "Error al recortar") {
            val res = withContext(Dispatchers.Default) {
                val rec = Bitmap.createBitmap(bmp, r[0], r[1], r[2], r[3])
                Almacen.publicar(env.app, Salida(rec, rec.hasAlpha()), "Recortadas", "foto_recortada", "Foto recortada")
            }
            mostrada = res
            env.decir("Guardada, ${r[2]} por ${r[3]} píxeles")
        }
    }

    fun describir(env: Entorno) {
        if (bitmap == null) { env.decir("Sin foto"); return }
        val r = rectangulo()!!
        env.decir(
            "Izquierda $izq por ciento, derecha $der, arriba $arr, abajo $aba. " +
                if (r[2] > 0 && r[3] > 0) "La foto quedará de ${r[2]} por ${r[3]} píxeles" else "Los recortes se superponen",
        )
    }

    fun limpiar(env: Entorno) {
        bitmap = null; nombre = ""; ancho = 0; alto = 0
        izq = 0; der = 0; arr = 0; aba = 0; mostrada = null
        env.decir("Limpio")
    }
}

// ============================================================
// NORMALIZAR
// ============================================================
class NormalizarState {
    val fotos = mutableStateListOf<Uri>()
    var modo by mutableStateOf(MODOS_NORMALIZAR[0])
    var resumen by mutableStateOf<String?>(null)
    var ocupado by mutableStateOf(false)

    fun agregar(env: Entorno, uris: List<Uri>) {
        if (uris.isEmpty()) return
        fotos.addAll(uris)
        env.decir("${fotos.size} fotos")
    }

    fun hacer(env: Entorno) {
        if (fotos.isEmpty()) { env.decir("Agrega fotos"); return }
        if (ocupado) return
        val lista = fotos.toList(); val m = modo
        val tam = if (m.contains("800")) 800 else 1080
        env.decir("Normalizando ${lista.size} fotos")
        env.ejecutar({ ocupado = it }, "Error al normalizar") {
            val fallidas = withContext(Dispatchers.Default) {
                var fallos = 0
                for (uri in lista) {
                    try {
                        val src = Imagenes.cargar(env.app, uri, 6000).bitmap
                        val fin = if (m.startsWith("Proporci")) Render.proporcion(src, tam) else Render.cuadradoCentro(src, tam)
                        val base = Imagenes.nombre(env.app, uri).substringBeforeLast('.') + "_norm"
                        Almacen.guardar(env.app, fin, "Normalizadas", base, false)
                    } catch (ex: Exception) {
                        Log.e("TallerAccesible", "normalizar", ex); fallos++
                    }
                }
                fallos
            }
            val ok = lista.size - fallidas
            val msg = "Normalizadas $ok de ${lista.size} fotos" + if (fallidas > 0) ". $fallidas con error" else ""
            resumen = msg
            env.decir(msg)
        }
    }

    fun limpiar(env: Entorno) { fotos.clear(); resumen = null; env.decir("Limpiado") }
}

// ============================================================
// COLLAGE
// ============================================================
class CollageState {
    val fotos = mutableStateListOf<Uri>()
    var tipo by mutableStateOf(TIPOS_COLLAGE[0])
    var resultado by mutableStateOf<Resultado?>(null)
    var ocupado by mutableStateOf(false)

    fun agregar(env: Entorno, uris: List<Uri>) {
        if (uris.isEmpty()) return
        fotos.addAll(uris)
        env.decir("${fotos.size} fotos" + if (fotos.size > 4) ". Solo se usarán las primeras 4" else "")
    }

    fun crear(env: Entorno) {
        if (fotos.isEmpty()) { env.decir("Agrega foto"); return }
        if (ocupado) return
        val lista = fotos.take(4); val t = tipo
        env.ejecutar({ ocupado = it }, "Error al crear el collage") {
            val r = withContext(Dispatchers.Default) {
                val imgs = lista.map { Render.cuadradoCentro(Imagenes.cargar(env.app, it, 4096).bitmap, 600) }
                val c = Render.collage(imgs, t)
                Almacen.publicar(env.app, Salida(c, false), "Collages", "collage_final", "Collage de ${imgs.size} fotos, tipo $t")
            }
            resultado = r
            env.decir("Collage creado")
        }
    }

    fun limpiar(env: Entorno) { fotos.clear(); resultado = null; env.decir("Limpiado") }
}

// ============================================================
// VIEWMODEL (sobrevive a rotaciones y cambios de tamaño de fuente)
// ============================================================
class AppViewModel(app: Application) : AndroidViewModel(app) {
    val entorno = Entorno(app, viewModelScope)
    var tab by mutableIntStateOf(0)
    var fuentes by mutableStateOf(Fuentes.listar(app))
        private set

    val cortador = CortadorState()
    val normalizar = NormalizarState()
    val collage = CollageState()
    val texto = TextoState()

    fun recargarFuentes(mensaje: String = "Fuentes recargadas") {
        fuentes = Fuentes.listar(getApplication())
        entorno.decir(mensaje)
    }

    fun importarFuente(uri: Uri) {
        val n = Fuentes.importar(getApplication(), uri)
        if (n == null) entorno.decir("Elige un archivo .ttf u .otf") else recargarFuentes("Fuente $n importada")
    }

    // Atajos de teclado físico (Ctrl+G / Ctrl+L)
    fun generarActual() = when (tab) {
        0 -> cortador.recortar(entorno)
        1 -> normalizar.hacer(entorno)
        2 -> collage.crear(entorno)
        else -> texto.generar(entorno)
    }

    fun limpiarActual() = when (tab) {
        0 -> cortador.limpiar(entorno)
        1 -> normalizar.limpiar(entorno)
        2 -> collage.limpiar(entorno)
        else -> texto.limpiar(entorno)
    }
}
