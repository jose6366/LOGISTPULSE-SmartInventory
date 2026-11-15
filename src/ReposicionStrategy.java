public interface ReposicionStrategy {
    Recomendacion evaluar(DatosReposicion d);
    String nombre();
}
record DatosReposicion(double demandaAnual, double costoPedido,
                       double costoMantenimientoAnualPorUnidad,
                       double demandaDiaria, int plazoEntregaDias,
                       int stockSeguridad, int posicionInventario) {}
record Recomendacion(String estrategia, String indicador, double valor,
                     String decision) {}
class EOQ implements ReposicionStrategy {
    public String nombre() { return "EOQ"; }
    public Recomendacion evaluar(DatosReposicion d) {
        if (d.demandaAnual() <= 0 || d.costoPedido() <= 0 || d.costoMantenimientoAnualPorUnidad() <= 0)
            throw new IllegalArgumentException("Parametros EOQ invalidos");
        double q = Math.sqrt(2 * d.demandaAnual() * d.costoPedido() /
                             d.costoMantenimientoAnualPorUnidad());
        return new Recomendacion(nombre(), "Cantidad optima de pedido (unidades)", q,
            "Lote economico sugerido: " + Math.ceil(q) + " unidades");
    }
}
class PuntoReorden implements ReposicionStrategy {
    public String nombre() { return "Punto de reorden"; }
    public Recomendacion evaluar(DatosReposicion d) {
        if (d.demandaDiaria() < 0 || d.plazoEntregaDias() < 0 || d.stockSeguridad() < 0 || d.posicionInventario() < 0)
            throw new IllegalArgumentException("Parametros ROP invalidos");
        double punto = d.demandaDiaria() * d.plazoEntregaDias() + d.stockSeguridad();
        String decision = d.posicionInventario() <= punto ? "Emitir pedido" : "Aun no reponer";
        return new Recomendacion(nombre(), "Umbral de reorden (unidades)", punto, decision);
    }
}
class MinMax implements ReposicionStrategy {
    private final int minimo, maximo;
    public MinMax(int minimo, int maximo) {
        if (minimo < 0 || maximo < minimo) throw new IllegalArgumentException("Min-Max invalido");
        this.minimo = minimo; this.maximo = maximo;
    }
    public String nombre() { return "Min-Max"; }
    public Recomendacion evaluar(DatosReposicion d) {
        int cantidad = d.posicionInventario() <= minimo ? maximo - d.posicionInventario() : 0;
        return new Recomendacion(nombre(), "Cantidad a pedir (unidades)", cantidad,
            cantidad > 0 ? "Emitir pedido" : "Aun no reponer");
    }
}
