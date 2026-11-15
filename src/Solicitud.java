public record Solicitud(String idempotencia, String actor, String tipoActor,
                        String accion, String origen, String destino,
                        String sku, String loteId, int cantidad,
                        String nuevoLoteId) {
    public Solicitud {
        if (idempotencia == null || idempotencia.isBlank() || actor == null || actor.isBlank()
            || !("HUMANO".equals(tipoActor) || "IA".equals(tipoActor)))
            throw new IllegalArgumentException("Identidad o clave de idempotencia invalida");
    }
}
