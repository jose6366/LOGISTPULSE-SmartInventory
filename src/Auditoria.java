import java.time.Instant;
import java.util.*;

record Registro(Instant fecha, String id, String actor, String tipoActor,
                String accion, String resultado, String detalle) {}
public class Auditoria {
    private final List<Registro> registros = new ArrayList<>();
    public void agregar(Solicitud s, String resultado, String detalle) {
        registros.add(new Registro(Instant.now(), s.idempotencia(), s.actor(),
            s.tipoActor(), s.accion(), resultado, detalle));
    }
    public List<Registro> historial() { return List.copyOf(registros); }
}
