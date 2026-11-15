public interface LoteState {
    String nombre();
    default void almacenar(Lote lote) { throw new IllegalStateException("No se puede almacenar desde " + nombre()); }
    default void reservar(Lote lote, int cantidad) { throw new IllegalStateException("No se puede reservar desde " + nombre()); }
    default void liberar(Lote lote, int cantidad) { throw new IllegalStateException("No se puede liberar desde " + nombre()); }
    default void ajustar(Lote lote, int diferencia) { throw new IllegalStateException("No se puede ajustar desde " + nombre()); }
    default void sacar(Lote lote, int cantidad) { throw new IllegalStateException("No se puede transferir desde " + nombre()); }
    default void pickear(Lote lote) { throw new IllegalStateException("No se puede pickear desde " + nombre()); }
    default void enviar(Lote lote) { throw new IllegalStateException("No se puede enviar desde " + nombre()); }
    default void cuarentena(Lote lote) { throw new IllegalStateException("No se puede poner en cuarentena desde " + nombre()); }
    default void rehabilitar(Lote lote) { throw new IllegalStateException("No se puede rehabilitar desde " + nombre()); }
}
