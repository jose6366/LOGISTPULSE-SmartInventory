abstract class ComandoBase implements Command {
    private final Solicitud solicitud;
    protected ComandoBase(Solicitud solicitud) { this.solicitud = solicitud; }
    public Solicitud solicitud() { return solicitud; }
    protected Lote lote(Inventario i) { return i.buscarLote(solicitud.origen(), solicitud.loteId()); }
}
class ReservarCommand extends ComandoBase {
    public ReservarCommand(Solicitud s) { super(s); }
    public void ejecutar(Inventario i) { lote(i).reservar(solicitud().cantidad()); }
}
class LiberarCommand extends ComandoBase {
    public LiberarCommand(Solicitud s) { super(s); }
    public void ejecutar(Inventario i) { lote(i).liberar(solicitud().cantidad()); }
}
class AjustarCommand extends ComandoBase {
    public AjustarCommand(Solicitud s) { super(s); }
    public void ejecutar(Inventario i) { lote(i).ajustar(solicitud().cantidad()); }
}
class TransferirCommand extends ComandoBase {
    public TransferirCommand(Solicitud s) { super(s); }
    public void ejecutar(Inventario i) {
        i.transferir(solicitud().origen(), solicitud().destino(), lote(i),
            solicitud().cantidad(), solicitud().nuevoLoteId());
    }
}
