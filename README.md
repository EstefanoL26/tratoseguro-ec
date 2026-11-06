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
H2 (local) · PostgreSQL (servidor) · Stripe Java SDK · Bootstrap 5.3.3 · Maven Wrapper · Docker

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

41 pruebas: validador de cédula, máquina de estados, días hábiles (ejemplo del documento de análisis),
liquidación (los tres casos de la tabla), flujos completos del core, disputas, vía rápida, plazos,
permisos por rol y renderizado de cada pantalla.

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
| POST | `/stripe/webhook` | Aviso de Stripe (firma verificada) | Stripe |

## Patrón MVC

```
src/main/java/com/tratoseguro/
├── model/        → M: entidades JPA y enums (Transaccion, Usuario, Envio, Movimiento, Historial, Disputa...)
├── repository/   → M: acceso a datos con Spring Data JPA
├── service/      → reglas del negocio: TransaccionService, LiquidacionService, DisputaService,
│                   DiasHabilesService, PlazoService, PagoService (Stripe), NotificacionService...
├── controller/   → C: reciben la petición, llaman al servicio y eligen la vista
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

El esquema lo crean las migraciones de **Flyway** (`db/migration/V1__esquema_core.sql`) con claves foráneas y
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
