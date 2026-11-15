import java.time.Instant;

public record StockChanged(String operacionId, String sku, String bodega,
                           String loteId, String accion, int disponible,
                           int reservado, String actor, String tipoActor,
                           Instant fecha) {}
