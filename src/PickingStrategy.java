import java.util.List;
public interface PickingStrategy {
    Lote elegir(List<Lote> lotes, int cantidad);
    String nombre();
}
