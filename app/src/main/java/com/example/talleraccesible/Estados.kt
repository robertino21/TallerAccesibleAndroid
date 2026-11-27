package com.example.talleraccesible

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class Entorno(val app: Application, val scope: CoroutineScope) {
    var decir: (String) -> Unit = {}
}

// Una versión nueva encontrada, esperando que el usuario confirme la descarga.
data class Actualizacion(val version: String, val url: String, val notas: String = "")

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
    var opacidadFondo by mutableIntStateOf(100)

    fun elegirPreset(nombre: String) {
        preset = nombre
        val p = PRESETS.first { it.nombre == nombre }
        if (p.ancho != null && p.alto != null) { ancho = p.ancho; alto = p.alto }
    }

    fun snap() = LienzoSpec(ancho, alto, fondoTipo, color1, color2, fondoUri, opacidadFondo)

    fun reset() {
        preset = PRESETS[0].nombre; ancho = 1080; alto = 1080
        fondoTipo = FONDOS[0]; color1 = "Blanco"; color2 = "Blanco"
        fondoUri = null; fondoNombre = ""
        opacidadFondo = 100
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
                // Guardamos en caché, NO en la galería
                val uri = Almacen.guardarTemporal(env.app, s.bitmap, s.png)
                Resultado(Imagenes.vista(s.bitmap), uri, "Vista previa generada", s.avisos, s.png)
            }
            resultado = r
            val extra = if (r.avisos.isEmpty()) "" else ". " + r.avisos.joinToString(". ")
            env.decir("Vista previa lista. Puedes compartirla o guardarla.$extra")
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
                // Guardamos en caché, NO en la galería
                val uri = Almacen.guardarTemporal(env.app, s.bitmap, s.png)
                Resultado(Imagenes.vista(s.bitmap), uri, "Vista previa de capas", s.avisos, s.png)
            }
            resultado = r
            val extra = if (r.avisos.isEmpty()) "" else ". " + r.avisos.joinToString(". ")
            env.decir("Vista previa de capas lista.$extra")
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

    // El resultado del modo actual, no el último que se generó en cualquier modo.
    fun resultadoActual(): Resultado? = if (modo == "Simple") simple.resultado else capas.resultado

    fun generar(env: Entorno) = if (modo == "Simple") simple.generar(env) else capas.generar(env)
    fun limpiar(env: Entorno) = if (modo == "Simple") simple.limpiar(env) else capas.limpiar(env)
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val entorno = Entorno(app, viewModelScope)
    var tab by mutableIntStateOf(0)
    var forzarOscuro by mutableStateOf<Boolean?>(null)
    var fuentes by mutableStateOf(Fuentes.listar(app))
        private set
    
    val texto = TextoState()
    val herramientas = HerramientasState()

    private val prefs = app.getSharedPreferences("taller_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    var plantillas by mutableStateOf<List<Plantilla>>(emptyList())
        private set

    // Variables para el buscador de fuentes
    var buscandoFuente by mutableStateOf(false)
        private set
    var resultadoBusqueda by mutableStateOf<String?>(null)
        private set
    var nombreFuenteEncontrada by mutableStateOf<String?>(null)
        private set

    init {
        val json = prefs.getString("plantillas_json", null)
        if (json != null) {
            val tipo = object : TypeToken<List<Plantilla>>() {}.type
            val leidas: List<Plantilla> = try {
                gson.fromJson(json, tipo) ?: emptyList()
            } catch (ex: Exception) {
                emptyList()
            }
            // Las plantillas viejas no traen modo ni capas: se dejan válidas.
            plantillas = leidas.map { it.saneada() }
        }
    }

    private fun guardarPlantillasEnMemoria(lista: List<Plantilla>) {
        plantillas = lista
        prefs.edit().putString("plantillas_json", gson.toJson(lista)).apply()
    }

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
        val simple = texto.simple
        val capas = texto.capas
        // El lienzo es el del modo actual (Simple y Capas tienen lienzo propio).
        // Se guarda el modo actual completo: en Simple, el estilo y la posición;
        // en Capas, también la lista de capas. Si ya existe una con ese nombre,
        // se actualiza en vez de duplicarla.
        val lienzoActual = if (texto.modo == "Capas") capas.lienzo else simple.lienzo
        val nueva = Plantilla(
            nombre = nombre,
            modo = texto.modo,
            estilo = simple.estilo,
            lienzoAncho = lienzoActual.ancho,
            lienzoAlto = lienzoActual.alto,
            preset = lienzoActual.preset,
            pos = simple.pos,
            yPct = simple.yPct,
            capas = capas.capas.map { it.aPlantilla() },
        )
        val yaExistia = plantillas.any { it.nombre.equals(nombre, ignoreCase = true) }
        val lista = plantillas.filterNot { it.nombre.equals(nombre, ignoreCase = true) } + nueva
        guardarPlantillasEnMemoria(lista)
        entorno.decir(if (yaExistia) "Plantilla $nombre actualizada" else "Plantilla $nombre guardada")
    }

    fun borrarPlantilla(nombre: String) {
        guardarPlantillasEnMemoria(plantillas.filter { it.nombre != nombre })
        entorno.decir("Plantilla borrada")
    }

    fun aplicarPlantilla(plantilla: Plantilla) {
        val p = plantilla.saneada()
        val simple = texto.simple
        simple.estilo = p.estilo
        simple.pos = p.pos
        simple.yPct = p.yPct
        // El lienzo se aplica al modo que trae la plantilla.
        val lienzoDestino = if (p.modo == "Capas") texto.capas.lienzo else simple.lienzo
        lienzoDestino.ancho = p.lienzoAncho
        lienzoDestino.alto = p.lienzoAlto
        lienzoDestino.preset = p.preset
        texto.modo = p.modo
        val c = texto.capas
        c.capas.clear()
        if (p.modo == "Capas") {
            c.capas.addAll(p.capas.map { it.aCapa() })
            c.seleccion = if (c.capas.isEmpty()) -1 else 0
        } else {
            c.seleccion = -1
        }
        c.resultado = null
        simple.resultado = null
        entorno.decir("Plantilla ${p.nombre} aplicada")
    }

    fun limpiarTodo() {
        texto.limpiar(entorno)
        entorno.decir("Lienzo limpiado")
    }

    // Opciones del diálogo al guardar: formato, calidad JPG y carpeta.
    var dlgGuardarVisible by mutableStateOf(false)
        private set
    var dlgGuardarResultado by mutableStateOf<Resultado?>(null)
        private set
    var dlgGuardarCarpeta by mutableStateOf("Texto")
    var dlgGuardarFormato by mutableStateOf("Automático")
    var dlgGuardarCalidad by mutableIntStateOf(95)

    // Abre el diálogo de opciones para guardar el resultado indicado.
    fun pedirGuardar(r: Resultado?, carpeta: String) {
        if (r == null || r.uri == null) {
            entorno.decir("Primero genera una imagen")
            return
        }
        dlgGuardarResultado = r
        dlgGuardarCarpeta = carpeta
        dlgGuardarFormato = "Automático"
        dlgGuardarCalidad = 95
        dlgGuardarVisible = true
    }

    fun cancelarGuardar() {
        dlgGuardarVisible = false
        dlgGuardarResultado = null
    }

    fun confirmarGuardar() {
        val r = dlgGuardarResultado
        val carpeta = dlgGuardarCarpeta
        val formato = dlgGuardarFormato
        val calidad = dlgGuardarCalidad
        dlgGuardarVisible = false
        dlgGuardarResultado = null
        guardarResultadoEnGaleria(r, carpeta, formato, calidad)
    }

    // Guarda el resultado que se le pasa, en la carpeta de galería indicada,
    // con el formato elegido (Automático = PNG si tiene transparencia, JPG si no).
    fun guardarResultadoEnGaleria(r: Resultado?, carpeta: String, formato: String = "Automático", calidad: Int = 95) {
        if (r == null || r.uri == null) {
            entorno.decir("Primero genera una imagen")
            return
        }
        val uri = r.uri
        val png = when (formato) {
            "PNG" -> true
            "JPG" -> false
            else -> r.png
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (png == r.png) {
                    // Mismo formato que el temporal: se copian los bytes sin recomprimir.
                    Almacen.guardarEnGaleria(getApplication(), uri, carpeta, "imagen_taller", png)
                } else {
                    // Otro formato: se vuelve a codificar desde el temporal.
                    Almacen.guardarBitmapEnGaleria(getApplication(), uri, carpeta, "imagen_taller", png, calidad)
                }
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

    // --- LÓGICA DE ACTUALIZACIÓN ---
    var descargando by mutableStateOf(false)
        private set
    var progresoDescarga by mutableStateOf(0f)
        private set
    // Hay una versión nueva esperando confirmación (null = no hay nada pendiente).
    var actualizacionPendiente by mutableStateOf<Actualizacion?>(null)
        private set

    fun versionInstalada(): String {
        val ctx = getApplication<Application>()
        return try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            }
            info.versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }

    // Compara versiones tipo "1.2" o "v1.10": true solo si la disponible es mayor.
    private fun esMasNueva(disponible: String, instalada: String): Boolean {
        fun partes(v: String) = v.trim().trimStart('v', 'V').split(".", "-", "_")
            .map { it.toIntOrNull() ?: 0 }
        val d = partes(disponible)
        val i = partes(instalada)
        for (k in 0 until maxOf(d.size, i.size)) {
            val a = d.getOrElse(k) { 0 }
            val b = i.getOrElse(k) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    // Si silencioso es true, solo avisa cuando hay una versión nueva:
    // si no hay nada nuevo, no hay internet o falla la consulta, no dice nada.
    // Sirve para la revisión automática al abrir la aplicación.
    fun buscarActualizacion(silencioso: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = URL("https://api.github.com/repos/robertino21/TallerAccesibleAndroid/releases/latest")
                val conexion = url.openConnection() as HttpURLConnection
                conexion.requestMethod = "GET"
                conexion.setRequestProperty("Accept", "application/vnd.github.v3+json")
                conexion.setRequestProperty("User-Agent", "TallerAccesible-Android")
                conexion.connectTimeout = 10000
                conexion.readTimeout = 10000

                if (conexion.responseCode == 200) {
                    val respuesta = conexion.inputStream.bufferedReader().readText()
                    val json = JSONObject(respuesta)
                    val tagVersion = json.getString("tag_name")
                    val assets = json.getJSONArray("assets")
                    // Las novedades de la versión, para mostrarlas en el aviso.
                    val notas = json.optString("body", "").trim().take(600)

                    // El APK es el archivo .apk, no simplemente el primer archivo.
                    var urlDescarga: String? = null
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        if (asset.getString("name").endsWith(".apk", ignoreCase = true)) {
                            urlDescarga = asset.getString("browser_download_url")
                            break
                        }
                    }
                    val instalada = versionInstalada()
                    withContext(Dispatchers.Main) {
                        when {
                            urlDescarga == null -> if (!silencioso) entorno.decir("No hay archivo APK en la última versión.")
                            !esMasNueva(tagVersion, instalada) -> if (!silencioso) entorno.decir("Ya estás al día, versión $instalada.")
                            else -> {
                                actualizacionPendiente = Actualizacion(tagVersion, urlDescarga, notas)
                                entorno.decir("Hay una nueva versión: $tagVersion. Revisa el aviso para descargarla.")
                            }
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        if (!silencioso) entorno.decir("No se pudo verificar la actualización o ya estás al día.")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (!silencioso) entorno.decir("Error: ${e.message ?: "Desconocido"}")
                }
            }
        }
    }

    // Solo se descarga después de que el usuario lo confirma en el aviso.
    fun descargarActualizacion() {
        val pendiente = actualizacionPendiente ?: return
        actualizacionPendiente = null
        iniciarDescargaConProgreso(pendiente.url)
    }

    fun descartarActualizacion() {
        actualizacionPendiente = null
    }

    private fun iniciarDescargaConProgreso(urlApk: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    descargando = true
                    progresoDescarga = 0f
                    entorno.decir("Descargando la actualización...")
                }

                val url = URL(urlApk)
                val conexion = url.openConnection() as HttpURLConnection
                conexion.connectTimeout = 15000
                conexion.readTimeout = 15000
                conexion.connect()

                val longitudArchivo = conexion.contentLength.toLong()
                val archivoDestino = File(getApplication<Application>().cacheDir, "TallerAccesible_Update.apk")

                val buffer = ByteArray(65536)
                var totalLeido: Long = 0
                var ultimoProgresoMostrado = -1

                conexion.inputStream.use { entrada ->
                    FileOutputStream(archivoDestino).use { salida ->
                        var bytesLeidos: Int
                        while (entrada.read(buffer).also { bytesLeidos = it } != -1) {
                            salida.write(buffer, 0, bytesLeidos)
                            totalLeido += bytesLeidos
                            if (longitudArchivo > 0) {
                                val progresoActual = ((totalLeido.toFloat() / longitudArchivo) * 100).toInt()
                                if (progresoActual != ultimoProgresoMostrado) {
                                    ultimoProgresoMostrado = progresoActual
                                    withContext(Dispatchers.Main) {
                                        progresoDescarga = progresoActual / 100f
                                    }
                                }
                            } else {
                                withContext(Dispatchers.Main) { progresoDescarga = -1f }
                            }
                        }
                        salida.flush()
                    }
                }

                withContext(Dispatchers.Main) {
                    descargando = false
                    progresoDescarga = 1f
                    entorno.decir("Descarga completa. Abriendo instalador...")
                    abrirInstalador(archivoDestino)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    descargando = false
                    entorno.decir("Error al descargar: ${e.message}")
                }
            }
        }
    }

    private fun abrirInstalador(archivo: File) {
        val contexto = getApplication<Application>()
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            if (!contexto.packageManager.canRequestPackageInstalls()) {
                val intentAjustes = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${contexto.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                contexto.startActivity(intentAjustes)
                entorno.decir("Activa el permiso para instalar apps y volvé a intentar.")
                return
            }
        }

        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                contexto,
                "${contexto.packageName}.fileprovider",
                archivo
            )
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) 
            }
            
            contexto.startActivity(intent)
            
        } catch (e: Exception) {
            entorno.decir("Error al abrir el instalador: ${e.message}")
            entorno.decir("Activa el permiso para instalar apps desconocidas y volvé a buscar la actualización.")
        }
    }

    // --- BUSCADOR INTELIGENTE DE GOOGLE FONTS ---
    fun buscarFuenteEnGitHub(nombre: String) {
        if (nombre.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    buscandoFuente = true
                    resultadoBusqueda = null
                    nombreFuenteEncontrada = null
                    entorno.decir("Buscando $nombre...")
                }
                
                val busquedaNormalizada = nombre.trim().lowercase().replace(" ", "_")
                
                val urlLista = URL("https://api.github.com/repos/google/fonts/contents/ofl")
                val conexionLista = urlLista.openConnection() as HttpURLConnection
                conexionLista.setRequestProperty("User-Agent", "TallerAccesible-Android")
                conexionLista.connectTimeout = 10000
                
                var carpetaEncontrada = ""
                if (conexionLista.responseCode == 200) {
                    val respuesta = conexionLista.inputStream.bufferedReader().readText()
                    conexionLista.disconnect()
                    val jsonLista = JSONArray(respuesta)
                    
                    for (i in 0 until jsonLista.length()) {
                        val item = jsonLista.getJSONObject(i)
                        val nombreCarpeta = item.getString("name").lowercase()
                        if (nombreCarpeta.contains(busquedaNormalizada)) {
                            carpetaEncontrada = item.getString("name")
                            break
                        }
                    }
                }
                
                if (carpetaEncontrada.isNotEmpty()) {
                    val urlArchivos = URL("https://api.github.com/repos/google/fonts/contents/ofl/$carpetaEncontrada")
                    val conexionArchivos = urlArchivos.openConnection() as HttpURLConnection
                    conexionArchivos.setRequestProperty("User-Agent", "TallerAccesible-Android")
                    conexionArchivos.connectTimeout = 10000
                    
                    if (conexionArchivos.responseCode == 200) {
                        val respuestaArchivos = conexionArchivos.inputStream.bufferedReader().readText()
                        conexionArchivos.disconnect()
                        val jsonArchivos = JSONArray(respuestaArchivos)
                        
                        var urlDescarga = ""
                        for (i in 0 until jsonArchivos.length()) {
                            val archivo = jsonArchivos.getJSONObject(i)
                            val nombreArchivo = archivo.getString("name")
                            if (nombreArchivo.endsWith(".ttf") || nombreArchivo.endsWith(".otf")) {
                                urlDescarga = archivo.getString("download_url")
                                break
                            }
                        }
                        
                        if (urlDescarga.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                resultadoBusqueda = urlDescarga
                                nombreFuenteEncontrada = carpetaEncontrada
                                entorno.decir("Encontrada: $carpetaEncontrada. Tocá descargar.")
                            }
                        } else {
                            withContext(Dispatchers.Main) { entorno.decir("No se encontró archivo de fuente en esa carpeta.") }
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) { entorno.decir("No se encontró '$nombre' en Google Fonts.") }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { entorno.decir("Error al buscar: ${e.message}") }
            } finally {
                withContext(Dispatchers.Main) { buscandoFuente = false }
            }
        }
    }

    fun descargarFuenteDeUrl(urlDescarga: String, nombreFuente: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = URL(urlDescarga)
                val conexion = url.openConnection() as HttpURLConnection
                conexion.setRequestProperty("User-Agent", "TallerAccesible-Android")
                conexion.connectTimeout = 10000
                conexion.readTimeout = 15000
                conexion.connect()

                // La extensión es la del archivo descargado (.ttf o .otf), no siempre .ttf.
                val extension = urlDescarga.substringAfterLast('.', "").lowercase()
                    .takeIf { it == "ttf" || it == "otf" } ?: "ttf"
                val archivoDestino = File(getApplication<Application>().filesDir, "fuentes/$nombreFuente.$extension")
                archivoDestino.parentFile?.mkdirs()
                
                conexion.inputStream.use { input ->
                    archivoDestino.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                
                withContext(Dispatchers.Main) {
                    recargarFuentes("Fuente $nombreFuente descargada")
                    resultadoBusqueda = null
                    nombreFuenteEncontrada = null
                    entorno.decir("Fuente $nombreFuente instalada")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { entorno.decir("Error al descargar: ${e.message}") }
            }
        }
    }
}
// ============================================================
// HERRAMIENTAS: Cortador, Normalizar y Collage
// (antes solo existía el render; ahora tienen pantalla y estado propio)
// ============================================================
class CortadorState {
    var imagenUri by mutableStateOf<Uri?>(null)
    var imagenNombre by mutableStateOf("")
    var recIzq by mutableIntStateOf(0)
    var recDer by mutableIntStateOf(0)
    var recArr by mutableIntStateOf(0)
    var recAba by mutableIntStateOf(0)
    var resultado by mutableStateOf<Resultado?>(null)
    var ocupado by mutableStateOf(false)

    fun generar(env: Entorno) {
        val uri = imagenUri
        if (uri == null) { env.decir("Primero elige una imagen"); return }
        if (ocupado) return
        val izq = recIzq; val der = recDer; val arr = recArr; val aba = recAba
        env.ejecutar({ ocupado = it }, "Error al recortar la imagen") {
            val r = withContext(Dispatchers.Default) {
                val bmp = Imagenes.cargar(env.app, uri).bitmap
                val x = (bmp.width * izq / 100f).toInt()
                val y = (bmp.height * arr / 100f).toInt()
                val w = bmp.width - x - (bmp.width * der / 100f).toInt()
                val h = bmp.height - y - (bmp.height * aba / 100f).toInt()
                if (w <= 0 || h <= 0) throw ErrorUsuario("El recorte deja la imagen vacía, reduce los porcentajes")
                val rec = Bitmap.createBitmap(bmp, x, y, w, h)
                val uriOut = Almacen.guardarTemporal(env.app, rec, false)
                Resultado(Imagenes.vista(rec), uriOut, "Imagen recortada de $w por $h píxeles", png = false)
            }
            resultado = r
            env.decir("Recorte listo. Puedes compartirlo o guardarlo.")
        }
    }

    fun limpiar() {
        imagenUri = null; imagenNombre = ""
        recIzq = 0; recDer = 0; recArr = 0; recAba = 0
        resultado = null
    }
}

class NormalizarState {
    var imagenUri by mutableStateOf<Uri?>(null)
    var imagenNombre by mutableStateOf("")
    var modo by mutableStateOf(MODOS_NORMALIZAR[0])
    var resultado by mutableStateOf<Resultado?>(null)
    var ocupado by mutableStateOf(false)

    fun generar(env: Entorno) {
        val uri = imagenUri
        if (uri == null) { env.decir("Primero elige una imagen"); return }
        if (ocupado) return
        val m = modo
        env.ejecutar({ ocupado = it }, "Error al normalizar la imagen") {
            val r = withContext(Dispatchers.Default) {
                val bmp = Imagenes.cargar(env.app, uri).bitmap
                val out = when (m) {
                    "Cuadrado 1080" -> Render.cuadradoCentro(bmp, 1080)
                    "Cuadrado 800" -> Render.cuadradoCentro(bmp, 800)
                    else -> Render.proporcion(bmp, 1080)
                }
                val uriOut = Almacen.guardarTemporal(env.app, out, false)
                Resultado(Imagenes.vista(out), uriOut, "Imagen normalizada de ${out.width} por ${out.height} píxeles", png = false)
            }
            resultado = r
            env.decir("Imagen normalizada lista. Puedes compartirla o guardarla.")
        }
    }

    fun limpiar() {
        imagenUri = null; imagenNombre = ""; modo = MODOS_NORMALIZAR[0]
        resultado = null
    }
}

class CollageState {
    val imagenes = mutableStateListOf<Uri>()
    val nombres = mutableStateListOf<String>()
    var tipo by mutableStateOf(TIPOS_COLLAGE[0])
    var resultado by mutableStateOf<Resultado?>(null)
    var ocupado by mutableStateOf(false)

    fun agregar(uri: Uri, nombre: String) {
        if (imagenes.size >= 4) return
        imagenes.add(uri)
        nombres.add(nombre)
    }

    fun quitarUltima() {
        if (imagenes.isNotEmpty()) {
            imagenes.removeAt(imagenes.lastIndex)
            nombres.removeAt(nombres.lastIndex)
        }
    }

    fun generar(env: Entorno) {
        if (imagenes.isEmpty()) { env.decir("Primero elige al menos una imagen"); return }
        if (ocupado) return
        val lista = imagenes.toList()
        val t = tipo
        env.ejecutar({ ocupado = it }, "Error al crear el collage") {
            val r = withContext(Dispatchers.Default) {
                val cuadrados = lista.map { uri ->
                    Render.cuadradoCentro(Imagenes.cargar(env.app, uri).bitmap, 600)
                }
                val out = Render.collage(cuadrados, t)
                val uriOut = Almacen.guardarTemporal(env.app, out, false)
                Resultado(Imagenes.vista(out), uriOut, "Collage de ${out.width} por ${out.height} píxeles", png = false)
            }
            resultado = r
            env.decir("Collage listo. Puedes compartirlo o guardarlo.")
        }
    }

    fun limpiar() {
        imagenes.clear(); nombres.clear(); tipo = TIPOS_COLLAGE[0]
        resultado = null
    }
}

class HerramientasState {
    val cortador = CortadorState()
    val normalizar = NormalizarState()
    val collage = CollageState()

    fun limpiar(env: Entorno) {
        cortador.limpiar()
        normalizar.limpiar()
        collage.limpiar()
        env.decir("Herramientas limpias")
    }
}
