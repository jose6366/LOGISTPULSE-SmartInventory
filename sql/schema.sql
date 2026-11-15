-- PostgreSQL 15+. Patrón State con estado persistido y transiciones verificadas.
CREATE TABLE IF NOT EXISTS producto (
    sku VARCHAR(40) PRIMARY KEY,
    nombre VARCHAR(120) NOT NULL CHECK (length(trim(nombre)) > 0)
);
CREATE TABLE IF NOT EXISTS bodega (
    id VARCHAR(40) PRIMARY KEY,
    capacidad INTEGER NOT NULL CHECK (capacidad >= 0)
);
CREATE TABLE IF NOT EXISTS lote (
    id VARCHAR(60) PRIMARY KEY,
    sku VARCHAR(40) NOT NULL REFERENCES producto(sku),
    bodega_id VARCHAR(40) NOT NULL REFERENCES bodega(id),
    fecha_ingreso DATE NOT NULL,
    fecha_vencimiento DATE NOT NULL,
    disponible INTEGER NOT NULL DEFAULT 0 CHECK (disponible >= 0),
    reservado INTEGER NOT NULL DEFAULT 0 CHECK (reservado >= 0),
    estado VARCHAR(20) NOT NULL DEFAULT 'RECIBIDO',
    CONSTRAINT estado_lote_valido CHECK (estado IN (
        'RECIBIDO','ALMACENADO','RESERVADO','PICKEADO','ENVIADO','CUARENTENA')),
    CONSTRAINT invariantes_lote CHECK (
        (estado IN ('RECIBIDO','ALMACENADO') AND reservado = 0)
        OR (estado = 'RESERVADO' AND reservado > 0)
        OR (estado = 'PICKEADO' AND reservado > 0 AND disponible = 0)
        OR (estado = 'ENVIADO' AND reservado = 0 AND disponible = 0)
        OR (estado = 'CUARENTENA')
    ),
    CONSTRAINT fecha_lote_valida CHECK (fecha_vencimiento > fecha_ingreso)
);
CREATE INDEX IF NOT EXISTS idx_lote_busqueda ON lote(bodega_id, sku);
CREATE TABLE IF NOT EXISTS auditoria (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fecha TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    clave_idempotencia VARCHAR(120) NOT NULL,
    actor VARCHAR(120) NOT NULL,
    tipo_actor VARCHAR(10) NOT NULL CHECK (tipo_actor IN ('HUMANO', 'IA')),
    accion VARCHAR(20) NOT NULL CHECK (accion IN ('RESERVAR','LIBERAR','AJUSTAR','TRANSFERIR')),
    resultado VARCHAR(16) NOT NULL CHECK (resultado IN ('APROBADO','RECHAZADO','DUPLICADO')),
    detalle TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_auditoria_clave ON auditoria(clave_idempotencia);
CREATE TABLE IF NOT EXISTS operacion_ejecutada (
    clave_idempotencia VARCHAR(120) PRIMARY KEY,
    huella_solicitud TEXT NOT NULL,
    fecha TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
-- Guardrail adicional en SQL: impide exceder la capacidad total de la bodega,
-- incluso cuando se escribe directamente en la base de datos.
CREATE OR REPLACE FUNCTION comprobar_capacidad_bodega() RETURNS TRIGGER AS $$
DECLARE
    bodega_objetivo VARCHAR(40);
    limite INTEGER;
    ocupado BIGINT;
BEGIN
    bodega_objetivo := NEW.bodega_id;
    SELECT capacidad INTO limite FROM bodega WHERE id = bodega_objetivo FOR UPDATE;
    SELECT COALESCE(SUM(disponible + reservado), 0) INTO ocupado
      FROM lote WHERE bodega_id = bodega_objetivo;
    IF ocupado > limite THEN
        RAISE EXCEPTION 'Capacidad excedida en bodega %: % > %', bodega_objetivo, ocupado, limite;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_capacidad_lote ON lote;
CREATE CONSTRAINT TRIGGER trg_capacidad_lote
AFTER INSERT OR UPDATE ON lote DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION comprobar_capacidad_bodega();

-- Controla que el lote siga las mismas transiciones del autómata Java.
CREATE OR REPLACE FUNCTION validar_transicion_lote() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        IF OLD.estado = 'ENVIADO' THEN
            RAISE EXCEPTION 'Lote enviado es terminal';
        END IF;
        IF OLD.estado <> NEW.estado AND NOT (
            (OLD.estado = 'RECIBIDO' AND NEW.estado IN ('ALMACENADO','CUARENTENA')) OR
            (OLD.estado = 'ALMACENADO' AND NEW.estado IN ('RESERVADO','CUARENTENA')) OR
            (OLD.estado = 'RESERVADO' AND NEW.estado IN ('ALMACENADO','PICKEADO','CUARENTENA')) OR
            (OLD.estado = 'PICKEADO' AND NEW.estado IN ('ENVIADO','CUARENTENA')) OR
            (OLD.estado = 'CUARENTENA' AND NEW.estado IN ('ALMACENADO','RESERVADO'))
        ) THEN
            RAISE EXCEPTION 'Transicion invalida de % a %', OLD.estado, NEW.estado;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_transicion_lote ON lote;
CREATE TRIGGER trg_transicion_lote
BEFORE UPDATE ON lote FOR EACH ROW EXECUTE FUNCTION validar_transicion_lote();

-- Observer: registro transaccional de eventos StockChanged.
CREATE TABLE IF NOT EXISTS stock_evento (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    operacion_id VARCHAR(120) NOT NULL REFERENCES operacion_ejecutada(clave_idempotencia),
    sku VARCHAR(40) NOT NULL REFERENCES producto(sku),
    bodega_id VARCHAR(40) NOT NULL REFERENCES bodega(id),
    lote_id VARCHAR(60) NOT NULL REFERENCES lote(id),
    accion VARCHAR(20) NOT NULL CHECK (accion IN ('RESERVAR','LIBERAR','AJUSTAR','TRANSFERIR')),
    disponible INTEGER NOT NULL CHECK (disponible >= 0),
    reservado INTEGER NOT NULL CHECK (reservado >= 0),
    actor VARCHAR(120) NOT NULL,
    tipo_actor VARCHAR(10) NOT NULL CHECK (tipo_actor IN ('HUMANO','IA')),
    fecha TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_stock_evento_operacion ON stock_evento(operacion_id);
