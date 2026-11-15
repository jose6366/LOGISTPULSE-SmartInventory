import java.util.ArrayList;
import java.util.List;

class ReposicionObserver implements StockObserver {
    private final List<StockChanged> recibidos = new ArrayList<>();
    private final List<String> avisos = new ArrayList<>();
    private final int umbral;

    public ReposicionObserver(int umbral) { this.umbral = umbral; }

    public void actualizar(StockChanged evento) {
        recibidos.add(evento);
        if (evento.disponible() <= umbral)
            avisos.add("Revisar reposicion de " + evento.sku() + " en " + evento.bodega());
    }
    public List<StockChanged> recibidos() { return List.copyOf(recibidos); }
    public List<String> avisos() { return List.copyOf(avisos); }
}

class PulseFleetObserver implements StockObserver {
    private final List<StockChanged> eventos = new ArrayList<>();
    public void actualizar(StockChanged evento) { eventos.add(evento); }
    public List<StockChanged> eventos() { return List.copyOf(eventos); }
}

class LedgerObserver implements StockObserver {
    private final List<StockChanged> movimientos = new ArrayList<>();
    public void actualizar(StockChanged evento) { movimientos.add(evento); }
    public List<StockChanged> movimientos() { return List.copyOf(movimientos); }
}
