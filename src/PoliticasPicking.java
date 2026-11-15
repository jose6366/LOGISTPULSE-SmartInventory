import java.util.*;

abstract class PoliticaOrdenada implements PickingStrategy {
    protected abstract Comparator<Lote> orden();
    public Lote elegir(List<Lote> lotes, int cantidad) {
        if (cantidad <= 0) return null;
        return lotes.stream().filter(l -> l.apto() && l.disponible() >= cantidad)
            .min(orden()).orElse(null);
    }
}
class FEFO extends PoliticaOrdenada {
    protected Comparator<Lote> orden() { return Comparator.comparing(Lote::vencimiento); }
    public String nombre() { return "FEFO"; }
}
class FIFO extends PoliticaOrdenada {
    protected Comparator<Lote> orden() { return Comparator.comparing(Lote::ingreso); }
    public String nombre() { return "FIFO"; }
}
class LIFO extends PoliticaOrdenada {
    protected Comparator<Lote> orden() { return Comparator.comparing(Lote::ingreso).reversed(); }
    public String nombre() { return "LIFO"; }
}
