import java.util.*;

public class Control {
    private final Inventario inventario;
    private final Auditoria auditoria;
    private final Validador validaciones;
    private final Map<String, Solicitud> ejecutadas = new HashMap<>();
    private final StockEventPublisher eventos = new StockEventPublisher();
    private PickingStrategy picking;
    private ReposicionStrategy reposicion;

    public Control(Inventario inventario, Auditoria auditoria,
                   PickingStrategy picking, ReposicionStrategy reposicion) {
        this.inventario = inventario; this.auditoria = auditoria;
        this.picking = picking; this.reposicion = reposicion;
        Validador sku = new ValidarSKU();
        sku.enlazar(new ValidarStock()).enlazar(new ValidarLote()).enlazar(new ValidarCapacidad());
        this.validaciones = sku;
    }
    public void suscribir(StockObserver observador) { eventos.suscribir(observador); }
    public void desuscribir(StockObserver observador) { eventos.desuscribir(observador); }
    public List<String> erroresNotificacion() { return eventos.errores(); }
    public void cambiarPicking(PickingStrategy estrategia) { this.picking = Objects.requireNonNull(estrategia); }
    public void cambiarReposicion(ReposicionStrategy estrategia) { this.reposicion = Objects.requireNonNull(estrategia); }
    public Recomendacion recomendar(DatosReposicion datos) { return reposicion.evaluar(datos); }
    public Lote elegirLote(String bodega, String sku, int cantidad) {
        return picking.elegir(inventario.buscar(bodega, sku), cantidad);
    }
    public synchronized String ejecutar(Command comando) {
        Solicitud s = comando.solicitud();
        try {
            if (ejecutadas.containsKey(s.idempotencia())) {
                if (!ejecutadas.get(s.idempotencia()).equals(s))
                    throw new IllegalArgumentException("Clave idempotente reutilizada con otra solicitud");
                auditoria.agregar(s, "DUPLICADO", "Sin volver a modificar stock");
                return "DUPLICADO: no se ejecuto nuevamente";
            }
            if (!comandoCoincide(comando, s.accion()))
                throw new IllegalArgumentException("Comando y accion no coinciden");
            validaciones.validar(s, inventario);
            comando.ejecutar(inventario);
            ejecutadas.put(s.idempotencia(), s);
            auditoria.agregar(s, "APROBADO", "Operacion realizada");
            publicarCambios(s);
            return "APROBADO: " + s.accion();
        } catch (RuntimeException e) {
            auditoria.agregar(s, "RECHAZADO", e.getMessage());
            return "RECHAZADO: " + e.getMessage();
        }
    }
    private void publicarCambios(Solicitud s) {
        publicarLote(s, s.origen(), s.loteId());
        if ("TRANSFERIR".equals(s.accion()))
            publicarLote(s, s.destino(), s.nuevoLoteId());
    }

    private void publicarLote(Solicitud s, String bodega, String id) {
        Lote lote = inventario.buscarLote(bodega, id);
        if (lote != null)
            eventos.publicar(new StockChanged(s.idempotencia(), s.sku(), bodega,
                id, s.accion(), lote.disponible(), lote.reservado(), s.actor(),
                s.tipoActor(), java.time.Instant.now()));
    }

    private boolean comandoCoincide(Command c, String accion) {
        return switch (accion) {
            case "RESERVAR" -> c instanceof ReservarCommand;
            case "LIBERAR" -> c instanceof LiberarCommand;
            case "AJUSTAR" -> c instanceof AjustarCommand;
            case "TRANSFERIR" -> c instanceof TransferirCommand;
            default -> false;
        };
    }
}
