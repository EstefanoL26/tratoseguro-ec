# TratoSeguro EC

Aplicación web de **pago protegido (escrow)** para compras en línea entre personas en Ecuador.
El comprador paga, la plataforma **retiene el dinero** y solo lo libera al vendedor cuando el comprador
confirma que recibió el producto. Si hay un problema, un **mediador** decide si se reembolsa al comprador
(total o parcialmente) o se paga al vendedor.

> **Los pagos son simulados.** Retener dinero de terceros es una actividad regulada en Ecuador; el sistema
> implementa el flujo y la contabilidad completos, pero sin dinero real.

Proyecto de la materia **ISWZ3101 – Ingeniería Web (UDLA)** · Grupo 10
Estéfano Daniel López Pozo · Juan Alberto Choca Barrionuevo

**Repositorio:** https://github.com/EstefanoL26/tratoseguro-ec
**Aplicación desplegada:** _(pega aquí la URL de Render, por ejemplo https://tratoseguro-ec.onrender.com)_

## Tecnologías

Java 17 · Spring Boot 4.1.1 · Spring MVC · Thymeleaf · Spring Data JPA · Spring Security · H2 · Bootstrap 5.3.3 · Maven Wrapper · Docker

## Cómo ejecutarlo en local

Requisito: **JDK 17 o superior** (Maven no hace falta, se usa el wrapper incluido).

```bash
# Windows
.\mvnw.cmd spring-boot:run
# Mac / Linux
./mvnw spring-boot:run
```

Abrir http://localhost:8080

La base de datos H2 se guarda en `./data/tratoseguro.mv.db` y se llena sola la primera vez con datos de ejemplo.
Para empezar de cero, borra la carpeta `data/`.

### Usuarios de prueba

| Usuario | Contraseña | Rol | Ciudad |
|---|---|---|---|
| `admin` | `admin123` (en el servidor: variable `ADMIN_PASSWORD`) | Administrador | Quito |
| `carlos` | `clave123` | Vendedor (Carlos Mena) | Quito |
| `anatorres` | `clave123` | Comprador (Ana Torres) | Guayaquil |
| `paula` | `clave123` | Mediador (Paula Ríos) | Cuenca |
| `finanzas` | `clave123` | Finanzas (Diego Salazar) | Ambato |

Las cédulas de ejemplo son ficticias, pero cumplen el dígito verificador.

### Pruebas

```bash
./mvnw test
```

## Módulo de administración (avance)

La administración **alimenta al core**: el motor de custodia necesita saber quién es comprador, vendedor,
mediador o personal de finanzas, y dónde vive cada uno (para los envíos). Desde `/admin/usuarios` el
administrador crea, consulta, edita y elimina esos usuarios y les asigna su rol.

| Método | Ruta | Acción |
|---|---|---|
| GET | `/admin/usuarios` (`?buscar=`) | Listar y buscar por nombre, usuario o cédula |
| GET | `/admin/usuarios/nuevo` | Formulario vacío |
| GET | `/admin/usuarios/editar/{id}` | Formulario con los datos del usuario |
| POST | `/admin/usuarios/guardar` | Crear o actualizar (con validaciones en el servidor) |
| POST | `/admin/usuarios/eliminar/{id}` | Eliminar |
| GET | `/admin/usuarios/ciudades?provinciaId=` | JSON con las ciudades de una provincia (dropdown dependiente) |

Todas las rutas `/admin/**` exigen el rol **ADMIN** (`SecurityConfig`). Un comprador o vendedor que intente
entrar recibe **403 – Acceso denegado**.

### 1. Validación en el back-end de un dato sensible: la cédula

La cédula identifica a cada persona que paga o cobra dinero en un trato, por eso es el dato sensible del core.
No se valida con JavaScript (el formulario usa `novalidate`): se valida **en el servidor, antes de guardar**.

- `util/ValidadorCedula` implementa el algoritmo del Registro Civil (**módulo 10**): 10 dígitos, código de
  provincia 01–24 (o 30), tercer dígito menor a 6 y dígito verificador correcto.
- `UsuarioService.guardar(...)` llama al validador y además comprueba que la cédula **no esté repetida**.
  Si falla, marca el error en el campo y **no se guarda nada** en la base de datos.
- El mismo control se aplica en el registro público (`/registro`).
- Prueba: `ValidadorCedulaTest` y `AdminUsuarioControllerTest.noSeGuardaUnUsuarioConCedulaInvalida`
  (envía el POST directo al servidor, sin navegador, y comprueba que el usuario no se creó).

Otras validaciones del servidor: usuario único, correo válido, contraseña de 6 caracteres o más (cifrada con
BCrypt), la ciudad debe existir en el catálogo, el admin no puede quitarse su propio rol ni eliminar su cuenta,
y en el registro público solo se puede escoger comprador o vendedor (nunca administrador).

### 2. Referencia a otra tabla con dropdown dependiente: provincia → ciudad

`USUARIO` tiene una clave foránea `id_ciudad` hacia `CIUDAD`, y cada `CIUDAD` pertenece a una `PROVINCIA`.

```
PROVINCIA (id, codigo, nombre) 1 ──── N CIUDAD (id, nombre, id_provincia) 1 ──── N USUARIO (..., id_ciudad)
```

En el formulario no se escribe ningún id a mano:

1. Se escoge la **provincia** en un dropdown (las 24 provincias del Ecuador; su código es el mismo de los dos
   primeros dígitos de la cédula).
2. Al cambiarla, JavaScript pide al servidor `GET /admin/usuarios/ciudades?provinciaId=X` y el dropdown de
   **ciudades** se llena solo con las ciudades de esa provincia.
3. Al guardar, el servidor vuelve a comprobar que la ciudad exista (por si alguien envía un id inventado).

## Patrón MVC

```
src/main/java/com/tratoseguro/
├── model/        → M: entidades JPA (Transaccion, Usuario, Provincia, Ciudad) y enums (Rol, EstadoTransaccion)
├── repository/   → M: acceso a datos con Spring Data JPA
├── service/      → lógica de negocio y validaciones (TransaccionService, UsuarioService, UbicacionService)
├── controller/   → C: reciben la petición, llaman al servicio y eligen la vista
├── util/         → ValidadorCedula (algoritmo módulo 10)
└── config/       → seguridad (SecurityConfig) y datos iniciales
src/main/resources/templates/
├── login.html, registro.html
├── lista.html, formulario.html          (CRUD de tratos)
├── admin/usuarios.html                  (lista de usuarios)
├── admin/usuario-form.html              (crear / editar con dropdown dependiente)
└── error/403.html
```

Flujo: `Navegador → Controller → Service → Repository → BD` y de vuelta `Controller → Model → Vista Thymeleaf`.

## Despliegue (Render)

El proyecto incluye un `Dockerfile` (compila con el Maven Wrapper y ejecuta con Java 17) y un `render.yaml`.

1. Subir el repositorio a GitHub.
2. En https://render.com → **New → Blueprint** (o **Web Service**) → conectar el repositorio. Render detecta el
   `Dockerfile` y el `render.yaml`.
3. En **Environment**, definir `ADMIN_PASSWORD` con una contraseña segura para el usuario `admin`.
4. Render compila la imagen y publica la app en `https://<nombre>.onrender.com`. Spring Boot usa el puerto de
   la variable `PORT` que entrega Render.

> En el plan gratuito el disco no es persistente: si el servicio se reinicia, la base H2 vuelve a los datos de
> ejemplo. Para producción se migrará a PostgreSQL (está en la hoja de ruta).

## Seguridad

- Contraseñas cifradas con **BCrypt**; nunca se guardan en texto plano.
- Todo lo que modifica datos es **POST** con token **CSRF** (`th:action`).
- La consola de H2 está desactivada a propósito: permitiría ver los hashes o cambiarse el rol a ADMIN.
- La contraseña del admin del servidor no está en el código: se toma de la variable `ADMIN_PASSWORD`.
- En una edición, el servicio vuelve a leer el registro de la base y solo copia los campos permitidos.

## Hoja de ruta

1. ~~Roles reales: comprador, vendedor, mediador, finanzas y administrador.~~ ✔ (este avance)
2. Relacionar la transacción con usuarios (vendedor y comprador) y restringir lo que ve y hace cada uno.
3. Máquina de estados completa del core.
4. Plazos automáticos en días hábiles con `@Scheduled`.
5. Disputas con evidencia y decisión del mediador, incluida la vía rápida.
6. Contabilidad: retención, comisión (3 %), liberación y reembolso con la regla de cuadre.
7. Historial de cambios de estado y notificaciones internas.
8. `@Version`, más pruebas con MockMvc, Flyway y PostgreSQL.
