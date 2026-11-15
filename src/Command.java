public interface Command {
    Solicitud solicitud();
    void ejecutar(Inventario inventario);
}
