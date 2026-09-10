# KoHs Core: investigación de FPS, latencia y streaming

Fecha de corte: 2026-09-06. Objetivo: definir diez funciones nuevas, medibles y
compatibles con el límite de confianza de un mod cliente. Esta lista no promete
"menos ping": separa tiempo de red, tiempo de CPU, tiempo de GPU y estabilidad de
OBS.

## Reglas de diseño

- Las diez propuestas son **L0 (sólo cliente)**: no adelantan ataques, no inventan
  paquetes, no alteran movimiento y no omiten validación del servidor.
- KoHs Core no tocará `maxFps` ni activará VSync. En Minecraft, `260` representa
  la opción *Ilimitado* en la instancia objetivo. El modo de stream liberará GPU
  reduciendo trabajo visual, no imponiendo un límite de FPS.
- Toda adaptación debe ser reversible, tener histéresis para que la imagen no
  oscile y ceder el dominio cuando Sodium, Iris, ImmediatelyFast, EntityCulling u
  otro mod especializado ya haga el trabajo.
- Una cifra de mejora sólo se publicará después de una prueba A/B repetible. Los
  porcentajes de otros proyectos sirven como evidencia de dirección, no como una
  promesa para KoHs Core.

## Las 10 funciones candidatas

| # | Nombre de trabajo | Aporta | Funcionamiento propuesto | Riesgo / complejidad |
|---:|---|---|---|---|
| 1 | **GPU Headroom Governor** | FPS estables, 1% lows, stream | Medir el tiempo real de GPU de forma asíncrona y mantener margen para OBS bajando temporalmente nubes, sombras, partículas y distancia de entidades. Nunca cambia el límite de FPS. | Media. Las consultas nunca se deben esperar en el mismo frame. |
| 2 | **OBS Stream Sentinel** | Streaming | Conexión local, opcional y de sólo lectura a obs-websocket. Detecta stream/grabación y observa frames de render/salida omitidos; activa y revierte un perfil visual de stream con histéresis. | Media. Credencial protegida y ninguna acción remota por defecto. |
| 3 | **WorldScale dinámico** | FPS GPU, stream | Renderizar sólo el mundo 3D entre 70–100% y reescalarlo; HUD, texto y menús permanecen nativos. Baja un paso únicamente bajo carga GPU sostenida y sube lentamente. | Alta. Debe usar la abstracción de Blaze3D y tener puertas de compatibilidad para Iris/Sodium/Vulkan. |
| 4 | **Chunk Upload Stutter Shield** | 1% lows al moverse | Presupuestar por tiempo y bytes la construcción/subida de mallas por frame. Bloques cercanos o recién modificados: inmediato o máximo un frame; terreno lejano: diferible. | Alta. Si Sodium ya gobierna las subidas, Core cede completamente. |
| 5 | **Static Text Geometry Cache** | FPS en hubs, menos asignaciones | Cachear geometría de letreros y `TextDisplay` estáticos y invalidarla al cambiar texto, estilo, luz, fuente o paquete de recursos. Complementa el culling por distancia ya existente. | Media. Se desactiva si ImmediatelyFast ya amortigua ese camino. |
| 6 | **Occlusion Culling conservador** | FPS CPU/GPU en zonas densas | Trazado asíncrono para entidades no jugador y block entities totalmente ocultas. Sólo omite render; no omite ticks, IA ni paquetes. Lista segura para beacons y renderizadores fuera de su caja. | Media-alta. Nunca oculta jugadores/nametags y cede a EntityCulling/MoreCulling. |
| 7 | **Shader & Pipeline Warm-up** | Menos tirones de primer uso | Durante carga o menú, precalentar los pipelines comunes y consultar compilación paralela cuando el backend lo soporte. Nunca inicia una compilación nueva en combate. | Alta. La lista se invalida al cambiar shader o resource pack; fallback normal si la extensión falta. |
| 8 | **Hybrid GPU Capture Guard** | Stream y captura estables | Identificar la GPU de Minecraft, la GPU de presentación y, con consentimiento, la usada por OBS. Avisar si están cruzadas y ofrecer instrucciones exactas; no cambia Windows silenciosamente. | Baja-media. Muy relevante en portátiles con iGPU+dGPU. |
| 9 | **Frame Spike Black Box** | Diagnóstico de 1%/0.1% lows | Búfer circular local que, al detectar un frame largo, conserva unos segundos de frame time, GC, carga CPU, tareas de chunks y muestras de stacks. Exporta un informe anonimizado sólo a petición. | Media. Perfil continuo mínimo; modo detallado breve y explícito. |
| 10 | **Input-to-Present Meter** | Diagnóstico y ajuste de latencia | Marcas desde el callback de teclado/ratón hasta extracción, envío al render y finalización GPU. Presenta P50/P95/P99 y separa *input→frame* de ping/confirmación del servidor. | Media-alta. Métrica local; no afirma que un ataque ocurrió antes de la respuesta del servidor. |

## Por qué estas diez

### 1–3: margen de GPU sin capar FPS

OBS necesita GPU incluso cuando se usa un encoder dedicado, porque debe componer
y renderizar la escena. Su guía oficial advierte que un juego sin límite puede
consumir todo el margen y recomienda limitar FPS o reducir gráficos. La restricción
de este proyecto descarta el cap, así que la alternativa honesta es reducir de
forma adaptativa el **trabajo visual**. El `GPU Headroom Governor` se apoya en
consultas temporales asincrónicas: Khronos especifica que permiten medir comandos
GL sin detener el pipeline y que el resultado debe recogerse en frames posteriores.

`OBS Stream Sentinel` no necesita adivinar si OBS va bien. `GetStats` expone
`averageFrameRenderTime`, `renderSkippedFrames` y `outputSkippedFrames`, y
`GetStreamStatus` dice si la salida está activa o reconectando. La conexión queda
apagada por defecto, sólo en localhost y autenticada. `WorldScale` es el escalón de
mayor impacto cuando la carga sigue saturada, pero conserva la interfaz a resolución
nativa para que chat, crosshair y overlays del stream no se vuelvan borrosos.

### 4–7: atacar trabajo costoso, no la red

El código actual de Sodium ya demuestra un modelo útil: estima duración, limita
subidas por tiempo/tamaño y separa tareas importantes, de un frame y diferibles.
Los reportes de sus usuarios también relacionan movimiento/carga de chunks con
microtirones. Core sólo implementaría esto cuando ningún renderer especializado
sea propietario del dominio.

La medición JFR de esta instancia señaló la preparación de texto del mundo como el
foco de asignación más claro. ImmediatelyFast confirma de manera independiente
que el batching y el buffering de texto de letreros son rutas valiosas, aunque sus
números no deben extrapolarse. El caché propuesto siempre invalida ante cambios y
cede si ese mod está instalado.

EntityCulling valida el patrón de calcular visibilidad en hilos libres y omitir
draw calls ocultos. KoHs debe ser más conservador: no tick-culling, no jugadores,
fallback a visible y exclusiones explícitas. Para tirones de shaders, Khronos
ofrece `KHR_parallel_shader_compile`, que permite limitar hilos de compilación y
consultar si shader/programa terminó. Eso habilita warm-up sin bloquear, si el
driver y el backend lo ofrecen.

### 8–10: streaming y evidencia reproducible

OBS documenta específicamente que Minecraft y OBS deben usar la GPU correcta en
sistemas con varias GPU. El guard detectará la topología y explicará el arreglo;
no editará preferencias del sistema sin confirmación.

Para los tirones que sobrevivan, Oracle indica que una grabación JFR continua
suele no tener efecto medible y que el perfil predeterminado queda por debajo de
2% en la mayoría de aplicaciones. El Black Box usará el nivel continuo y sólo
subirá el detalle durante una ventana corta. La solicitud de `--only-frames-over`
en spark muestra exactamente el hueco de diagnóstico de cliente: TPS normal no
descarta frames largos.

Finalmente, NVIDIA divide la latencia en entrada, simulación, envío de render,
driver, cola y GPU. KoHs puede exponer una versión agnóstica de GPU de esas marcas
para saber si la sensación viene de la cola local o del servidor. Será un medidor,
no un sistema que modifique paquetes ni una etiqueta falsa de "ping reducido".

## Orden recomendado de implementación

1. **Static Text Geometry Cache**, porque ya existe evidencia directa de esta
   instancia y es acotado.
2. **OBS Stream Sentinel** + **GPU Headroom Governor**, primero sólo telemetría y
   después adaptación A/B.
3. **Frame Spike Black Box**, para medir correctamente lo que sigue.
4. **Chunk Upload Stutter Shield**, exclusivamente en vanilla y con pruebas de
   aparición de bloques cercanos.
5. **Hybrid GPU Capture Guard**.
6. **Input-to-Present Meter**.
7. **Occlusion Culling conservador**.
8. **Shader & Pipeline Warm-up**.
9. **WorldScale dinámico**, al final por ser la intervención más profunda en el
   renderer.

## Criterios de aceptación comunes

- Prueba A/B de 3 recorridos iguales: mediana, P95, P99, peor frame, uso CPU/GPU y
  asignaciones por segundo; para stream, delta de frames omitidos de OBS.
- Sin tráfico adicional de gameplay y sin hooks que cambien el orden/contenido de
  paquetes vanilla.
- `maxFps` permanece en Ilimitado y VSync permanece como lo eligió el usuario.
- Restauración exacta de cada ajuste al apagar la función, cerrar stream o salir.
- Si faltan capacidades o aparece un mod dueño, la función falla abierta: render
  vanilla/especializado, no pantalla negra ni objeto invisible.

## Fuentes primarias y foros técnicos consultados

- [OBS: Encoding Performance Troubleshooting](https://obsproject.com/kb/encoding-performance-troubleshooting)
- [OBS: Hardware Encoding](https://obsproject.com/kb/hardware-encoding)
- [OBS: Minecraft Java Edition Troubleshooting](https://obsproject.com/kb/minecraft-java-edition-troubleshooting)
- [OBS Forum: encoding overload y competencia de GPU](https://obsproject.com/forum/threads/how-to-debug-encoding-overloaded.168625/)
- [obs-websocket: README y autenticación](https://github.com/obsproject/obs-websocket)
- [obs-websocket 5.x: GetStats y GetStreamStatus](https://raw.githubusercontent.com/obsproject/obs-websocket/master/docs/generated/protocol.md)
- [Khronos: ARB_timer_query](https://registry.khronos.org/OpenGL/extensions/ARB/ARB_timer_query.txt)
- [Khronos: KHR_parallel_shader_compile](https://registry.khronos.org/OpenGL/extensions/KHR/KHR_parallel_shader_compile.txt)
- [Fabric: conceptos de render y abstracción Blaze3D](https://docs.fabricmc.net/develop/rendering/basic-concepts)
- [Sodium: presupuesto de tareas y subidas de chunks](https://github.com/CaffeineMC/sodium/blob/dev/common/src/main/java/net/caffeinemc/mods/sodium/client/render/chunk/RenderSectionManager.java)
- [Sodium: opciones de chunks, fullscreen y VSync](https://github.com/CaffeineMC/sodium/blob/dev/common/src/main/resources/assets/sodium/lang/en_us.json)
- [Sodium issue #1070: tirones al moverse y deferir chunks](https://github.com/CaffeineMC/sodium/issues/1070)
- [ImmediatelyFast: batching, texto, HUD y buffering de letreros](https://github.com/RaphiMC/ImmediatelyFast/blob/26.2/README.md)
- [EntityCulling: trazado asíncrono y límites](https://github.com/tr7zw/entityculling)
- [spark: perfil ligero de cliente](https://github.com/lucko/spark)
- [spark issue #555: frames largos y 1% lows](https://github.com/lucko/spark/issues/555)
- [Oracle Java 25: diagnóstico con JFR](https://docs.oracle.com/en/java/javase/25/troubleshoot/troubleshoot-performance-issues-using-jfr.html)
- [NVIDIA Reflex SDK: etapas de latencia y marcadores](https://developer.nvidia.com/performance-rendering-tools/reflex)

