import java.util.ArrayList;
import java.util.List;

public class StockEventPublisher {
    private final List<StockObserver> observadores = new ArrayList<>();
    private final List<String> erroresNotificacion = new ArrayList<>();

    public synchronized void suscribir(StockObserver observador) {
        if (observador == null) throw new IllegalArgumentException("Observador nulo");
        if (!observadores.contains(observador)) observadores.add(observador);
    }

    public synchronized void desuscribir(StockObserver observador) {
        observadores.remove(observador);
    }

    public void publicar(StockChanged evento) {
        List<StockObserver> copia;
        synchronized (this) { copia = List.copyOf(observadores); }
        for (StockObserver observador : copia) {
            try {
                observador.actualizar(evento);
            } catch (RuntimeException e) {
                synchronized (this) {
                    erroresNotificacion.add(observador.getClass().getSimpleName() + ": " + e.getMessage());
                }
            }
        }
    }

    public synchronized List<String> errores() { return List.copyOf(erroresNotificacion); }
}
