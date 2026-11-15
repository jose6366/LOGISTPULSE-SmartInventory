# LOGISTPULSE — Smart Inventory (Command + Chain + Strategy + State + Observer)

## Alcance estricto
Cinco patrones de comportamiento de la presentación:
1. **Command:** `ReservarCommand`, `LiberarCommand`, `AjustarCommand`, `TransferirCommand`. Las acciones se identifican por clave de idempotencia y dejan auditoría de humano o agente IA.
2. **Chain of Responsibility:** `ValidarSKU` → `ValidarStock` → `ValidarLote` → `ValidarCapacidad`. La misma cadena se usa sin importar `tipoActor`.
3. **Strategy:** picking FEFO/FIFO/LIFO, reposición EOQ/punto de reorden/Min-Max; cambios en ejecución.
4. **State:** máquina de estados del lote: recibido → almacenado → reservado → pickeado → enviado, con desvíos a cuarentena. Solo puede pickearse un lote completamente reservado; enviar consume esas unidades.

5. **Observer:** al aprobar un comando se publica `StockChanged` hacia `ReposicionObserver`, `PulseFleetObserver` y `LedgerObserver`, sin acoplar los comandos a esos consumidores. Una transferencia genera un evento por lote afectado. Rechazos y duplicados no generan nuevos eventos.

**State:** `Lote` delega en clases de estado (`Recibido`, `Almacenado`, `Reservado`, `Pickeado`, `Enviado`, `Cuarentena`). SQL guarda el estado con `CHECK` para invariantes y trigger para transiciones permitidas. **No se implementa Template Method.**

## Archivos
- `src/Control.java`: coordina Command, Chain y Strategy en memoria, publica Observer y no imprime. `Lote` aplica State.
- `src/ControlSql.java`: utiliza los mismos comandos, validaciones y estrategias contra PostgreSQL mediante JDBC, en transacciones.
- `src/Main.java`: ejemplo ejecutable en memoria sin dependencias; muestra Observer.
- `src/MainSql.java`: ejemplo de reservas en PostgreSQL.
- `sql/schema.sql`: tablas producto, bodega, lote, auditoría, operación idempotente, restricciones CHECK, transición de estados, capacidad por bodega y tabla `stock_evento`.
- `sql/ejemplos.sql`: datos de demostración.
- `docker-compose.yml`: servidor PostgreSQL para demostración.

## Demostración sin base de datos (Java 17+)
```bash
cd src
javac *.java
java Main
```

## Demostración real con PostgreSQL
1. Desde la raíz del proyecto, iniciar `docker compose up -d`. Docker carga los scripts SQL *en la primera creación de la base*. Si ya hay una base previa, ejecutar `schema.sql` y `ejemplos.sql` manualmente.
2. Descargar el driver PostgreSQL JDBC compatible con tu versión de Java desde https://jdbc.postgresql.org/download/ y guardarlo como `postgresql.jar` en la raíz (el JAR no se incluye).
3. Compilar y ejecutar desde la raíz:

**Windows**
```bat
javac src\*.java
java -cp "src;postgresql.jar" MainSql
```
**macOS/Linux**
```bash
javac src/*.java
java -cp "src:postgresql.jar" MainSql
```

La conexión por defecto es `jdbc:postgresql://localhost:5432/logistpulse`, usuario y clave `logistpulse`. Puedes cambiar `DB_URL`, `DB_USER` y `DB_PASSWORD` usando variables de entorno.

Ejemplo de consulta SQL:
```sql
SELECT id, disponible, reservado, estado FROM lote ORDER BY id;
SELECT actor, tipo_actor, accion, resultado FROM auditoria ORDER BY id;
```

## Notas de diseño
- La reserva incrementa `reservado` y reduce `disponible`, sin consumir el total físico. La liberación hace lo contrario. Ajustar modifica unidades disponibles; transferir mueve unidades disponibles a otro lote/bodega.
- La capacidad se mide en unidades, no en metros cúbicos. La cadena valida capacidad de la bodega destino; un trigger SQL protege ese límite en la persistencia.
- Los `CHECK` de SQL protegen cantidades no negativas, fechas, estados e invariantes. Un trigger valida transiciones de estado; las clases Java son la implementación del patrón State.
- **EOQ** (`sqrt(2DS/H)`) devuelve cantidad óptima por pedido; **punto de reorden** (`dL + stockSeguridad`) devuelve umbral de activación. Son indicadores distintos, no magnitudes directamente intercambiables.
- En `ControlSql`, el registro de la operación, auditoría, eventos `stock_evento` y cambios del stock se confirman juntos dentro de una transacción; la clave idempotente se conserva entre reinicios.
- Es un ejemplo educativo. No hay autenticación real de identidades, ni ejecución automática de IA, ni manejo avanzado de reintentos por fallas de serialización.

## Observer: evento de inventario
- `StockChanged` identifica operación, SKU, bodega, lote, acción, cantidades finales, actor y fecha.
- `StockEventPublisher` permite suscribir y desuscribir consumidores. Captura fallos individuales para no interrumpir el resto de notificaciones.
- `ReposicionObserver` genera avisos de reposición cuando el disponible del lote queda bajo un umbral **didáctico por lote** (no sustituye la estrategia de reposición por SKU).
- `PulseFleetObserver` simula la recepción de eventos que luego pueden integrarse con PULSEFLEET.
- `LedgerObserver` guarda un historial de eventos en memoria, separado de la auditoría de comandos. Los eventos SQL se persisten en `stock_evento`.
- En PostgreSQL, los eventos se insertan en la **misma transacción** de la operación y se notifican a observadores Java **después del commit**. Esto no es entrega garantizada entre procesos: para reintentos/distribución se necesitaría un despachador de outbox, que queda fuera del alcance.
- Las transiciones directas de `Lote` en el ejemplo State no generan `StockChanged`: Observer está conectado al flujo de comandos de inventario.

Consulta SQL:
```sql
SELECT operacion_id, sku, bodega_id, lote_id, accion, disponible, reservado
FROM stock_evento ORDER BY id;
```

## Autómata de lotes
- `RECIBIDO` → `ALMACENADO` o `CUARENTENA`.
- `ALMACENADO` → `RESERVADO` o `CUARENTENA`.
- `RESERVADO` → `ALMACENADO` (al liberar la última unidad), `PICKEADO` o `CUARENTENA`.
- `PICKEADO` → `ENVIADO` o `CUARENTENA`.
- `CUARENTENA` → `ALMACENADO` o `RESERVADO` después de rehabilitar.
- `ENVIADO` es terminal.

Para demostrar `State`, `Main` crea un lote `L3`, lo almacena, reserva por completo, pickea y envía. Al pasar a `ENVIADO` quedan 0 unidades en el lote. Para la demostración, el picking y envío de lotes completos se ejecutan mediante los métodos del lote; no se han creado patrones adicionales.

**Migración de base de datos:** el esquema actualizado está pensado para una base nueva. Si conservas el volumen anterior, migra manualmente el antiguo campo `cuarentena` a `estado` o recrea la base de demostración (esto borra sus datos).
