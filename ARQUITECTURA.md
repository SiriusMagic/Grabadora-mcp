# Arquitectura de Grabadora

Carpeta = package (`android/app/src/main/java/com/zirakusa/grabadora/`):

| Carpeta | Qué hay |
|---|---|
| `data/` | Lógica pura, con pruebas. `Recording` y `Names` (nombre «yyyyMMdd_HHmmss», nombres seguros, `.m4a`, sin repetidos «(2)»); `Clock` («00:00:03», «1:02:03», «44,77 KB») y `Run` (reloj de grabación que no cuenta las pausas); `Waveform` (nivel desde `getMaxAmplitude`, 20 por segundo, reducir a barras, normalizar, guardar en bytes) y `PeakMeter` (niveles desde muestras PCM); `Markers` (marcas ordenadas, sin dos en el mismo segundo, máx. 99), `Search` (sin acentos, más nuevas primero) y `Selection` (modo selección: qué permite la barra); `Settings` (calidad AAC 64/128/256 kbps, 44,1/48 kHz, tema, acento) y `MetaStore` (marcas y duración por id). |
| `ui/` | `RecState` (único dueño del estado: página, lista, búsqueda, selección y diálogos, lo que suena, ajustes) y `GrabadoraApp` (lista, páginas encima y la hoja de grabación; `AppActions`). |
| `ui/screens/` | `ListScreen` (título, buscar, ajustes, «Sin grabaciones» con micrófono, botón rojo), `SelectionParts` (Todo, «N elementos seleccionados», Compartir/Renombrar/Eliminar/Detalles) y `DetailsDialog`, `RecordSheet` (nombre, onda en vivo, «00:00:01», Grabando/En pausa, marcar · pausa · ✓), `WaveViews` (`LiveWave` con regla y cabezal rojo; `FileWave` con marcas y toque para buscar), `PlayerScreen` (−10 s, play, +10 s, 0,5×–2×, marcas), `SettingsScreen` (calidad, grabaciones anteriores, Modo MCP, icono, temas, privacidad, versión), `McpScreen` (igual que en las apps hermanas), `Chrome` y `Texts`. Solo pintan y avisan. |
| `ui/theme/` | `Theme`: `ThemeMode` (Sistema de fábrica, Claro, Oscuro, Negro), `Accent` (rojo de fábrica…) y `RecColors`. |
| `mcp/` | Modo MCP, como las apps hermanas: `Tokens` (`grb_…`, cifrados con `SecretBox`), `RecTools` (`rec_list`, `rec_get`; con escritura `rec_rename`; a través de `RecorderData`), `McpHandler`, `RelayLink` (relay de Stone), `McpMode` (`LiveData` lee `Library`; `local` para Lucía; `connectFromHub`) y `HubLink` (módulo `rec_`, métodos `link`, `connect`, `tool`). |
| `Library.kt` | Las grabaciones: Android 10+ en MediaStore Audio, `Music/Grabadora` (`RELATIVE_PATH`, `IS_PENDING` mientras graba; borrar = papelera en 11+); Android 8–9 en la carpeta de música propia de la app (sin permisos; se comparte con `FileProvider`). `SharedValues` (preferencias «grabadora»). |
| `RecorderService.kt` | Servicio en primer plano de tipo micrófono: `MediaRecorder` AAC/.m4a, muestrea la amplitud cada 50 ms, pausa/reanuda, marcas, notificación con Pausar/Reanudar y Detener. Solo lo arranca la pantalla de la app. |
| `Session.kt` | La grabación en curso (estado de Compose): nombre, reloj, niveles, marcas. El servicio escribe, la hoja lee. |
| `Playback.kt` / `Waves.kt` | `MediaPlayer` (velocidad con `PlaybackParams`) y las ondas de cada archivo (guardadas al grabar o decodificadas una vez con `MediaCodec`, en `files/waves`). |
| `Summary.kt` | `SummaryLines` (pura, con prueba) y `SummaryProvider`: grabando ahora, cuántas hay y la última, para la pantalla «Hoy» de Inicio. |
| `AppIcon.kt` | 5 iconos (alias `.Home`, `.HomeClaro`, `.HomeDorado`, `.HomeAzul`, `.HomeVerde`) y la tarjeta para elegirlos. |
| `MainActivity.kt` | Crea el estado; micrófono (explica antes), notificaciones, «Grabaciones anteriores»; órdenes al servicio; reproducir; compartir, renombrar y eliminar (pide permiso a Android para las de antes de reinstalar); «Ir al Hub». |

Reglas:
- Texto visible en `res/values/strings.xml` (español) y `values-en`.
- Colores siempre de `RecColors`; el botón de grabar y el cabezal son siempre rojos (`RecColors.Record`).
- Archivos de pantalla de unas 150 líneas como mucho; si crece, se divide.
- `ScreenshotsTest` usa fechas fijas (noviembre de 2026, Ecuador). Los diálogos se capturan con `captureScreenRoboImage`;
  el de «Renombrar» no tiene captura (su campo de texto no deja a Compose quedarse quieto en Robolectric).
