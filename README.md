# Gestión de Inventario Crisol en Bonita

Proyecto Bonita que modela el proceso de gestión de inventario de Librerías
Crisol y lo integra de forma asíncrona con RabbitMQ y el servicio Django
`SistemaAlmacenamientoCrisol`.

## Estado de la rama

Este documento corresponde a la rama `feature/rabbitmq-inventario`.

| Avance | Estado | Evidencia principal |
|--------|--------|---------------------|
| Proceso BPMN `Proceso_Gestion_Inventario` | Completado | `app/diagrams/GestionInventario-1.0.proc` |
| Publicación de consultas en RabbitMQ | Completado | Conector de entrada de `Monitorear nivel de stock` |
| Consumo correlacionado de respuestas | Completado | Conector de salida de `Monitorear nivel de stock` |
| Actualización de la variable `stockBajo` | Completado | Salida del conector mapeada a la variable del proceso |
| Conector personalizado | Completado | `extensions/connector-rabbitmq-inventario/` |
| Prueba Bonita → RabbitMQ → Django → Bonita | Validada | Casos de inventario completados en el runtime local |
| Página `inventarioDashboard` | Completado | `app/web_page/inventarioDashboard/` |
| Living Application | Completada y desplegada | `app/applications/gestionInventarioApplication.xml` |
| Enlaces a procesos, casos y tareas | Completados | Página de inicio de la aplicación |
| Notificación SMTP de stock bajo | Configurada en Studio; pendiente de guardar y probar | Conector de correo en `Generar solicitud de reposición` |

El backend integrado se encuentra en
[`MisaelPalomino/SistemaAlmacenamientoCrisol`](https://github.com/MisaelPalomino/SistemaAlmacenamientoCrisol),
rama acumulativa `feature/openapi-swagger`.

## Arquitectura de integración

```text
Bonita: Monitorear nivel de stock
        │
        │ JSON: inventario.stock_bajo.consultar
        │ correlation_id único
        ▼
RabbitMQ: crisol.inventario.request
        │
        ▼
Django: inventario_consumer
        │
        ├── ProductoService.generar_alerta_stock_bajo()
        ├── ack del mensaje válido
        └── publica la respuesta conservando correlation_id
        ▼
RabbitMQ: crisol.inventario.response
        │
        ▼
Bonita: Consumir respuesta de inventario
        │
        ├── actualiza stockBajo
        └── continúa hacia ¿Stock bajo?
                 ├── No → Fin sin reposición
                 └── Sí → Generar solicitud de reposición
                            └── Notificación SMTP
```

## Componentes implementados

### Proceso de Gestión de Inventario

El proceso principal es `Proceso_Gestion_Inventario`, definido en
`app/diagrams/GestionInventario-1.0.proc`.

En la tarea de servicio `Monitorear nivel de stock` se configuraron dos
conectores:

1. `publicarConsultaStockBajoRabbitMQ`, ejecutado en `ON_ENTER`.
2. `consumirRespuestaStockBajoRabbitMQ`, ejecutado en `ON_FINISH`.

La salida booleana `stockBajo` del segundo conector se asigna a la variable de
proceso `stockBajo`. La compuerta `¿Stock bajo?` utiliza esa variable para
decidir si termina el caso o inicia la reposición.

También se creó la variable de proceso `correlationIdInventario`, utilizada por
ambos conectores para asociar cada solicitud con su respuesta.

### Conector RabbitMQ personalizado

Código fuente:

```text
extensions/connector-rabbitmq-inventario/
```

El artefacto contiene dos definiciones:

- `Publicar consulta de inventario en RabbitMQ`.
- `Consumir respuesta de inventario desde RabbitMQ`.

El JAR se compila con Java 17:

```powershell
cd extensions\connector-rabbitmq-inventario
mvn clean package
```

Artefacto generado:

```text
target/connector-rabbitmq-inventario-1.0.1-SNAPSHOT.jar
```

### Contrato de solicitud

Cola durable:

```text
crisol.inventario.request
```

Mensaje:

```json
{
  "tipo": "inventario.stock_bajo.consultar"
}
```

### Contrato de respuesta

Cola durable:

```text
crisol.inventario.response
```

Ejemplo de respuesta:

```json
{
  "tipo": "inventario.stock_bajo.respuesta",
  "datos": {
    "alerta": true,
    "mensaje": "Productos con stock bajo detectados",
    "total": 1,
    "productos": [
      {
        "id": 1,
        "nombre": "El Principito",
        "isbn": "9786120012345",
        "stock_actual": 2,
        "stock_minimo": 5,
        "categoria": "LITERATURA"
      }
    ]
  }
}
```

La propiedad AMQP `correlation_id` de la respuesta debe ser idéntica a la de la
solicitud.

## Living Application

La aplicación se encuentra en:

```text
app/applications/gestionInventarioApplication.xml
```

Configuración:

| Propiedad | Valor |
|-----------|-------|
| Nombre | Gestión de Inventario Crisol |
| Token de aplicación | `crisol-inventario` |
| Perfil | `User` |
| Página inicial | `inicio` |
| Página UI Designer | `inventarioDashboard` |

URL local:

```text
http://localhost:8080/bonita/apps/crisol-inventario/inicio
```

La página muestra tres accesos rápidos:

- Iniciar proceso de inventario.
- Ver casos de inventario.
- Ver tareas pendientes.

Los enlaces utilizan las rutas de `Bonita User Application` y fueron validados
en el runtime local.

## Notificación por correo

Se preparó el conector oficial `Correo electrónico (SMTP)` para la tarea
`Generar solicitud de reposición`. Se ejecuta cuando la rama
`stockBajo == true` llega a esa tarea.

Configuración local utilizada con Gmail:

| Campo | Valor |
|-------|-------|
| Host | `smtp.gmail.com` |
| Puerto | `465` |
| SSL | Activado |
| STARTTLS | Desactivado |
| Autenticación | Básica con contraseña de aplicación |

Las credenciales no deben escribirse directamente en el proceso. Los campos
del conector obtienen sus valores mediante expresiones Groovy:

```groovy
System.getenv("CRISOL_SMTP_USER")
System.getenv("CRISOL_SMTP_APP_PASSWORD")
System.getenv("CRISOL_ALERT_RECIPIENT")
```

Variables requeridas:

| Variable | Descripción |
|----------|-------------|
| `CRISOL_SMTP_USER` | Cuenta remitente de Gmail |
| `CRISOL_SMTP_APP_PASSWORD` | Contraseña de aplicación de Google |
| `CRISOL_ALERT_RECIPIENT` | Dirección que recibirá la alerta |

Ejemplo seguro para iniciar Bonita desde PowerShell:

```powershell
$env:CRISOL_SMTP_USER = "remitente@gmail.com"
$env:CRISOL_ALERT_RECIPIENT = "destinatario@gmail.com"

$claveSegura = Read-Host "Contraseña de aplicación de Gmail" -AsSecureString
$claveTemporal = [System.Net.NetworkCredential]::new("", $claveSegura).Password
$env:CRISOL_SMTP_APP_PASSWORD = $claveTemporal.Replace(" ", "")

Start-Process "C:\BonitaStudioCommunity-2025.2-u0\BonitaStudioCommunity.exe"
```

No se debe subir al repositorio una dirección privada, una contraseña normal de
Gmail ni la contraseña de aplicación.

## Ejecución completa

### 1. Iniciar RabbitMQ

Comprobar:

- AMQP: `localhost:5672`.
- Panel de administración: `http://localhost:15672`.
- Colas `crisol.inventario.request` y `crisol.inventario.response`.

### 2. Iniciar el consumidor Django

Desde el repositorio `SistemaAlmacenamientoCrisol`:

```powershell
.\.venv\Scripts\Activate.ps1
python manage.py consumir_inventario
```

Resultado esperado:

```text
Esperando mensajes en la cola crisol.inventario.request
```

### 3. Ejecutar Bonita

1. Abrir `GestionInventario-1.0.proc`.
2. Configurar los actores del proceso con usuarios del entorno local.
3. Ejecutar o desplegar `Proceso_Gestion_Inventario`.
4. Completar las tareas humanas hasta llegar a
   `Monitorear nivel de stock`.
5. Verificar que Django registre una consulta procesada con su
   `correlation_id`.
6. Comprobar que Bonita continúe por la salida correcta de
   `¿Stock bajo?`.
7. Si existe stock bajo, verificar la recepción del correo de alerta.

## Despliegue de la aplicación

Para desplegar todo en un runtime limpio, seleccionar:

- Organización.
- Modelo de Datos de Negocio.
- `Proceso_Gestion_Inventario`.
- `gestionInventarioApplication.xml`.
- Página `inventarioDashboard`.

Cuando otros diagramas del repositorio tengan errores de formularios o actores,
deben quedar desmarcados. Esos errores no forman parte del alcance de esta
rama.

## Estructura relevante

```text
proceso-de-atencion-al-cliente/
├── app/
│   ├── applications/
│   │   └── gestionInventarioApplication.xml
│   ├── diagrams/
│   │   └── GestionInventario-1.0.proc
│   ├── web_page/
│   │   └── inventarioDashboard/
│   └── pom.xml
├── bdm/
│   └── bom.xml
├── extensions/
│   └── connector-rabbitmq-inventario/
└── README.md
```

## Verificación realizada

- El conector publicador genera un `correlation_id`.
- Django recibe y procesa la consulta de stock bajo.
- La respuesta conserva el mismo `correlation_id`.
- Bonita consume la respuesta y actualiza `stockBajo`.
- Se completó un caso de `Proceso_Gestion_Inventario`.
- La Living Application se desplegó correctamente.
- Los tres enlaces del dashboard funcionan.
- La configuración SMTP utiliza variables de entorno y no contiene
  credenciales escritas directamente.
- Queda pendiente guardar el diagrama y validar la entrega real del correo.

## Recomendaciones para Git

Antes de crear el commit, guardar el diagrama en Bonita Studio y comprobar que
`app/diagrams/GestionInventario-1.0.proc` aparezca modificado.

No utilizar `git add .` porque UI Designer genera numerosos cambios temporales.
Preparar únicamente los archivos relacionados con el avance:

```powershell
git add README.md
git add app/diagrams/GestionInventario-1.0.proc
git add app/pom.xml
git add app/applications/gestionInventarioApplication.xml
git add app/web_page/inventarioDashboard
git add extensions/connector-rabbitmq-inventario
```

No preparar:

```text
app/dependencies/uid-tmp/
target/
*.log
credenciales o contraseñas
```
