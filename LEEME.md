# Taller Accesible para Android

Port de `Main.py` (PySide6 + Pillow) a Android nativo: Kotlin + Jetpack Compose.

> **Estado:** el código compila en GitHub Actions (flujo "Compilar APK") y el APK
> se descarga como artefacto `TallerAccesible-debug`. Falta probarlo en un
> dispositivo con TalkBack (lista al final).

## Cómo compilarlo

**Opción A, sin instalar nada (recomendada si Android Studio te resulta incómodo):**
1. Sube esta carpeta a un repositorio de GitHub.
2. Ve a la pestaña *Actions* y ejecuta el flujo "Compilar APK" (o se lanza solo con cada push).
3. Al terminar, descarga el artefacto `TallerAccesible-debug`, que contiene `app-debug.apk`.
4. Instala el APK en el teléfono (hay que permitir "instalar apps de origen desconocido").

**Opción B, Android Studio:** abrir la carpeta, esperar la sincronización de Gradle y ejecutar.

**Opción C, línea de comandos:** con JDK 17 y el SDK de Android (API 35) instalados:
`gradle assembleDebug` (Gradle 8.9). El APK queda en `app/build/outputs/apk/debug/`.

## Pestañas

Barra inferior con 4 pestañas (Ctrl+1..4 con teclado físico):

1. **Editor** — Texto en modo Simple y modo Capas.
2. **Herramientas** — Cortador, Normalizar y Collage.
3. **Vista previa** — imagen generada, con zoom real (el tamaño cambia de verdad y se puede desplazar), Compartir, Copiar y Guardar.
4. **Ajustes** — tema, actualizaciones y plantillas.

## Equivalencias de accesibilidad (escritorio → Android)

| Escritorio (Qt)                    | Android                                                                                         |
|------------------------------------|-------------------------------------------------------------------------------------------------|
| `accessible_output2` / `hablar()`  | Snackbar (región *live*): TalkBack lo lee solo, se ve en pantalla y dura más con TalkBack activo |
| `setAccessibleName`                | Etiquetas visibles en cada campo (TalkBack lee la etiqueta) + `contentDescription` en botones ± |
| `QSpinBox`                         | Deslizadores con valor anunciado + botones de acción de TalkBack. Valida el rango               |
| `QComboBox`                        | Botón "Etiqueta, valor actual" que abre un diálogo con lista de botones de radio                  |
| `QCheckBox`                        | Fila completa como un único control (rol casilla de verificación)                                |
| `QListWidget` (capas)              | Grupo de botones de radio: "2 de 3: Título, capa de texto" + botones Subir/Bajar; la primera de la lista queda adelante |
| `QGroupBox` / etiquetas de sección | Encabezados reales (TalkBack permite saltar de encabezado en encabezado)                         |
| `QTabWidget` + Ctrl+1..4           | Barra de navegación inferior (rol pestaña) y Ctrl+1..4 con teclado físico                        |
| Ctrl+Enter en el texto             | Igual, con teclado físico (genera la imagen)                                                     |
| Ctrl+G / Ctrl+L                    | Igual, con teclado físico (generar / limpiar; en Herramientas, Ctrl+L limpia la herramienta)    |
| Controles deshabilitados           | Se mantienen deshabilitados (TalkBack dice "no disponible")                                      |
| Vista previa (no existía)          | Imagen con descripción que incluye el tamaño y el texto, para que un lector de pantalla "vea" el resultado |
| Copiar al portapapeles             | Copiar imagen (portapapeles de Android) y **Compartir imagen**                                   |

## Dónde se guardan los archivos

Galería, en `Imágenes/TallerAccesible/{Texto, Recortadas, Normalizadas, Collages}`, con fecha y hora en el nombre
(el original sobrescribía siempre el mismo archivo). No se piden permisos de almacenamiento.
La vista previa es temporal y se guarda en caché (PNG si el fondo es transparente, JPG si no).
Al tocar Guardar se abre un diálogo con las opciones: formato (Automático, PNG o JPG),
calidad JPG (70–100, por defecto 95) y carpeta; con Automático se conserva el formato del
temporal sin volver a comprimir, y al cambiar de formato se vuelve a codificar (JPG sobre blanco).

## Fuentes

Ya no existe `C:\Windows\Fonts`. La lista se arma con: Predeterminada, `app/src/main/assets/fuentes/`
(poné ahí tus .ttf/.otf antes de compilar), fuentes que importes desde la app con **Importar fuente**,
y las fuentes del sistema Android. El buscador descarga de Google Fonts y guarda cada fuente
con su extensión real (.ttf u .otf).

## Plantillas

Se guarda el modo actual completo (Simple o Capas, con toda la lista de capas) y el lienzo.
Si ya existe una plantilla con ese nombre, se actualiza en vez de duplicarse.
Las imágenes de las capas se guardan por su URI con permiso persistente, así la plantilla
sigue funcionando después de cerrar la app. El fondo "Imagen" no se guarda en la plantilla,
igual que en el original de escritorio.

## Actualizaciones

"Buscar actualizaciones" compara la versión publicada con la instalada y solo avisa si hay
una más nueva; nada se descarga sin confirmación. La descarga toma el archivo .apk de la
versión y abre el instalador. Los builds de depuración se firman siempre con el mismo
`debug.keystore` del repo para que la actualización no falle por cambio de firma.

## Cambios respecto al original (intencionales)

- **Modo capas**: ahora aplican de verdad borde, sombra, caja, interlineado y alineación
  (en el original esos controles existían pero `generar_imagen_capas` los ignoraba). Los textos largos se ajustan a varias líneas.
- **"Auto" (ajuste automático)**: en el original el checkbox no hacía nada. Ahora reduce el tamaño hasta que el texto entra en el 90 % del alto. Si aun así no entra, avisa.
- **Sombra y caja respetan la transparencia** del color elegido ("Negro 50%" ahora sí es 50 %); antes se descartaba el canal alfa.
- **Opacidad del fondo**: el control ahora funciona de verdad para fondo de color, degradado e imagen.
- **Altura de línea** calculada con las métricas de la fuente, así el interlineado es parejo. La caja tiene el mismo relleno arriba y abajo.
- **Errores que ahora se anuncian** en vez de ignorarse: fondo "Imagen" sin imagen elegida, capa de imagen que no carga, capa de texto vacía, recortes que se superponen.
- **Ctrl+L en el Cortador** limpiaba el collage (error del original); ahora limpia el cortador.
- Las fotos respetan la rotación EXIF (importante con fotos de cámara).
- Cuadrícula/Proporción llevan tilde en los nombres para que el lector de voz los pronuncie bien.

## Qué probar con TalkBack

1. Recorrer cada pestaña con deslizamientos: ¿el orden de lectura es lógico y cada control dice su nombre, su valor y su tipo?
2. Deslizadores: cambiar un valor y comprobar que TalkBack anuncia el valor nuevo.
3. Abrir y cerrar los diálogos de listas: ¿el foco vuelve al botón correcto?
4. Modo capas: agregar, renombrar, reordenar (Subir/Bajar) y eliminar capas; comprobar los anuncios.
5. Generar una imagen: ¿se anuncia el resultado? ¿La descripción de la vista previa es útil?
6. Herramientas: recortar, normalizar y armar un collage de 4 fotos; guardar cada resultado en la galería.
7. Con teclado Bluetooth: Ctrl+1..4, Ctrl+G, Ctrl+L, Ctrl+Enter, Tab.
8. Rotar el teléfono y cambiar el tamaño de fuente a "Máximo": no debe perderse el trabajo ni quedar nada cortado.
9. Probar también con Voice Access y con el modo de contraste alto.
