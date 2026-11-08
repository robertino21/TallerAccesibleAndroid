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
class Resultado(val vista: Bitmap, val uri: Uri?, val descripcion: String, val avisos: List<String> = emptyList())

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
    fun guardar(ctx: Context, bmp: Bitmap, carpeta: String, base: String, png: Boolean): Uri {
        val sello = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val ext = if (png) "png" else "jpg"
        val valores = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "${base}_$sello.$ext")
            put(MediaStore.Images.Media.MIME_TYPE, if (png) "image/png" else "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/TallerAccesible/$carpeta")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = ctx.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), valores)
            ?: throw IOException("No se pudo crear el archivo")
        try {
            val salida = resolver.openOutputStream(uri) ?: throw IOException("No se pudo abrir el archivo")
            salida.use {
                val formato = if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                if (!bmp.compress(formato, 95, it)) throw IOException("No se pudo escribir la imagen")
            }
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        return uri
    }

    fun publicar(ctx: Context, s: Salida, carpeta: String, base: String, detalle: String): Resultado {
        val uri = guardar(ctx, s.bitmap, carpeta, base, s.png)
        val desc = "Imagen de ${s.bitmap.width} por ${s.bitmap.height} píxeles. $detalle"
        return Resultado(Imagenes.vista(s.bitmap), uri, desc, s.avisos)
    }

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

    // Recuperado para el botón Guardar
    fun guardarEnGaleria(ctx: Context, uri: Uri, carpeta: String, base: String): Uri {
        val bmp = MediaStore.Images.Media.getBitmap(ctx.contentResolver, uri)
        return guardar(ctx, bmp, carpeta, base, false)
    }

    // Recuperado para guardar imágenes temporales
    fun guardarTemporal(ctx: Context, bmp: Bitmap): Uri {
        val archivo = File(ctx.cacheDir, "preview_taller.jpg")
        archivo.outputStream().use { salida ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 95, salida)
        }
        return androidx.core.content.FileProvider.getUriForFile(
            ctx,
            "${ctx.packageName}.fileprovider",
            archivo
        )
    }
}