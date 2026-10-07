# Faltos (aplicación Android)

Herramienta **independiente** para organizar expedientes del procedimiento
disciplinario por infracciones Leves de la Ley N.° 30714. **No pertenece a la
Policía Nacional del Perú ni a ninguna entidad pública**, y no sustituye ningún
canal oficial, acto de notificación ni plazo legal.

La aplicación envuelve y complementa la web
[moral-y-disciplina](https://github.com/NUMBRSWORD/moral-y-disciplina): aporta el
acceso con Google, la firma de políticas, el Token Digital, el escáner de
documentos y los módulos nativos de expedientes y seguimiento.

## Cómo se entra

1. Acceso con Google y aceptación de términos y política de datos.
2. Identificación (grado, nombres, CIP, DNI y teléfono) y **aprobación de un
   administrador**: crear la cuenta no da acceso a nada.
3. Firma de las políticas vigentes.
4. Activación del **Token Digital** (TOTP) y entrega de ocho códigos de
   recuperación de un solo uso, por si se pierde el teléfono.

Descargar e instalar la aplicación no da acceso a ninguna información.

## Seguridad

- Acceso a Internet y permiso opcional de notificaciones (`POST_NOTIFICATIONS` en Android 13+).
- Las pantallas del token no permiten capturas (`FLAG_SECURE`).
- El secreto del token y la llave de sesión se guardan cifrados con el llavero de
  Android (AES/GCM); las copias de seguridad del sistema están desactivadas.
- El puente con la web solo acepta mensajes del origen propio; el contenido mixto
  y el acceso a archivos están desactivados en la vista web.
- El servidor (Supabase) exige cuenta aprobada en cada consulta mediante RLS; la
  clave que lleva la aplicación es la pública (`anon`), la misma que ya publica la
  web.

## Compilar

Necesita Android Studio (JDK incluido) y el SDK de Android.

```bash
./gradlew :app:assembleQa          # versión de pruebas (paquete .qa)
./gradlew :app:testQaUnitTest      # pruebas unitarias
./gradlew :app:connectedQaAndroidTest  # pruebas en un emulador o teléfono
./gradlew :app:lintRelease         # análisis estático
```

Para generar el paquete de publicación hacen falta cuatro variables de entorno con
la clave de carga: `FALTOS_UPLOAD_STORE_FILE`, `FALTOS_UPLOAD_STORE_PASSWORD`,
`FALTOS_UPLOAD_KEY_ALIAS` y `FALTOS_UPLOAD_KEY_PASSWORD`. **Ninguna clave ni
contraseña se guarda en este repositorio.**

## Documentos

- [CLAUDE.md](CLAUDE.md) — reglas, comandos y estructura del proyecto (web, Android y Supabase).
- [docs/recuperacion-de-acceso.md](docs/recuperacion-de-acceso.md) — clave olvidada, administradores y recuperación.
- [docs/](docs/) — guías vigentes (avisos Android, respaldos, token, datos personales, IA).
- [docs/historial/](docs/historial/) — auditorías e informes anteriores, como registro; pueden describir estados ya superados.
- [supabase/](supabase/) — scripts y funciones; su presencia en Git no confirma su despliegue en producción.
