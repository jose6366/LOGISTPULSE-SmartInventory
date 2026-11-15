import java.util.*;

public class Inventario {
    private final Map<String, List<Lote>> bodegas = new HashMap<>();
    private final Map<String, Integer> capacidades = new HashMap<>();

    public void agregarBodega(String id, int capacidad) {
        if (id == null || id.isBlank() || capacidad < 0 || bodegas.containsKey(id))
            throw new IllegalArgumentException("Bodega invalida");
        bodegas.put(id, new ArrayList<>());
        capacidades.put(id, capacidad);
    }

    public Set<String> listarBodegas() { return Set.copyOf(bodegas.keySet()); }
    public int capacidad(String id) { verificarBodega(id); return capacidades.get(id); }
    public List<Lote> lotesEnBodega(String id) {
        verificarBodega(id);
        return List.copyOf(bodegas.get(id));
    }

    public boolean existeBodega(String id) { return bodegas.containsKey(id); }

    public void agregarLote(String bodega, Lote lote) {
        verificarBodega(bodega);
        if (lote == null || buscarGlobal(lote.id()) != null)
            throw new IllegalArgumentException("Lote nulo o ID duplicado");
        if (ocupacion(bodega) + lote.total() > capacidades.get(bodega))
            throw new IllegalArgumentException("Capacidad insuficiente");
        bodegas.get(bodega).add(lote);
    }

    public List<Lote> buscar(String bodega, String sku) {
        verificarBodega(bodega);
        List<Lote> encontrados = new ArrayList<>();
        for (Lote lote : bodegas.get(bodega)) {
            if (lote.producto().sku().equals(sku)) encontrados.add(lote);
        }
        return encontrados;
    }

    public Lote buscarLote(String bodega, String id) {
        verificarBodega(bodega);
        for (Lote lote : bodegas.get(bodega)) {
            if (lote.id().equals(id)) return lote;
        }
        return null;
    }

    private Lote buscarGlobal(String id) {
        for (String bodega : bodegas.keySet()) {
            Lote lote = buscarLote(bodega, id);
            if (lote != null) return lote;
        }
        return null;
    }

    public boolean tieneSKU(String bodega, String sku) {
        return !buscar(bodega, sku).isEmpty();
    }

    public int ocupacion(String bodega) {
        verificarBodega(bodega);
        int total = 0;
        for (Lote lote : bodegas.get(bodega)) total += lote.total();
        return total;
    }

    public int capacidadLibre(String bodega) {
        verificarBodega(bodega);
        return capacidades.get(bodega) - ocupacion(bodega);
    }

    public void transferir(String origen, String destino, Lote lote, int cantidad, String nuevoId) {
        verificarBodega(origen);
        verificarBodega(destino);
        if (origen.equals(destino) || lote == null ||
            buscarLote(origen, lote.id()) != lote || cantidad <= 0 ||
            nuevoId == null || nuevoId.isBlank() || buscarGlobal(nuevoId) != null ||
            capacidadLibre(destino) < cantidad || lote.disponible() < cantidad || !lote.apto())
            throw new IllegalArgumentException("Transferencia no permitida");

        Lote nuevo = new Lote(nuevoId, lote.producto(), cantidad, lote.ingreso(), lote.vencimiento());
        lote.sacar(cantidad);
        nuevo.almacenar();
        bodegas.get(destino).add(nuevo);
    }

    private void verificarBodega(String bodega) {
        if (!existeBodega(bodega))
            throw new IllegalArgumentException("Bodega inexistente: " + bodega);
    }
}
