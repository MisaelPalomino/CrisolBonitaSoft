#  Sistema de Gestión de Ventas - Librerías Crisol 

##  Equipo de Trabajo e Integrantes

Integrantes
-Misael Palomino
-Jose Alva
-Arthur Meza
-Sebastian Himan
-Gonzalo Zapana

---

## 🏢 Cliente / Organización

* **Organización:** Librerías Crisol S.A.
* **Sector:** Retail / Comercialización de Libros y Artículos Culturales.
* **Alcance del Laboratorio:** Optimización del flujo de consulta de inventario, atención en pasillo y transacciones en el Punto de Venta (POS).

---

## Introducción
En el contexto actual, las organizaciones requieren comprender de manera integral su estructura y funcionamiento para mejorar la toma de decisiones y optimizar sus procesos. La arquitectura empresarial permite analizar de forma sistemática los componentes clave de una empresa, tales como sus procesos de negocio, áreas funcionales, información y objetivos estratégicos.
El presente reporte tiene como objetivo describir y analizar la arquitectura empresarial de la empresa Librerías Crisol S.A.C., identificando sus principales procesos de negocio, los sectores involucrados y los elementos de información necesarios para su correcto funcionamiento. A partir de la información disponible y del análisis del dominio del problema, se busca obtener una visión clara de cómo la organización opera y cómo sus diferentes componentes se integran para cumplir con sus objetivos.

### Misión
Somos la cadena de librerías con la oferta más amplia y variada de productos de entretenimiento cultural, brindando el mejor nivel de servicio en el mercado peruano. Nos caracterizamos por los altos estándares de respeto, calidez y amabilidad de nuestros colaboradores.

### Visión

Ser la principal cadena de tiendas de entretenimiento cultural ampliando nuestra red de librerías hacia todas las regiones del país, y de esta manera, convertirnos progresivamente en referente cultural de América Latina.

## Organigrama

<img width="435" height="325" alt="image" src="https://github.com/user-attachments/assets/675d86ae-dc53-478e-912e-29636af95c6a" />

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
