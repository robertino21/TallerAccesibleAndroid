package com.example.talleraccesible
import android.graphics.Color
import android.net.Uri

const val SIN = "Sin seleccionar"
const val FUENTE_DEF = "Predeterminada"

data class Preset(val nombre: String, val ancho: Int?, val alto: Int?)

val PRESETS = listOf(
    Preset("Personalizado", null, null),
    Preset("Post cuadrado 1080x1080", 1080, 1080),
    Preset("Post vertical 1080x1350", 1080, 1350),
    Preset("Reel / Story 1080x1920", 1080, 1920),
    Preset("Portada Facebook 1640x924", 1640, 924),
    Preset("Miniatura YouTube 1280x720", 1280, 720),
)

val LISTA_PRESETS = PRESETS.map { it.nombre }

val COLORES: Map<String, Int> = linkedMapOf(
    "Blanco" to Color.rgb(255, 255, 255),
    "Negro" to Color.rgb(0, 0, 0),
    "Negro 50%" to Color.argb(128, 0, 0, 0),
    "Negro 30%" to Color.argb(77, 0, 0, 0),
    "Rojo" to Color.rgb(255, 0, 0),
    "Verde lima" to Color.rgb(0, 255, 0),
    "Verde oscuro" to Color.rgb(0, 128, 0),
    "Azul" to Color.rgb(0, 0, 255),
    "Azul cielo" to Color.rgb(30, 144, 255),
    "Azul marino" to Color.rgb(0, 0, 128),
    "Amarillo" to Color.rgb(255, 255, 0),
    "Naranja" to Color.rgb(255, 165, 0),
    "Morado" to Color.rgb(128, 0, 128),
    "Rosa" to Color.rgb(255, 20, 147),
    "Gris claro" to Color.rgb(200, 200, 200),
    "Gris oscuro" to Color.rgb(80, 80, 80),
    "Turquesa" to Color.rgb(64, 224, 208),
    "Transparente" to Color.argb(0, 0, 0, 0),
)

val LISTA_COLORES = COLORES.keys.toList()
val LISTA_COLORES_SIN = listOf(SIN) + LISTA_COLORES

fun colorDe(nombre: String): Int? = if (nombre == SIN) null else COLORES[nombre]

val MODOS_TEXTO = listOf("Simple", "Capas")
val ALINEACIONES = listOf("Izquierda", "Centro", "Derecha")
val ALINEACIONES_SIN = listOf(SIN) + ALINEACIONES
val POSICIONES = listOf("Arriba", "Centro", "Abajo", "Personalizada %")
val FONDOS = listOf("Color sólido", "Degradado vertical", "Degradado horizontal", "Imagen")
val MODOS_NORMALIZAR = listOf("Cuadrado 1080", "Cuadrado 800", "Proporción 1080")
val TIPOS_COLLAGE = listOf("Cuadrícula", "Horizontal", "Vertical")

data class EstiloUi(
    val fuente: String = FUENTE_DEF,
    val tam: Int = 80,
    val inter: Int = 10,
    val colorTexto: String = "Blanco",
    val borde: Boolean = false,
    val colorBorde: String = SIN,
    val grosor: Int = 2,
    val sombra: Boolean = false,
    val colorSombra: String = SIN,
    val sombraOpacidad: Int = 100,
    val sx: Int = 3,
    val sy: Int = 3,
    val blur: Int = 0,
    val caja: Boolean = false,
    val colorCaja: String = "Blanco",
    val padding: Int = 20,
    val radio: Int = 10,
    val align: String = "Centro",
) {
    companion object {
        fun paraCapa() = EstiloUi(
            fuente = SIN, tam = 0, inter = 0, colorTexto = SIN, grosor = 0,
            sx = 0, sy = 0, blur = 0, colorCaja = SIN, padding = 0, radio = 0, align = SIN,
        )
    }
}

sealed interface Capa {
    val nombre: String
    val xPct: Int
    val yPct: Int
}

data class CapaTexto(
    override val nombre: String,
    val contenido: String = "",
    val estilo: EstiloUi = EstiloUi.paraCapa(),
    override val xPct: Int = 50,
    override val yPct: Int = 50,
) : Capa

data class CapaImagen(
    override val nombre: String,
    val uri: Uri,
    val escala: Int = 100,
    override val xPct: Int = 50,
    override val yPct: Int = 50,
) : Capa

// ACTUALIZADO: Agregamos opacidadFondo
data class LienzoSpec(
    val ancho: Int,
    val alto: Int,
    val tipo: String,
    val color1: String,
    val color2: String,
    val fondo: Uri?,
    val opacidadFondo: Int = 100
)

data class Plantilla(
    val nombre: String,
    val estilo: EstiloUi,
    val lienzoAncho: Int,
    val lienzoAlto: Int,
    val pos: String,
    val yPct: Int
)