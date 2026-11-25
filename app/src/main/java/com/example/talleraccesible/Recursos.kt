package com.example.talleraccesible

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Typeface
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/** Error con un mensaje pensado para leerse tal cual al usuario. */
class ErrorUsuario(mensaje: String) : Exception(mensaje)

/** Imagen terminada, antes de guardarla. */
class Salida(val bitmap: Bitmap, val png: Boolean, val avisos: List<String> = emptyList())

/** Imagen ya guardada: miniatura para pantalla + URI + descripción para lectores de pantalla. */
class Resultado(val vista: Bitmap, val uri: Uri?, val descripcion: String, val avisos: List<String> = emptyList(), val png: Boolean = false)

// ============================================================
// FUENTES
// ============================================================
object Fuentes {
    private var origen: Map<String, () -> Typeface> = emptyMap()
    private val cache = HashMap<String, Typeface>()

    private fun esFuente(nombre: String) =
        nombre.endsWith(".ttf", true) || nombre.endsWith(".otf", true)

    private fun carpetaUsuario(ctx: Context) = File(ctx.filesDir, "fuentes").apply { mkdirs() }

    fun listar(ctx: Context): List<String> {
        val m = LinkedHashMap<String, () -> Typeface>()
        m[FUENTE_DEF] = { Typeface.DEFAULT }
        runCatching {
            ctx.assets.list("fuentes")?.filter { esFuente(it) }?.sorted()?.forEach { f ->
                val n = f.substringBeforeLast('.')
                if (n !in m) m[n] = { Typeface.createFromAsset(ctx.assets, "fuentes/$f") }
            }
        }
        carpetaUsuario(ctx).listFiles()?.filter { it.isFile && esFuente(it.name) }
            ?.sortedBy { it.name }?.forEach { f ->
                val n = f.nameWithoutExtension
                if (n !in m) m[n] = { Typeface.createFromFile(f) }
            }
        File("/system/fonts").listFiles()?.filter { it.isFile && esFuente(it.name) }
            ?.sortedBy { it.name }?.forEach { f ->
                val n = f.nameWithoutExtension
                if (n !in m) m[n] = { Typeface.createFromFile(f) }
            }
        origen = m
        cache.clear()
        return m.keys.toList()
    }

    fun typeface(nombre: String): Typeface = cache.getOrPut(nombre) {
        runCatching { origen[nombre]?.invoke() }.getOrNull() ?: Typeface.DEFAULT
    }

    fun importar(ctx: Context, uri: Uri): String? {
        val nombre = Imagenes.nombre(ctx, uri)
        if (!esFuente(nombre)) return null
        val destino = File(carpetaUsuario(ctx), nombre)
        ctx.contentResolver.openInputStream(uri)?.use { entrada ->
            destino.outputStream().use { salida -> entrada.copyTo(salida) }
        } ?: return null
        return destino.nameWithoutExtension
    }
}

// ============================================================
// IMÁGENES
// ============================================================
object Imagenes {
    class Cargada(val bitmap: Bitmap, val muestreo: Int)

    fun cargar(ctx: Context, uri: Uri, maxLado: Int = 4096): Cargada {
        var muestreo = 1
        val src = ImageDecoder.createSource(ctx.contentResolver, uri)
        val bmp = ImageDecoder.decodeBitmap(src) { dec, info, _ ->
            val mayor = max(info.size.width, info.size.height)
            while (mayor / muestreo > maxLado) muestreo *= 2
            dec.setTargetSampleSize(muestreo)
            dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        return Cargada(bmp, muestreo)
    }

    fun nombre(ctx: Context, uri: Uri): String {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val n = c.getString(0)
                if (!n.isNullOrEmpty()) return n
            }
        }
        return uri.lastPathSegment ?: "archivo"
    }

    fun vista(b: Bitmap, limite: Int = 1024): Bitmap {
        val mayor = max(b.width, b.height)
        if (mayor <= limite) return b
        val e = limite.toFloat() / mayor
        return Bitmap.createScaledBitmap(
            b, (b.width * e).roundToInt().coerceAtLeast(1), (b.height * e).roundToInt().coerceAtLeast(1), true,
        )
    }
}

// ============================================================
// ALMACENAMIENTO (Galería)
// ============================================================
object Almacen {
    private fun sello() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    fun copiar(ctx: Context, uri: Uri): Boolean = runCatching {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newUri(ctx.contentResolver, "Imagen", uri))
        true
    }.getOrDefault(false)

    fun compartir(ctx: Context, uri: Uri) {
        val envio = Intent(Intent.ACTION_SEND).apply {
            type = ctx.contentResolver.getType(uri) ?: "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(
            Intent.createChooser(envio, "Compartir imagen").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    // Guarda la imagen tal cual está en la URI temporal, sin volver a comprimirla:
    // así no se pierde calidad ni transparencia. La carpeta y el formato salen
    // de la imagen misma (PNG si el fondo es transparente, JPG si no).
    fun guardarEnGaleria(ctx: Context, uri: Uri, carpeta: String, base: String, png: Boolean): Uri {
        val ext = if (png) "png" else "jpg"
        val valores = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "${base}_${sello()}.$ext")
            put(MediaStore.Images.Media.MIME_TYPE, if (png) "image/png" else "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/TallerAccesible/$carpeta")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = ctx.contentResolver
        val destino = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), valores)
            ?: throw IOException("No se pudo crear el archivo")
        try {
            resolver.openInputStream(uri)?.use { entrada ->
                resolver.openOutputStream(destino)?.use { salida ->
                    entrada.copyTo(salida)
                } ?: throw IOException("No se pudo abrir el archivo")
            } ?: throw IOException("No se pudo leer la imagen")
        } catch (e: Exception) {
            resolver.delete(destino, null, null)
            throw e
        }
        resolver.update(destino, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        return destino
    }

    // Vista previa temporal: PNG si la imagen tiene transparencia, JPG si no.
    fun guardarTemporal(ctx: Context, bmp: Bitmap, png: Boolean): Uri {
        val archivo = File(ctx.cacheDir, if (png) "preview_taller.png" else "preview_taller.jpg")
        archivo.outputStream().use { salida ->
            bmp.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 95, salida)
        }
        return androidx.core.content.FileProvider.getUriForFile(
            ctx,
            "${ctx.packageName}.fileprovider",
            archivo
        )
    }
}