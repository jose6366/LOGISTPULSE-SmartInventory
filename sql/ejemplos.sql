-- Ejemplos SQL (ejecutar después de schema.sql).
INSERT INTO producto(sku, nombre) VALUES ('SKU001','Arroz') ON CONFLICT (sku) DO NOTHING;
INSERT INTO bodega(id, capacidad) VALUES ('QUITO',500),('GUAYAQUIL',200)
ON CONFLICT (id) DO NOTHING;
INSERT INTO lote(id,sku,bodega_id,fecha_ingreso,fecha_vencimiento,disponible,reservado,estado)
VALUES ('L1','SKU001','QUITO','2026-01-10','2027-08-10',100,0,'ALMACENADO'),
       ('L2','SKU001','QUITO','2026-02-10','2027-03-10',80,0,'ALMACENADO')
ON CONFLICT (id) DO NOTHING;
SELECT id, sku, bodega_id, disponible, reservado, estado FROM lote;
SELECT fecha, actor, tipo_actor, accion, resultado, detalle FROM auditoria ORDER BY id;

SELECT operacion_id, sku, bodega_id, lote_id, accion FROM stock_evento ORDER BY id;
