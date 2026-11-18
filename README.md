# TratoSeguro EC

Aplicación web de **pago protegido (escrow)** para compras en línea entre personas en Ecuador.
El comprador paga, la plataforma **retiene el dinero** y solo lo libera al vendedor cuando el comprador confirma
que recibió el producto. Si hay un problema, se abre una **disputa** y un **mediador** decide si se reembolsa al
comprador (total o parcialmente) o se paga al vendedor.

> **Los pagos son de prueba.** Retener dinero de terceros es una actividad regulada en Ecuador. El sistema
> implementa el flujo y la contabilidad completos con **Stripe en modo de prueba** (tarjetas de prueba) o, si no hay
> claves configuradas, con un **pago simulado**. Las claves reales de Stripe se rechazan a propósito.

Proyecto de **ISWZ3101 – Ingeniería Web (UDLA)** · Grupo 10
Estéfano Daniel López Pozo · Juan Alberto Choca Barrionuevo

**Repositorio:** https://github.com/EstefanoL26/tratoseguro-ec
**Aplicación desplegada (Render):** https://tratoseguro-ec.onrender.com
(plan gratuito: si estuvo inactiva, la primera carga tarda cerca de un minuto)

## Tecnologías

Java 17 · Spring Boot 4.1.1 · Spring MVC · Thymeleaf · Spring Data JPA · Spring Security · Flyway ·
H2 (local) · PostgreSQL (servidor) · Stripe Java SDK (Checkout + Connect) · OpenPDF · springdoc-openapi (Swagger) ·
Chart.js 4.4.1 · Bootstrap 5.3.3 · Maven Wrapper · Docker

## Cómo ejecutarlo en local

Requisito: **JDK 17 o superior** (Maven no hace falta, se usa el wrapper incluido).

```bash
# Windows
.\mvnw.cmd spring-boot:run
# Mac / Linux
./mvnw spring-boot:run
```

Abrir http://localhost:8080. La base H2 se guarda en `./data/` y se llena sola con datos de ejemplo
(para empezar de cero, borra la carpeta `data/`).

### Usuarios de prueba

| Usuario | Contraseña | Rol | Qué puede hacer |
|---|---|---|---|
| `carlos` | `clave123` | Vendedor (Carlos Mena) | Crear tratos, registrar envíos, aportar evidencia |
| `anatorres` | `clave123` | Comprador (Ana Torres) | Pagar, confirmar recepción, abrir disputas |
| `paula` | `clave123` | Mediador (Paula Ríos) | Resolver disputas, vía rápida |
| `finanzas` | `clave123` | Finanzas (Diego Salazar) | Panel de contabilidad, revisión de plazos |
| `admin` | `admin123` en local; en Render, `ADMIN_PASSWORD` | Administrador | Usuarios, feriados, finanzas |

Los datos de ejemplo incluyen un trato en cada situación del core: creado, pagado, enviado, liberado
(comisión $ 6,00 y neto $ 194,00), en disputa con evidencia de ambas partes, resuelto con reembolso parcial
($ 50,00 + $ 4,50 + $ 145,50 = $ 200,00) y dos tratos con el plazo vencido para probar la revisión automática.

### Pruebas

```bash
./mvnw test
```

68 pruebas: validador de cédula, máquina de estados, días hábiles (ejemplo del documento de análisis),
liquidación (los tres casos de la tabla), flujos completos del core, disputas, vía rápida, plazos,
permisos por rol y renderizado de cada pantalla. Además, de las mejoras: Stripe Connect (con Stripe reemplazado
por un doble de prueba), archivos de evidencia (tipo por firma de bytes, tamaño, permisos), calificaciones,
comprobante PDF, código de verificación, CSV y la API REST (credenciales, permisos y errores en JSON).

## El core: motor de custodia y liquidación del pago

### Máquina de estados

| Origen | Evento | Actor | Destino |
|---|---|---|---|
| CREADA | Pagar (Stripe de prueba o simulado) | Comprador | PAGADA |
| CREADA | Cancelar | Comprador o vendedor | CANCELADA |
| PAGADA | Registrar envío con guía | Vendedor | ENVIADA |
| PAGADA | Vence el límite de envío | Sistema | REEMBOLSADA (total) |
| PAGADA / ENVIADA | Abrir disputa | Comprador | EN_DISPUTA |
| ENVIADA | Confirmar recepción | Comprador | LIBERADA |
| ENVIADA | Vence el límite de revisión sin reclamo | Sistema | LIBERADA |
| EN_DISPUTA | Pago al vendedor | Mediador | LIBERADA |
| EN_DISPUTA | Reembolso total, parcial o vía rápida | Mediador | REEMBOLSADA |

- Las transiciones están en `EstadoTransaccion.puedeCambiarA()` y se validan en `TransaccionService.cambiarEstado()`,
  el único lugar donde cambia el estado. Cualquier otra transición se rechaza en el servidor.
- Solo los participantes del trato pueden verlo y actuar sobre él (el mediador ve los tratos con disputa;
  finanzas y el administrador, todos en modo lectura).
- Cada cambio guarda un registro en el **historial** (quién, cuándo, estado anterior, nuevo y motivo) y envía
  **notificaciones internas** a las partes.

### Plazos en días hábiles

```
límite de envío    = sumarDiasHabiles(fechaPago, diasEntrega)
límite de revisión = sumarDiasHabiles(fechaEnvio, diasEntrega + 3)
```

Sin sábados, domingos ni **feriados** (los mantiene el administrador en `/admin/feriados`). Ejemplo: pago el
jueves 01/10/2026 con 5 días → 08/10/2026; envío el 05/10/2026 → 16/10/2026 (se salta el 9 de octubre).

`PlazoService` revisa los plazos **todos los días a las 6:00 (hora de Ecuador)** con `@Scheduled`; finanzas puede
adelantar la revisión desde su panel. Los tratos en disputa quedan pausados.

### Liquidación (BigDecimal, 2 decimales, HALF_UP)

```
base = M − R        comisión = base × 0.03        neto al vendedor = base − comisión
Regla de cuadre: R + comisión + neto == M   (si no se cumple, se rechaza y el trato no cambia)
```

Cada liquidación registra sus movimientos (`RETENCION`, `LIBERACION`, `COMISION`, `REEMBOLSO`). El panel de
finanzas verifica además el cuadre global: retenido = en custodia + liberado + reembolsado + comisiones.

### Concurrencia

`Transaccion` tiene `@Version` (bloqueo optimista): si la confirmación del comprador y la liberación automática
llegan al mismo tiempo, la segunda falla y el dinero no se liquida dos veces.

## Pagos con Stripe (modo de prueba)

1. Crear una cuenta en https://dashboard.stripe.com y quedarse en **modo de prueba** (Test mode).
2. En **Developers → API keys**, copiar la **Secret key** que empieza con `sk_test_`.
3. En Render → servicio `tratoseguro-ec` → **Environment**, crear `STRIPE_SECRET_KEY` con esa clave.
   **Nunca** la escribas en el código ni la compartas.
4. (Opcional, recomendado) En Stripe → **Developers → Webhooks**, agregar el endpoint
   `https://tratoseguro-ec.onrender.com/stripe/webhook` con el evento `checkout.session.completed`, y guardar su
   *Signing secret* (`whsec_...`) en Render como `STRIPE_WEBHOOK_SECRET`.

Flujo: el comprador pulsa **Pagar y retener** → paga en Stripe Checkout con la tarjeta `4242 4242 4242 4242`
(cualquier fecha futura y CVC) → Stripe lo devuelve a la app, que **vuelve a consultar a Stripe** antes de marcar
el trato como pagado. Los reembolsos (por disputa o por plazo vencido) se crean en Stripe con una clave de
idempotencia para no reembolsar dos veces. La liberación al vendedor se registra en la contabilidad interna
(transferir a cuentas de vendedores requeriría Stripe Connect).

Sin `STRIPE_SECRET_KEY`, todo funciona igual con el pago simulado.

### Stripe Connect (pago a vendedores)

1. En Stripe (modo de prueba) entrar a **Connect** y activarlo para la cuenta (es gratis en modo de prueba).
2. Con la misma `STRIPE_SECRET_KEY`, el vendedor entra a **Cobros → Conectar con Stripe** y completa el registro
   con los datos de prueba que ofrece Stripe.
3. (Opcional) En **Developers → Webhooks**, agregar un endpoint para **eventos de cuentas conectadas** con la URL
   `https://tratoseguro-ec.onrender.com/stripe/webhook` y el evento `account.updated`, para que la app sepa
   cuándo se activa la cuenta aunque el vendedor no vuelva a la app. Usa el mismo `STRIPE_WEBHOOK_SECRET`
   si es el mismo endpoint; si Stripe genera otro secreto, el endpoint necesita su propio valor.

## Mejoras sobre el core

### 1. Stripe Connect: el vendedor recibe su dinero de verdad (modo de prueba)

Antes la liberación al vendedor solo quedaba en la contabilidad interna. Ahora se usa el modelo
**"separate charges and transfers"** de Stripe Connect, que es como funciona un escrow real:

1. El comprador le paga a la **plataforma** (Stripe Checkout). El cobro lleva `transfer_group = TRATO-{id}`.
2. El dinero queda retenido en la cuenta de la plataforma.
3. Al liberar (confirmación, plazo vencido o decisión del mediador), `CobroVendedorService` crea un
   **Transfer** por el **neto** a la cuenta conectada del vendedor, ligado al cobro original (`source_transaction`).
4. Si el vendedor todavía no conectó su cuenta, o Stripe rechaza la transferencia, la liberación se registra
   igual y la transferencia queda **PENDIENTE**: se reintenta sola cuando el vendedor termina su registro
   (o con el botón de finanzas). Antes de transferir se busca si ya existe una del mismo trato, para no pagar dos veces.

El vendedor conecta su cuenta desde **Cobros** (`/cuenta/cobros`): la app crea una cuenta *Express* de prueba
y lo lleva al registro de Stripe. Stripe Connect no opera en Ecuador, así que la cuenta de prueba se crea en EE. UU.
Con pago simulado todo sigue funcionando como antes (referencia `SIMULADO`).

### 2. Evidencia con archivos

Las partes pueden adjuntar **fotos (JPG, PNG, WebP) o PDF de hasta 5 MB** al abrir la disputa o al aportar evidencia.

- El tipo se detecta por la **firma de bytes** del archivo, no por la extensión: un `.exe` renombrado a `.jpg` se rechaza.
- Se guarda en la base (tabla `archivo_evidencia`) con su **huella SHA-256**, porque el disco de Render se borra en
  cada despliegue. El nombre se sanea (sin rutas ni caracteres raros).
- Solo lo ven las partes, el mediador, finanzas y el administrador (`/evidencias/{id}/archivo`), con
  `X-Content-Type-Options: nosniff`. El mediador ve las miniaturas de ambas partes lado a lado.

### 3. Comprobante PDF y gráficos de finanzas

- **Comprobante de liquidación en PDF** (`/transacciones/{id}/comprobante.pdf`): reparto R + comisión + neto = M,
  movimientos, decisión del mediador e historial. Lleva un **código de verificación** (SHA-256 de las cifras):
  si alguien edita el PDF, el código deja de coincidir con el que muestra la app. La cédula sale enmascarada.
- **Panel de finanzas**: gráfico de barras del dinero por mes (retenido, liberado, reembolsado, comisiones) y
  gráfico circular de tratos por estado (Chart.js, con tabla de datos accesible), **exportación CSV** de todos los
  movimientos (protegida contra inyección de fórmulas de Excel) y lista de **transferencias pendientes**.

### 4. Reputación del vendedor y API REST

- Al cerrar un trato (liberado o reembolsado), el comprador **califica al vendedor de 1 a 5 estrellas**.
  La reputación (promedio, tratos cerrados y % sin disputa) aparece **antes de pagar**, en el detalle y en el
  perfil público del vendedor (`/vendedores/{id}`).
- **API REST v1** (JSON, HTTP Basic, sin sesión) documentada con **Swagger UI** en `/swagger-ui.html`:

| Método | Ruta | Qué hace |
|---|---|---|
| GET | `/api/v1/transacciones?estado=&buscar=` | Tratos del usuario (mismas reglas de visibilidad que la web) |
| GET | `/api/v1/transacciones/{id}` | Detalle con plazos, liquidación, código de verificación, movimientos e historial |
| POST | `/api/v1/transacciones/{id}/confirmar-recepcion` | El comprador confirma y se liquida |
| GET | `/api/v1/vendedores/{id}/reputacion` | Reputación de un vendedor |
| GET | `/api/v1/liquidacion/simular?monto=200&reembolso=50` | Calcula la liquidación y el cuadre sin guardar |

```bash
curl -u anatorres:clave123 http://localhost:8080/api/v1/transacciones
```

La API usa los mismos servicios que la web (las reglas no se duplican) y nunca expone entidades: devuelve DTOs
sin contraseñas ni cédulas. Los errores salen en JSON (403, 409, 422).

## Administración (avance anterior)

- **Usuarios** (`/admin/usuarios`): CRUD con roles; la **cédula** se valida en el servidor con el algoritmo
  módulo 10 (`util/ValidadorCedula`) y no puede repetirse; la ciudad se escoge con un **dropdown dependiente**
  provincia → ciudad.
- **Feriados** (`/admin/feriados`): alimentan el cálculo de días hábiles.

## Rutas principales

| Método | Ruta | Acción | Rol |
|---|---|---|---|
| GET | `/transacciones` | Tratos (buscar y filtrar por estado) | Todos (cada uno ve lo suyo) |
| GET/POST | `/transacciones/nueva`, `/guardar`, `/editar/{id}`, `/eliminar/{id}` | Crear, editar o eliminar (solo CREADA) | Vendedor |
| GET | `/transacciones/{id}` | Detalle, plazos, liquidación e historial | Participantes |
| GET/POST | `/transacciones/{id}/pago`, `/pagar` | Pago protegido (Stripe o simulado) | Comprador |
| POST | `/transacciones/{id}/envio` | Registrar envío y guía | Vendedor |
| POST | `/transacciones/{id}/confirmar` | Confirmar recepción y liquidar | Comprador |
| POST | `/transacciones/{id}/cancelar` | Cancelar | Comprador o vendedor |
| GET/POST | `/transacciones/{id}/disputa`, `/evidencia` | Abrir disputa y aportar evidencia | Comprador / ambas partes |
| GET/POST | `/disputas`, `/disputas/{id}/resolver` | Resolver disputas y vía rápida | Mediador |
| GET/POST | `/finanzas`, `/finanzas/plazos` | Contabilidad y revisión de plazos | Finanzas, admin |
| GET | `/notificaciones` | Notificaciones internas | Todos |
| POST | `/transacciones/{id}/calificar` | Calificar al vendedor (1 a 5) | Comprador |
| GET | `/transacciones/{id}/comprobante.pdf` | Comprobante de liquidación | Participantes |
| GET | `/evidencias/{id}/archivo` | Ver o descargar un archivo de evidencia | Participantes y mediador |
| GET | `/vendedores/{id}` | Perfil y reputación del vendedor | Todos |
| GET/POST | `/cuenta/cobros`, `/conectar`, `/retorno` | Cuenta de cobro (Stripe Connect) | Vendedor |
| GET/POST | `/finanzas/movimientos.csv`, `/finanzas/transferencias` | CSV y reintento de transferencias | Finanzas, admin |
| GET | `/swagger-ui.html`, `/api/v1/**` | Documentación y API REST | Público / HTTP Basic |
| POST | `/stripe/webhook` | Aviso de Stripe (firma verificada) | Stripe |

## Patrón MVC

```
src/main/java/com/tratoseguro/
├── model/        → M: entidades JPA y enums (Transaccion, Usuario, Envio, Movimiento, Historial, Disputa...)
├── repository/   → M: acceso a datos con Spring Data JPA
├── service/      → reglas del negocio: TransaccionService, LiquidacionService, DisputaService,
│                   DiasHabilesService, PlazoService, PagoService (Stripe), NotificacionService...
├── controller/   → C: reciben la petición, llaman al servicio y eligen la vista
├── api/          → API REST v1 (DTOs, controlador y errores en JSON)
├── config/       → seguridad y datos iniciales
└── util/         → ValidadorCedula, Reloj (hora de Ecuador)
src/main/resources/
├── db/migration/ → esquema de la base (Flyway)
└── templates/    → V: vistas Thymeleaf (fragmentos/base.html tiene el navbar y piezas comunes)
```

## Base de datos

| Entorno | Base |
|---|---|
| Local | H2 en archivo (`./data/`) |
| Servidor (Render) | PostgreSQL 18 (`tratoseguro-db`), perfil `prod` |
| Pruebas | H2 en memoria |

El esquema lo crean las migraciones de **Flyway** (`db/migration/V1__esquema_core.sql` y
`V2__cobros_archivos_calificaciones.sql`, que solo agrega tablas y columnas) con claves foráneas y
restricciones con nombre (`fk_transaccion_vendedor`, `ck_transaccion_estado`, ...); Hibernate solo valida que las
entidades coincidan. Diagrama y consultas para DBeaver: [`docs/BASE_DE_DATOS.md`](docs/BASE_DE_DATOS.md) y
[`docs/consultas.sql`](docs/consultas.sql).

## Despliegue (Render)

`Dockerfile` en dos etapas y `render.yaml` (Blueprint): servicio web + base PostgreSQL. Variables de entorno:
`ADMIN_PASSWORD`, `STRIPE_SECRET_KEY` y `STRIPE_WEBHOOK_SECRET` (opcionales), y los datos de la base, que Render
pasa solo. Cada `git push` a `main` se despliega automáticamente.

## Seguridad

- Contraseñas con **BCrypt**; formularios con token **CSRF**; todo lo que modifica datos es **POST**.
- Permisos por rol en `SecurityConfig` y por participación en los servicios (un usuario ajeno recibe 403).
- Validación en el servidor de cédula, montos, guías, enlaces de evidencia y transiciones de estado.
- Stripe: solo claves de prueba, el pago se verifica consultando a Stripe y el webhook exige firma válida.
- Las claves y contraseñas del servidor son variables de entorno, nunca código.
