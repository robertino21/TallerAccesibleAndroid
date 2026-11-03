# Taller Accesible para Android

Port de `Main.py` (PySide6 + Pillow) a Android nativo: Kotlin + Jetpack Compose.
Las cuatro pestañas originales están: Cortador, Normalizar, Collage y Texto (modo simple y modo capas).

> **Estado:** el código no se compiló ni se probó en un dispositivo. Hay que compilarlo,
> corregir lo que salga y probarlo con TalkBack (lista al final).

## Cómo compilarlo

**Opción A, sin instalar nada (recomendada si Android Studio te resulta incómodo):**
1. Sube esta carpeta a un repositorio de GitHub.
2. Ve a la pestaña *Actions* y ejecuta el flujo "Compilar APK" (o se lanza solo con cada push).
3. Al terminar, descarga el artefacto `TallerAccesible-debug`, que contiene `app-debug.apk`.
4. Instala el APK en el teléfono (hay que permitir "instalar apps de origen desconocido").

**Opción B, Android Studio:** abrir la carpeta, esperar la sincronización de Gradle y ejecutar.

**Opción C, línea de comandos:** con JDK 17 y el SDK de Android (API 35) instalados:
`gradle assembleDebug` (Gradle 8.9). El APK queda en `app/build/outputs/apk/debug/`.

## Equivalencias de accesibilidad (escritorio → Android)

| Escritorio (Qt)                    | Android                                                                                         |
|------------------------------------|-------------------------------------------------------------------------------------------------|
| `accessible_output2` / `hablar()`  | Snackbar (región *live*): TalkBack lo lee solo, se ve en pantalla y dura más con TalkBack activo |
| `setAccessibleName`                | Etiquetas visibles en cada campo (TalkBack lee la etiqueta) + `contentDescription` en botones ± |
| `QSpinBox`                         | Campo numérico con teclado numérico + botones "Disminuir/Aumentar" + acciones personalizadas de TalkBack. Valida el rango y anuncia el valor nuevo |
| `QComboBox`                        | Botón "Etiqueta, valor actual" que abre un diálogo con lista de botones de radio                  |
| `QCheckBox`                        | Fila completa como un único control (rol casilla de verificación)                                |
| `QListWidget` (capas)              | Grupo de botones de radio: "2 de 3: Título, capa de texto"                                       |
| `QGroupBox` / etiquetas de sección | Encabezados reales (TalkBack permite saltar de encabezado en encabezado)                         |
| `QTabWidget` + Ctrl+1..4           | Barra de navegación inferior (rol pestaña) y Ctrl+1..4 con teclado físico                        |
| Ctrl+Enter en el texto             | Igual, con teclado físico                                                                        |
| Ctrl+G / Ctrl+L                    | Igual, con teclado físico                                                                        |
| Controles deshabilitados           | Se mantienen deshabilitados (TalkBack dice "no disponible")                                      |
| Vista previa (no existía)          | Imagen con descripción que incluye el tamaño y el texto, para que un lector de pantalla "vea" el resultado |
| Copiar al portapapeles             | Copiar imagen (portapapeles de Android) y **Compartir imagen** (nuevo)                           |

## Dónde se guardan los archivos

Galería, en `Imágenes/TallerAccesible/{Texto, Recortadas, Normalizadas, Collages}`, con fecha y hora en el nombre
(el original sobrescribía siempre el mismo archivo). No se piden permisos de almacenamiento.

## Fuentes

Ya no existe `C:\Windows\Fonts`. La lista se arma con: Predeterminada, `app/src/main/assets/fuentes/`
(poné ahí tus .ttf/.otf antes de compilar), fuentes que importes desde la app con **Importar fuente**,
y las fuentes del sistema Android.

## Cambios respecto al original (intencionales)

- **Modo capas**: ahora aplican de verdad borde, sombra, caja, interlineado y alineación
  (en el original esos controles existían pero `generar_imagen_capas` los ignoraba). Los textos largos se ajustan a varias líneas.
- **"Auto" (ajuste automático)**: en el original el checkbox no hacía nada. Ahora reduce el tamaño hasta que el texto entra en el 90 % del alto.
- **Sombra y caja respetan la transparencia** del color elegido ("Negro 50%" ahora sí es 50 %); antes se descartaba el canal alfa.
- **Altura de línea** calculada con las métricas de la fuente, así el interlineado es parejo. La caja tiene el mismo relleno arriba y abajo.
- **Errores que ahora se anuncian** en vez de ignorarse: fondo "Imagen" sin imagen elegida, capa de imagen que no carga, capa de texto vacía, recortes que se superponen.
- **Ctrl+L en el Cortador** limpiaba el collage (error del original); ahora limpia el cortador.
- Las fotos respetan la rotación EXIF (importante con fotos de cámara).
- Cuadrícula/Proporción llevan tilde en los nombres para que el lector de voz los pronuncie bien.

## Qué probar con TalkBack

1. Recorrer cada pestaña con deslizamientos: ¿el orden de lectura es lógico y cada control dice su nombre, su valor y su tipo?
2. Campos numéricos: escribir un valor, usar "Aumentar/Disminuir", y el menú de acciones de TalkBack. ¿Se anuncia el valor nuevo?
3. Abrir y cerrar los diálogos de listas: ¿el foco vuelve al botón correcto?
4. Modo capas: agregar, renombrar, reordenar y eliminar capas; comprobar los anuncios.
5. Generar una imagen: ¿se anuncia el resultado? ¿La descripción de la vista previa es útil?
6. Con teclado Bluetooth: Ctrl+1..4, Ctrl+G, Ctrl+L, Ctrl+Enter, Tab.
7. Rotar el teléfono y cambiar el tamaño de fuente a "Máximo": no debe perderse el trabajo ni quedar nada cortado.
8. Probar también con Voice Access y con el modo de contraste alto.
