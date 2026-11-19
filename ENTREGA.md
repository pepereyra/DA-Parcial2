# Parcial II · Jornadas del Sur · Entrega

Proyecto: `Jornadas/`. Consigna original: `README.md`.
El primer commit del repo es el proyecto tal como se entregó; el diff contra ese commit muestra todos los cambios.

## Resumen de cambios

| Archivo | Cambio |
| --- | --- |
| `data/Programa.kt` | Tabla `edicion` + migración 1→2; transacción `aplicarInstantanea` que borra retiradas y conserva marcas. |
| `data/AgendaRepository.kt` | Expone `Cartelera` (charlas + si hay edición previa) y sincroniza con la transacción nueva. |
| `Servicios.kt` | Instancia única por proceso (Room, Retrofit y ServicioLocal) + bandera de carga automática. |
| `ui/ProgramaModel.kt` | Filtros en `SavedStateHandle`, estado `fallo` en lugar de un texto, deduplicación de peticiones. |
| `MainActivity.kt` | Se quita la recarga de `onStart`; una carga automática por proceso. |
| `ui/Catalogo.kt` | Se quita el `LaunchedEffect` que recargaba; la falla ya no oculta la lista; estados vacíos. |
| `ui/Portada.kt` | Estado de la lista elevado (volver al mismo punto) y aviso si la ficha abierta fue retirada. |
| `data/Sincronizacion.kt` (nuevo) | `ResultadoSync` (sealed) y reglas puras `Instantanea` (validar fichas, fusionar marcas, detectar retiradas). |
| `app/build.gradle.kts` | OkHttp 4.12 explícito (el original no compilaba), `versionCode` 13 / `versionName` 1.3 (es una actualización de una app instalada), JUnit y coroutines-test. |
| `app/src/test/.../InstantaneaTest.kt` (nuevo) | 6 tests unitarios con los casos v1→v2→v3→v4 del enunciado. |
| `app/src/test/.../ProgramaModelTest.kt` (nuevo) | 3 tests del ViewModel con un fake de `Agenda`: una carga por proceso, doble toque = un intento, filtros en `SavedStateHandle`. |

No se tocaron `lab/ServicioLocal.kt` ni `ui/Mesa.kt` (son el banco de pruebas) ni `legacy/AgendaImpresa.kt` (código sin uso, fuera del alcance).

---

## Pedido 41 · Programa sin señal

**Problema.** `Catalogo` mostraba `aviso` *en lugar* de la `Grilla`: ante cualquier falla la lista desaparecía, aunque los datos estuvieran en Room. Además no había forma de saber si existía una edición previa: con v4 (lista vacía) la base queda sin filas, igual que una instalación nueva.

**Consecuencia.** Sin señal el usuario perdía el programa en pantalla aunque estuviera guardado, y un programa vacío válido no se distinguía de "nunca descargué nada".

**Corrección.**
- Tabla `edicion` (una fila) que se escribe en la misma transacción que la instantánea. `hayEdicion` se observa junto con las charlas (`Cartelera`).
- `fallo` es un booleano; la UI decide el texto según el estado guardado:
  - falla + edición previa → "Sin conexión: se están usando datos guardados." y la lista, el detalle y Mi agenda siguen visibles (vienen de Room);
  - falla sin edición → "No se pudo descargar el programa." + **Reintentar**;
  - edición válida vacía → "Sin charlas programadas." (también al reabrir sin señal, porque la fila de `edicion` persiste).
- Se capturan todas las excepciones de la petición (red, HTTP, JSON), re-lanzando `CancellationException`.

## Pedido 42 · Agenda personal

**Problema.** `guardar` usaba `@Insert(REPLACE)` con charlas armadas desde la respuesta (`favorita = false`). REPLACE borra y reinserta la fila, así que **cada sincronización borraba todas las marcas**. Además nunca se borraban las charlas que la organización retiraba.

**Consecuencia.** El usuario perdía su agenda en cada actualización; en v3 la charla 11 seguía en catálogo y Mi agenda.

**Corrección.** `CarteleraDao.aplicarInstantanea` (`@Transaction`):
1. lee las marcas actuales **dentro de la transacción** (no antes de la petición), así una marca agregada o quitada mientras la respuesta viajaba se respeta;
2. borra las charlas que no vienen en la instantánea (con su marca) → si una charla vuelve, vuelve sin marcar;
3. guarda las vigentes por id estable con su marca → la 11 en v2 conserva la marca con el título y la sala nuevos;
4. registra la edición.

**Migración.** La app instalada ya tiene usuarios con datos. Se sube la base a versión 2 con `MIGRACION_1_2` explícita (sin `fallbackToDestructiveMigration`, que borraría las marcas). Si ya había charlas, la migración registra una edición previa.

## Pedido 43 · Volver al punto de lectura

**Problema.** El tema estaba en `remember` dentro de `Catalogo` y el día en un `MutableStateFlow` del ViewModel. Al abrir una ficha `Catalogo` sale de la composición y el tema se pierde; tras la muerte del proceso se pierden ambos.

**Consecuencia.** Día 2 + Datos → abrir ficha → volver mostraba todos los temas; tras `am kill`, ambos filtros se reiniciaban.

**Corrección.** Día y tema se guardan en `SavedStateHandle` (`getStateFlow`). Sobreviven a rotación, a la navegación y a la muerte del proceso; una tarea nueva arranca con el handle vacío (sin filtros). La ficha abierta y la sección ya estaban en `rememberSaveable`. El `LazyListState` se elevó a `Portada` para volver a la misma posición de la lista.

## Pedido 44 · Control de descargas

**Problema.** Se recargaba en `MainActivity.onStart` (cada rotación, cada vuelta del fondo) **y** en `LaunchedEffect(Unit)` de `Catalogo` (cada vez que se volvía de una ficha o de otra pestaña). `recargar` lanzaba una corrutina nueva por toque. Y cada ViewModel creaba su propio `Servicios` (otra base Room, otro cliente y otro contador).

**Consecuencia.** Peticiones duplicadas (rotar sumaba 2), dobles toques sumaban 2, carreras entre respuestas.

**Corrección.**
- `Servicios.de(context)`: instancia única por proceso.
- `cargaAutomatica()` en `onCreate`, protegida por una bandera de proceso: una sola carga automática por sesión; rotar, entrar a una ficha o cambiar de pestaña agregan cero.
- `recargar()` ignora la llamada si hay un `Job` activo: dos toques durante una petición = un intento. Al terminar (éxito o falla) se puede volver a pedir.
- Los filtros no se deshabilitan durante la carga.

---

## Problemas extra encontrados (cruzados con la teoría de la cátedra)

| # | Problema | Consecuencia | Corrección |
| --- | --- | --- | --- |
| E1 | **El proyecto original no compilaba**: Retrofit 2.11 trae OkHttp 3.14, que no tiene `toMediaType()`/`toResponseBody()` que usa `ServicioLocal`. | `assembleDebug` fallaba. | Declarar `okhttp:4.12.0` en `build.gradle.kts` (sin tocar el banco de pruebas). |
| E2 | Solo se capturaba `IOException`. Retrofit tiene **tres tipos de fallo**: conexión, HTTP (`HttpException`) y parseo. | Un HTTP 500 o un JSON inválido era una excepción no capturada en `viewModelScope`: **crash**. | El Repository traduce cada caso a un `ResultadoSync` explícito y re-lanza `CancellationException` para no romper la cancelación estructurada. |
| E3 | Cada ViewModel creaba **su propia** base Room, Retrofit/OkHttp y `ServicioLocal`; el ViewModel creaba sus dependencias. | Varias conexiones a la misma base, pool de conexiones desperdiciado, contador de sesión inconsistente, imposible testear con un fake. | `Servicios.de(context)` singleton con `applicationContext` + `ProgramaModel.Factory` que **inyecta** `Agenda` (interfaz), `ServicioLocal`, `SesionDeCarga` y `SavedStateHandle`. |
| E4 | `dia`, `aviso` y `cargando` eran `MutableStateFlow` **públicos** y la UI los escribía (`model.dia.value = it`); el error era un `String`. | No hay un dueño del estado (rompe UDF); combinaciones imposibles (cargando + aviso viejo). | Un solo `StateFlow<ProgramaUiState>` de solo lectura; la UI emite intenciones (`elegirDia`, `actualizar`, `marcar`); error modelado con `sealed interface ResultadoSync` y `when` exhaustivo. |
| E5 | Gson ignora la nulabilidad de Kotlin. | Una ficha sin `title` deja `null` en un `String` "no nulo" y explota después en la UI. | `FichaRemota` con campos nulables y validación en `Instantanea.aCharla`; una ficha incompleta invalida la instantánea entera (se conserva la edición anterior). |
| E7 | El aviso de "datos guardados" estaba solo en el catálogo. | En Mi agenda o en una ficha la falla no se informaba (la consigna pide conservar lista, detalle y favoritas **e informar**). | `AvisoSincronizacion` arriba de `Portada`: visible en todas las pantallas. El "Reintentar" sin edición previa queda en el catálogo. |
| E8 | El ViewModel recibía el banco de pruebas (`ServicioLocal`). | Dependencia de Android innecesaria: no se podía instanciar en un test JVM. | `MainActivity` toma la Mesa de `Servicios` y se la pasa a `Portada`; el ViewModel solo depende de `Agenda`, `SesionDeCarga` y `SavedStateHandle`. |
| E6 | Al retirarse una charla abierta o agendada no se informaba nada. | Ficha en blanco; la charla desaparece de Mi agenda sin explicación. | "Esta charla fue retirada del programa" en la ficha y aviso con los títulos retirados de Mi agenda tras sincronizar. |

## Evidencia

**Automática:** `./gradlew assembleDebug testDebugUnitTest` compila y pasan **9/9 tests**.
- `InstantaneaTest` (6): marca conservada en v2 con título/sala nuevos, marca hecha durante la petición, 11 retirada en v3, charla que regresa sin marcar, v4 vacía, ficha incompleta rechazada.
- `ProgramaModelTest` (3): una sola carga automática por proceso aunque se recree el ViewModel; dos toques durante una petición = un intento y tras la falla se puede volver a pedir; filtros recuperados del `SavedStateHandle` y tarea nueva sin filtros.

**Manual en emulador (Pixel 10 Pro, API 36), resultados observados:**

| Prueba | Resultado |
| --- | --- |
| Actualización desde la versión instalada (APK original con datos → APK nuevo encima) | Sin crash, charlas conservadas, sin señal reconoce la edición previa ("Sin conexión: se están usando datos guardados."). |
| v1: marcar la 11 → v2 | La 11 muestra «Compose en producción avanzada» · «Sala Puerto» · "En mi agenda". |
| → v3 | Desaparece la 11 del catálogo y de Mi agenda, aparece «Clínica de trazas» (90) y el aviso "Se retiró del programa: Compose en producción avanzada". |
| → v1 de nuevo | La 11 vuelve **sin marcar**. |
| Demora larga: Actualizar y, durante la espera, agregar «Navegación entre pantallas» | Al llegar la respuesta la marca se conserva. |
| Demora larga: 3 toques seguidos en Actualizar | El contador de la Mesa sube de 4 a 5 (un solo intento). Los filtros se pueden cambiar durante la espera. |
| v4 | "Sin charlas programadas." |
| Reabrir sin servicio con v4 guardada | "Sin conexión: se están usando datos guardados." + "Sin charlas programadas." |
| `pm clear` + arranque con `--ez servicio_disponible false` | "No se pudo descargar el programa (sin conexión)." + Reintentar. |
| Rotar dos veces | El contador de la Mesa queda en 1. |
| Día 2 + Datos → abrir «Cachés para viajeros» → Volver → rotar | 2 resultados de Datos (Cachés para viajeros, Consultas observables). |
| Ficha abierta → Inicio → `adb shell am kill ar.jornadas` → volver desde Recientes | Se recupera la ficha; al volver, Día 2 + Datos siguen aplicados. |
| Abrir desde el ícono (tarea nueva) tras matar el proceso | Arranca sin filtros. |

**Limitación observada (no modificada):** en horizontal, el encabezado y los filtros ocupan casi toda la altura y la lista queda muy chica (hay que desplazarla). Es del diseño original y no afecta el estado.

## Cómo verificar (con la Mesa)

1. **41:** con v1 cargada, apagar el servicio y Actualizar → lista + "datos guardados". `adb shell pm clear ar.jornadas` y `adb shell am start -n ar.jornadas/.MainActivity --ez servicio_disponible false` → "No se pudo descargar" + Reintentar. v4 con servicio → "Sin charlas programadas"; reabrir sin servicio → igual + aviso.
2. **42:** marcar la 11 en v1 → v2: sigue marcada con «Compose en producción avanzada» / «Sala Puerto». v3: desaparece la 11, entra la 90. Volver a v1: la 11 vuelve sin marcar. Con demora larga, marcar/desmarcar durante la petición y comprobar que se respeta.
3. **43:** Día 2 + Datos → «Cachés para viajeros» → volver → rotar: 2 resultados. Con la ficha abierta, Inicio, `adb shell am kill ar.jornadas`, volver desde Recientes: ficha abierta; al volver, ambos filtros.
4. **44:** mirar "Peticiones en esta sesión" en la Mesa: 1 al abrir; rotar/ficha/pestañas no suman; dos toques en Actualizar con demora larga suman 1.
