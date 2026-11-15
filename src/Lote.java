import java.time.LocalDate;

public class Lote {
    private final String id;
    private final Producto producto;
    private final LocalDate ingreso;
    private final LocalDate vencimiento;
    private int disponible;
    private int reservado;
    private LoteState estado;

    public Lote(String id, Producto producto, int cantidad, LocalDate ingreso, LocalDate vencimiento) {
        if (id == null || id.isBlank() || producto == null || cantidad < 0 ||
            ingreso == null || vencimiento == null || !vencimiento.isAfter(ingreso))
            throw new IllegalArgumentException("Datos de lote invalidos");
        this.id = id;
        this.producto = producto;
        this.disponible = cantidad;
        this.ingreso = ingreso;
        this.vencimiento = vencimiento;
        this.estado = EstadosLote.RECIBIDO;
    }

    public String id() { return id; }
    public Producto producto() { return producto; }
    public LocalDate ingreso() { return ingreso; }
    public LocalDate vencimiento() { return vencimiento; }
    public int disponible() { return disponible; }
    public int reservado() { return reservado; }
    public int total() { return disponible + reservado; }
    public String estado() { return estado.nombre(); }
    public boolean cuarentena() { return estado == EstadosLote.CUARENTENA; }
    public boolean apto() {
        return (estado == EstadosLote.ALMACENADO || estado == EstadosLote.RESERVADO)
            && vencimiento.isAfter(LocalDate.now());
    }

    public void almacenar() { estado.almacenar(this); }
    public void reservar(int n) {
        if (n <= 0 || disponible < n || !apto()) throw new IllegalArgumentException("Reserva no permitida");
        estado.reservar(this, n);
    }
    public void liberar(int n) {
        if (n <= 0 || reservado < n) throw new IllegalArgumentException("Liberacion no permitida");
        estado.liberar(this, n);
    }
    public void ajustar(int diferencia) {
        if (diferencia == 0 || (long) disponible + diferencia < 0 || !apto())
            throw new IllegalArgumentException("Ajuste no permitido");
        estado.ajustar(this, diferencia);
    }
    public void sacar(int n) {
        if (n <= 0 || disponible < n || !apto()) throw new IllegalArgumentException("Transferencia no permitida");
        estado.sacar(this, n);
    }
    public void pickear() { estado.pickear(this); }
    public void enviar() { estado.enviar(this); }
    public void ponerEnCuarentena() { estado.cuarentena(this); }
    public void rehabilitar() { estado.rehabilitar(this); }

    void cambiarEstado(LoteState nuevo) { estado = nuevo; }
    void descontarDisponible(int n) { disponible -= n; }
    void aumentarDisponible(int n) { disponible += n; }
    void modificarDisponible(int delta) { disponible += delta; }
    void aumentarReservado(int n) { reservado += n; }
    void descontarReservado(int n) { reservado -= n; }
    void consumirReservado() { reservado = 0; }

    void restaurarExistencias(int disponibles, int reservados) {
        if (disponibles < 0 || reservados < 0) throw new IllegalArgumentException("Existencias persistidas invalidas");
        disponible = disponibles;
        reservado = reservados;
    }
    void restaurarEstado(String nombre) {
        LoteState nuevo = EstadosLote.desde(nombre);
        if ((nuevo == EstadosLote.ALMACENADO && reservado != 0) ||
            ((nuevo == EstadosLote.RESERVADO || nuevo == EstadosLote.PICKEADO) && reservado <= 0) ||
            (nuevo == EstadosLote.PICKEADO && disponible != 0) ||
            (nuevo == EstadosLote.ENVIADO && total() != 0))
            throw new IllegalArgumentException("Estado y existencias inconsistentes");
        estado = nuevo;
    }
}
