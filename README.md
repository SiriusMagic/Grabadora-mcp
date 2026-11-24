# Grabadora (grabadora de sonido MCP)

Grabadora de sonido para Android, clara u oscura con acento rojo. Parte de la familia de Stone, Luma, Calculadora,
Notas, Filem, Nivel, Reloj y las demás: tu IA (Claude, ChatGPT…) puede **ver y renombrar tus grabaciones** por MCP,
nunca grabar ni escuchar.

## Estado (0.1.0)

- **Grabar:** botón rojo; onda en vivo con regla y cabezal, reloj, pausa, marcas y ✓. AAC en .m4a en Música/Grabadora,
  también con la pantalla apagada (notificación con Pausar y Detener).
- **Reproducir:** onda con marcas, barra, −10 s / +10 s, velocidad 0,5×–2×.
- **Selección:** Compartir, Renombrar, Eliminar (papelera del teléfono en Android 11+) y Detalles.
- **Ajustes:** calidad, frecuencia, grabaciones anteriores, **Modo MCP**, icono de la app, temas, privacidad y versión.
- **Modo MCP:** `rec_list`, `rec_get` y, con token de lectura y escritura, `rec_rename`. Mismo relay y Gateway Hub
  (módulo `rec_`) que las demás apps. No hay herramienta para grabar ni para borrar.
- **Resumen para Inicio** en la pantalla «Hoy».
- Permisos: micrófono, notificaciones y, solo si lo pides, leer audio (grabaciones de antes de reinstalar). Sin anuncios
  ni estadísticas; internet solo para el Modo MCP (apagado de fábrica).

APK: [`android/Grabadora-0.1.0.apk`](android/Grabadora-0.1.0.apk) (`com.zirakusa.grabadora`, firmado como el resto de
la familia y Gateway Hub).

## Compilar y probar

```bash
cd android
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
```
