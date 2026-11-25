# Grabadora (grabadora de sonido MCP): reglas para agentes

- Lee `ARQUITECTURA.md` (y el contrato común `FAMILIA.md` del repo de Inicio) y abre solo los archivos que necesites.
- Pruebas: `cd android && ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease` (debe pasar todo).
  Nunca desactives ni saltes una prueba. Lint sin avisos nuevos. Mira las capturas en `android/app/build/screenshots`.
- Cada versión: sube `versionCode` y `versionName`, copia el APK a `android/Grabadora-<versión>.apk` (borra el anterior)
  y anótalo en `CHANGELOG.md`.
- Reglas puras (nombres de archivo, reloj de grabación, formato de duración y tamaño, ondas, marcas, búsqueda, selección,
  ajustes) en `data/`, sin Android y con pruebas. Las pantallas solo pintan y avisan. Todo lo de MediaStore vive en
  `Library`, la grabación en `RecorderService` y la reproducción en `Playback`.
- La IA **nunca** graba: no existe ni existirá una herramienta MCP para empezar a grabar. Solo la persona graba, tocando el botón rojo. Tampoco hay `rec_delete`: borrar solo se hace en el teléfono (Android 11+
  lo manda a su papelera). Por MCP salen nombres, fechas, duraciones, pesos y marcas; nunca rutas (`uri`). El audio sale
  solo por `rec_audio` (token de lectura y escritura, por partes) y solo con «Dejar que la IA escuche las grabaciones»
  encendido en el teléfono (apagado de fábrica), para que un modelo lo transcriba.
- Permisos: micrófono (con explicación antes), notificaciones (Android 13+) y, solo para «Grabaciones anteriores», leer
  audio (`READ_MEDIA_AUDIO` / `READ_EXTERNAL_STORAGE` ≤ 32). Nunca pidas nada más.
- Privacidad: las grabaciones se quedan en el teléfono (Música/Grabadora); ajustes, marcas y ondas fuera de copias. Nada
  sale salvo por el Modo MCP que el usuario active; internet es solo para eso.
- Cada herramienta MCP nueva lleva su prueba en `McpTest`: lista de herramientas, llamada correcta, argumentos malos y
  «no encontrado». Las que cambian algo exigen token de lectura y escritura (`READ_ONLY` si no).
- Prohibido: `eval`, motores de scripts, SQL concatenado (MediaStore siempre con `?`).
- Firma: la de depuración del entorno, igual que el resto de la familia y Gateway Hub.
