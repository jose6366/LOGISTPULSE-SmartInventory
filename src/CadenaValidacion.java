import java.time.LocalDate;

abstract class Validador {
    private Validador siguiente;
    public Validador enlazar(Validador otro) { siguiente = otro; return otro; }
    public final void validar(Solicitud s, Inventario i) {
        comprobar(s, i);
        if (siguiente != null) siguiente.validar(s, i);
    }
    protected abstract void comprobar(Solicitud s, Inventario i);
}
class ValidarSKU extends Validador {
    protected void comprobar(Solicitud s, Inventario i) {
        if (s.sku() == null || s.sku().isBlank() || !i.tieneSKU(s.origen(), s.sku()))
            throw new IllegalArgumentException("SKU no registrado en origen");
        if (s.loteId() == null || i.buscarLote(s.origen(), s.loteId()) == null
            || !i.buscarLote(s.origen(), s.loteId()).producto().sku().equals(s.sku()))
            throw new IllegalArgumentException("Lote no pertenece al SKU");
    }
}
class ValidarStock extends Validador {
    protected void comprobar(Solicitud s, Inventario i) {
        Lote l = i.buscarLote(s.origen(), s.loteId());
        if (s.cantidad() == 0) throw new IllegalArgumentException("Cantidad cero");
        if (s.accion().equals("RESERVAR") || s.accion().equals("TRANSFERIR")
            || (s.accion().equals("AJUSTAR") && s.cantidad() < 0)) {
            if (l.disponible() < Math.abs(s.cantidad())) throw new IllegalArgumentException("Stock insuficiente");
        }
        if (s.accion().equals("LIBERAR") && l.reservado() < s.cantidad())
            throw new IllegalArgumentException("Cantidad no reservada");
        if (!s.accion().equals("AJUSTAR") && s.cantidad() < 0)
            throw new IllegalArgumentException("Cantidad negativa");
    }
}
class ValidarLote extends Validador {
    protected void comprobar(Solicitud s, Inventario i) {
        Lote l = i.buscarLote(s.origen(), s.loteId());
        if (s.accion().equals("LIBERAR")) {
            if (l.reservado() <= 0) throw new IllegalArgumentException("Lote sin reservas");
        } else if (!l.apto()) {
            throw new IllegalArgumentException("Lote vencido, en cuarentena o en estado no operativo");
        }
    }
}
class ValidarCapacidad extends Validador {
    protected void comprobar(Solicitud s, Inventario i) {
        if (s.accion().equals("TRANSFERIR")) {
            if (s.destino() == null || !i.existeBodega(s.destino()) || s.destino().equals(s.origen()))
                throw new IllegalArgumentException("Bodega destino invalida");
            if (i.capacidadLibre(s.destino()) < s.cantidad())
                throw new IllegalArgumentException("Capacidad insuficiente en destino");
        }
        if (s.accion().equals("AJUSTAR") && s.cantidad() > 0
            && i.capacidadLibre(s.origen()) < s.cantidad())
            throw new IllegalArgumentException("Capacidad insuficiente en origen");
    }
}
