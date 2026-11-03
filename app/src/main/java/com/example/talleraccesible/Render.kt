package com.example.talleraccesible

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.max
import kotlin.math.min

/**
 * Port de la lógica de Pillow a android.graphics.
 * Todo lo de aquí es pesado: llamarlo siempre desde un hilo de fondo.
 */
object Render {

    // ---------- estilo resuelto (colores ya convertidos, valores por defecto aplicados) ----------
    private class E(
        val tf: Typeface, val tam: Float, val inter: Float,
        val texto: Int, val borde: Int?, val grosor: Float,
        val sombra: Int?, val sx: Float, val sy: Float, val blur: Float,
        val caja: Int?, val pad: Float, val radio: Float,
        val align: Int, // 0 izquierda, 1 centro, 2 derecha
    )

    private fun resolver(e: EstiloUi): E {
        val colBorde = if (e.borde) colorDe(e.colorBorde) else null
        return E(
            tf = Fuentes.typeface(e.fuente),
            tam = (if (e.tam <= 0) 40 else e.tam).toFloat(),
            inter = e.inter.toFloat(),
            texto = colorDe(e.colorTexto) ?: Color.WHITE,
            borde = colBorde,
            grosor = if (colBorde != null) e.grosor.toFloat() else 0f,
            sombra = if (e.sombra) colorDe(e.colorSombra) else null,
            sx = e.sx.toFloat(), sy = e.sy.toFloat(), blur = e.blur.toFloat(),
            caja = if (e.caja) colorDe(e.colorCaja) else null,
            pad = e.padding.toFloat(), radio = e.radio.toFloat(),
            align = when (e.align) { "Izquierda" -> 0; "Derecha" -> 2; else -> 1 },
        )
    }

    // ---------- medición y dibujo de bloques de texto ----------
    private class Bloque(
        val lineas: List<String>, val anchos: List<Float>, val anchoMax: Float,
        val lh: Float, val ascent: Float, val alto: Float,
    )

    private fun paintTexto(e: E, tam: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = e.tf
        textSize = tam
        strokeJoin = Paint.Join.ROUND
    }

    /** Ajuste de línea por palabras, igual que el original (máximo = 85 % del ancho). */
    private fun medir(texto: String, e: E, tam: Float, maxW: Float): Bloque {
        val p = paintTexto(e, tam)
        val extra = 2f * e.grosor
        val lineas = ArrayList<String>()
        for (parrafo in texto.split("\n")) {
            if (parrafo.isEmpty()) { lineas.add(""); continue }
            var actual = ""
            for (pal in parrafo.split(" ")) {
                val prueba = if (actual.isEmpty()) pal else "$actual $pal"
                if (p.measureText(prueba) + extra <= maxW) {
                    actual = prueba
                } else {
                    if (actual.isNotEmpty()) lineas.add(actual)
                    actual = pal
                }
            }
            if (actual.isNotEmpty()) lineas.add(actual)
        }
        val anchos = lineas.map { if (it.isEmpty()) 0f else p.measureText(it) + extra }
        val fm = p.fontMetrics
        val lh = fm.descent - fm.ascent
        val n = lineas.size
        val alto = if (n == 0) 0f else n * lh + (n - 1) * e.inter
        return Bloque(lineas, anchos, anchos.maxOrNull() ?: 0f, lh, fm.ascent, alto)
    }

    private fun dibujar(c: Canvas, b: Bloque, e: E, tam: Float, left: Float, top: Float) {
        if (b.lineas.isEmpty()) return

        if (e.caja != null) {
            val pc = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = e.caja }
            val r = RectF(left - e.pad, top - e.pad, left + b.anchoMax + e.pad, top + b.alto + e.pad)
            c.drawRoundRect(r, e.radio, e.radio, pc)
        }

        fun xLinea(i: Int): Float = when (e.align) {
            0 -> left
            2 -> left + b.anchoMax - b.anchos[i]
            else -> left + (b.anchoMax - b.anchos[i]) / 2f
        } + e.grosor

        fun yBase(i: Int): Float = top + i * (b.lh + e.inter) - b.ascent

        // Sombra (primero todas las líneas, luego el texto encima)
        if (e.sombra != null) {
            val ps = paintTexto(e, tam).apply {
                color = e.sombra
                if (e.grosor > 0f) { style = Paint.Style.FILL_AND_STROKE; strokeWidth = 2f * e.grosor }
                if (e.blur > 0f) maskFilter = BlurMaskFilter(max(1f, e.blur), BlurMaskFilter.Blur.NORMAL)
            }
            b.lineas.forEachIndexed { i, l ->
                if (l.isNotEmpty()) c.drawText(l, xLinea(i) + e.sx, yBase(i) + e.sy, ps)
            }
        }

        // Borde
        if (e.borde != null && e.grosor > 0f) {
            val pb = paintTexto(e, tam).apply {
                color = e.borde
                style = Paint.Style.FILL_AND_STROKE
                strokeWidth = 2f * e.grosor
            }
            b.lineas.forEachIndexed { i, l -> if (l.isNotEmpty()) c.drawText(l, xLinea(i), yBase(i), pb) }
        }

        // Relleno
        val pt = paintTexto(e, tam).apply { color = e.texto }
        b.lineas.forEachIndexed { i, l -> if (l.isNotEmpty()) c.drawText(l, xLinea(i), yBase(i), pt) }
    }

    // ---------- fondo ----------
    private class Fondo(val bitmap: Bitmap, val transparente: Boolean)

    private fun fondo(ctx: Context, l: LienzoSpec): Fondo {
        val bmp = Bitmap.createBitmap(l.ancho, l.alto, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val c1 = colorDe(l.color1)
        val c2 = colorDe(l.color2)
        var transparente = false
        when {
            l.tipo == "Imagen" -> {
                val uri = l.fondo ?: throw ErrorUsuario("Elige primero la imagen de fondo")
                val img = Imagenes.cargar(ctx, uri).bitmap
                c.drawBitmap(img, null, RectF(0f, 0f, l.ancho.toFloat(), l.alto.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
            }
            l.tipo.contains("Degradado") -> {
                if (c1 == null || c2 == null) throw ErrorUsuario("Elige los dos colores del degradado")
                val vertical = l.tipo.contains("vertical")
                val p = Paint().apply {
                    shader = LinearGradient(
                        0f, 0f,
                        if (vertical) 0f else l.ancho.toFloat(),
                        if (vertical) l.alto.toFloat() else 0f,
                        c1, c2, Shader.TileMode.CLAMP,
                    )
                }
                c.drawRect(0f, 0f, l.ancho.toFloat(), l.alto.toFloat(), p)
            }
            else -> {
                val col = c1 ?: Color.WHITE
                bmp.eraseColor(col)
                transparente = Color.alpha(col) == 0
            }
        }
        return Fondo(bmp, transparente)
    }

    // ============================================================
    // MODO SIMPLE
    // ============================================================
    fun simple(
        ctx: Context, texto: String, l: LienzoSpec, estilo: EstiloUi,
        pos: String, yPct: Int, auto: Boolean,
    ): Salida {
        if (estilo.caja && colorDe(estilo.colorCaja) == null) throw ErrorUsuario("Elige el color de la caja")
        val f = fondo(ctx, l)
        val c = Canvas(f.bitmap)
        val e = resolver(estilo)
        val w = l.ancho.toFloat()
        val h = l.alto.toFloat()

        var tam = e.tam
        var b = medir(texto, e, tam, w * 0.85f)
        if (auto) {
            while (b.alto > h * 0.9f && tam > 10f) {
                tam = max(10f, tam - 2f)
                b = medir(texto, e, tam, w * 0.85f)
            }
        }
        val margen = w * 0.075f
        val left = when (e.align) {
            0 -> margen
            2 -> w - b.anchoMax - margen
            else -> (w - b.anchoMax) / 2f
        }
        val top = when {
            pos == "Arriba" -> h * 0.05f
            pos == "Abajo" -> h - b.alto - h * 0.05f
            pos.startsWith("Personalizada") -> h * yPct / 100f - b.alto / 2f
            else -> (h - b.alto) / 2f
        }
        dibujar(c, b, e, tam, left, top)
        return Salida(f.bitmap, png = f.transparente)
    }

    // ============================================================
    // MODO CAPAS
    // ============================================================
    fun capas(ctx: Context, l: LienzoSpec, capas: List<Capa>): Salida {
        val f = fondo(ctx, l)
        val c = Canvas(f.bitmap)
        val w = l.ancho.toFloat()
        val h = l.alto.toFloat()
        val avisos = ArrayList<String>()

        for (capa in capas) {
            when (capa) {
                is CapaImagen -> {
                    try {
                        val carg = Imagenes.cargar(ctx, capa.uri)
                        val esc = capa.escala / 100f
                        val aw = carg.bitmap.width * carg.muestreo * esc
                        val ah = carg.bitmap.height * carg.muestreo * esc
                        val cx = w * capa.xPct / 100f
                        val cy = h * capa.yPct / 100f
                        c.drawBitmap(
                            carg.bitmap, null,
                            RectF(cx - aw / 2f, cy - ah / 2f, cx + aw / 2f, cy + ah / 2f),
                            Paint(Paint.FILTER_BITMAP_FLAG),
                        )
                    } catch (ex: Exception) {
                        avisos.add("No se pudo cargar la capa ${capa.nombre}")
                    }
                }
                is CapaTexto -> {
                    if (capa.contenido.isBlank()) {
                        avisos.add("La capa ${capa.nombre} no tiene texto")
                    } else {
                        val e = resolver(capa.estilo)
                        val b = medir(capa.contenido, e, e.tam, w * 0.85f)
                        val left = w * capa.xPct / 100f - b.anchoMax / 2f
                        val top = h * capa.yPct / 100f - b.alto / 2f
                        dibujar(c, b, e, e.tam, left, top)
                    }
                }
            }
        }
        return Salida(f.bitmap, png = true, avisos = avisos)
    }

    // ============================================================
    // CORTADOR / NORMALIZADOR / COLLAGE
    // ============================================================
    fun cuadradoCentro(src: Bitmap, tam: Int): Bitmap {
        val lado = min(src.width, src.height)
        val x = (src.width - lado) / 2
        val y = (src.height - lado) / 2
        val recorte = Bitmap.createBitmap(src, x, y, lado, lado)
        return Bitmap.createScaledBitmap(recorte, tam, tam, true)
    }

    /** Equivale a thumbnail() de Pillow: solo reduce, nunca amplía; centra sobre blanco. */
    fun proporcion(src: Bitmap, tam: Int): Bitmap {
        val out = Bitmap.createBitmap(tam, tam, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.WHITE)
        val esc = min(1f, min(tam / src.width.toFloat(), tam / src.height.toFloat()))
        val w = src.width * esc
        val h = src.height * esc
        val x = (tam - w) / 2f
        val y = (tam - h) / 2f
        c.drawBitmap(src, null, RectF(x, y, x + w, y + h), Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    private fun lienzoBlanco(w: Int, h: Int): Bitmap =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { Canvas(it).drawColor(Color.WHITE) }

    /** [imgs] ya vienen como cuadrados de 600 px. Máximo 4, como en el original. */
    fun collage(imgs: List<Bitmap>, tipo: String): Bitmap {
        val n = imgs.size
        return when (tipo) {
            "Horizontal" -> {
                val out = lienzoBlanco(600 * n, 600)
                val c = Canvas(out)
                imgs.forEachIndexed { i, b -> c.drawBitmap(b, (600 * i).toFloat(), 0f, null) }
                out
            }
            "Vertical" -> {
                val out = lienzoBlanco(600, 600 * n)
                val c = Canvas(out)
                imgs.forEachIndexed { i, b -> c.drawBitmap(b, 0f, (600 * i).toFloat(), null) }
                out
            }
            else -> when (n) {
                1 -> Bitmap.createScaledBitmap(imgs[0], 1200, 1200, true)
                2 -> lienzoBlanco(1200, 600).also {
                    val c = Canvas(it)
                    c.drawBitmap(imgs[0], 0f, 0f, null); c.drawBitmap(imgs[1], 600f, 0f, null)
                }
                3 -> lienzoBlanco(1200, 1200).also {
                    val c = Canvas(it)
                    c.drawBitmap(imgs[0], 0f, 0f, null); c.drawBitmap(imgs[1], 600f, 0f, null)
                    c.drawBitmap(imgs[2], 300f, 600f, null)
                }
                else -> lienzoBlanco(1200, 1200).also {
                    val c = Canvas(it)
                    c.drawBitmap(imgs[0], 0f, 0f, null); c.drawBitmap(imgs[1], 600f, 0f, null)
                    c.drawBitmap(imgs[2], 0f, 600f, null); c.drawBitmap(imgs[3], 600f, 600f, null)
                }
            }
        }
    }
}
