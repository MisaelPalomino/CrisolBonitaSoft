#  Sistema de Gestión de Ventas - Librerías Crisol 

##  Equipo de Trabajo e Integrantes


| Rol en el Proyecto | Nombre Completo | Usuario GitHub |
| :--- | :--- | :--- |
| **Líder de Proyecto / BPM Architect** | [Nombre del Integrante 1] | [@usuario1](https://github.com/usuario1) |
| **Analista de Procesos / QA** | [Nombre del Integrante 2] | [@usuario2](https://github.com/usuario2) |
| **Desarrollador BDM / UI Designer**| [Nombre del Integrante 3] | [@usuario3](https://github.com/usuario3) |
| **Especialista de Integración** | [Nombre del Integrante 4] | [@usuario4](https://github.com/usuario4) |

---

## 🏢 Cliente / Organización

* **Organización:** Librerías Crisol S.A.
* **Sector:** Retail / Comercialización de Libros y Artículos Culturales.
* **Alcance del Laboratorio:** Optimización del flujo de consulta de inventario, atención en pasillo y transacciones en el Punto de Venta (POS).

---

## ⚙️ Proceso de Negocio Modelado

### `ProcesoVentasCrisol` (Versión 1.0.0)

Este proceso automatiza el ciclo de atención al cliente desde que se solicita un libro en los pasillos de la tienda hasta el cierre de la transacción financiera en la caja registradora. El ecosistema está diseñado bajo un estricto control de aislamiento de privilegios, dividiendo las responsabilidades mediante actores y grupos del sistema organizativo:

#### 🔍 Flujo de Operaciones:
1. **Fase de Consulta (Asesor de Tienda - `/acme/production`):** El asesor recibe el código de libro solicitado por el cliente e interactúa con el sistema mediante un formulario exclusivo para verificar el stock mediante el parámetro `isbnLibro`. Si existe disponibilidad, el flujo le asigna la tarea humana de recuperar el producto físicamente del estante.
2. **Fase de Facturación y Cierre (Cajero - `/acme/finance`):** Una vez que el libro llega a la estación de cobro, el terminal del cajero se activa automáticamente con la tarea de facturación. Se utiliza un formulario dinámico que valida campos obligatorios (*Required*) como el **DNI del Cliente**, el **Monto Total (S/.)**, y registra observaciones en el historial de la compra, impactando directamente en el Modelo de Datos de Negocio (BDM) de forma segura antes de emitir la compra exitosa.

---

## 🛠️ Tecnologías Utilizadas

* **BPMN Engine:** Bonita Studio v8.x
* **Database:** Bonita Business Data Model (BDM) / H2 Database Local
* **Interface:** Bonita UI Designer (Formularios Responsivos e Inyecciones de Contrato JSON)
* **Language Scripting:** Groovy Scripting Engine
