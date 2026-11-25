# Grabadora 0.3.0: audio para transcribir

- **«Dejar que la IA escuche las grabaciones»** (Modo MCP, apagado de fábrica): si lo enciendes, una IA con token de
  lectura y escritura puede pedir el audio de una grabación con la herramienta nueva **`rec_audio`**, para transcribirlo a
  texto. Sale con su **fecha y hora, duración y peso**, y el .m4a (AAC) por partes de 256 KB que se unen en orden (así
  cabe por el relay y por Gateway Hub). Si la grabación cabe en una parte, también va como audio MCP directo. Apagado, el
  audio nunca sale (`NOT_ALLOWED`). La IA sigue sin poder grabar ni borrar.
- 23 pruebas.

# Grabadora 0.2.0

- **Iconos nuevos**: Metal (el de fábrica), Micrófono, Casete, Dulce y Píxel, en Ajustes › Icono de la app. Se quitan los
  anteriores; si tenías uno elegido, pasa solo al nuevo que ocupa su lugar.
- **Modo MCP**: `rec_list` da ahora, de cada grabación, la fecha y hora, la duración (segundos y «1:02:03») y el peso
  (bytes y «3,20 MB»), como ya hacía `rec_get`. Sigue sin salir el audio.
- 22 pruebas.

# Grabadora 0.1.0

Primera versión: grabadora de sonido de la familia, clara u oscura, con acento rojo.
- **Lista «Grabadora»** con buscar y ajustes; «Sin grabaciones» con un micrófono; cada grabación con nombre
  («20261121_063640» de fábrica), fecha, duración y sus marcas.
- **Grabar:** botón rojo; hoja con el nombre, onda en vivo (regla de tiempo y cabezal rojo), «00:00:01», Grabando / En
  pausa, marcar, pausar/reanudar y ✓ para guardar. AAC en .m4a en Música/Grabadora; sigue con la pantalla apagada
  (servicio de micrófono con notificación Pausar/Detener).
- **Reproducir:** onda del archivo con marcas (toque para ir), barra, −10 s / +10 s y velocidad 0,5×–2×.
- **Selección** (mantener pulsado): Todo, Compartir, Renombrar, Eliminar («¿Eliminar este archivo?», a la papelera en
  Android 11+) y Detalles.
- **Ajustes:** calidad (64/128/256 kbps, 44,1/48 kHz), grabaciones anteriores, Modo MCP, icono de la app (5), temas,
  privacidad y versión.
- **Modo MCP** (`grb_`, módulo `rec_`): `rec_list`, `rec_get` y, con lectura y escritura, `rec_rename`. La IA nunca graba,
  escucha ni borra. «Redireccionar a Hub», «Conectar todas» y Lucía sin internet, como Reloj.
- **Resumen para Inicio:** grabando ahora, cuántas hay y la última.
- 22 pruebas.
