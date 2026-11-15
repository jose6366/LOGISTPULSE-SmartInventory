import java.sql.*;
import java.time.LocalDate;
import java.util.*;

// Adaptador JDBC: conserva Command, Chain, Strategy y el estado State de los lotes.
// Cada comando ejecuta validaciones y escritura de stock/auditoria en una transaccion SQL.
public class ControlSql {
    private final String url;
    private final String usuario;
    private final String clave;
    private final StockEventPublisher eventos = new StockEventPublisher();
    private PickingStrategy picking;
    private ReposicionStrategy reposicion;

    public ControlSql(String url, String usuario, String clave,
                      PickingStrategy picking, ReposicionStrategy reposicion) {
        this.url = Objects.requireNonNull(url);
        this.usuario = Objects.requireNonNull(usuario);
        this.clave = Objects.requireNonNull(clave);
        this.picking = Objects.requireNonNull(picking);
        this.reposicion = Objects.requireNonNull(reposicion);
    }

    public void suscribir(StockObserver observador) { eventos.suscribir(observador); }
    public void desuscribir(StockObserver observador) { eventos.desuscribir(observador); }
    public List<String> erroresNotificacion() { return eventos.errores(); }
    public void cambiarPicking(PickingStrategy s) { picking = Objects.requireNonNull(s); }
    public void cambiarReposicion(ReposicionStrategy s) { reposicion = Objects.requireNonNull(s); }
    public Recomendacion recomendar(DatosReposicion d) { return reposicion.evaluar(d); }

    public Lote elegirLote(String bodega, String sku, int cantidad) throws SQLException {
        try (Connection c = conexion()) {
            return picking.elegir(cargar(c).buscar(bodega, sku), cantidad);
        }
    }

    private Connection conexion() throws SQLException {
        return DriverManager.getConnection(url, usuario, clave);
    }

    public synchronized String ejecutar(Command comando) throws SQLException {
        Solicitud s = Objects.requireNonNull(comando).solicitud();
        try (Connection c = conexion()) {
            c.setAutoCommit(false);
            c.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            try {
                // Bloquea las bodegas en orden estable para serializar comandos de inventario.
                try (Statement st = c.createStatement();
                     ResultSet rs = st.executeQuery("SELECT id FROM bodega ORDER BY id FOR UPDATE")) {
                    while (rs.next()) { /* consumir bloqueos */ }
                }

                String huella = huella(s);
                String previa = obtenerHuella(c, s.idempotencia());
                if (previa != null) {
                    String respuesta;
                    if (previa.equals(huella)) {
                        auditar(c, s, "DUPLICADO", "No se modifica stock nuevamente");
                        respuesta = "DUPLICADO: no se ejecuto nuevamente";
                    } else {
                        auditar(c, s, "RECHAZADO", "Clave reutilizada para otra solicitud");
                        respuesta = "RECHAZADO: clave idempotente reutilizada";
                    }
                    c.commit();
                    return respuesta;
                }

                Inventario inventario = cargar(c);
                Control control = new Control(inventario, new Auditoria(), picking, reposicion);
                String respuesta = control.ejecutar(comando);
                List<StockChanged> cambios = new ArrayList<>();
                if (respuesta.startsWith("APROBADO")) {
                    guardarLotes(c, inventario);
                    cambios = crearEventos(s, inventario);
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO operacion_ejecutada(clave_idempotencia,huella_solicitud) VALUES (?,?)")) {
                        ps.setString(1, s.idempotencia());
                        ps.setString(2, huella);
                        ps.executeUpdate();
                    }
                    guardarEventos(c, cambios);
                    auditar(c, s, "APROBADO", "Operacion realizada");
                } else {
                    auditar(c, s, "RECHAZADO", respuesta);
                }
                c.commit();
                for (StockChanged evento : cambios) eventos.publicar(evento);
                return respuesta;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    private List<StockChanged> crearEventos(Solicitud s, Inventario inv) {
        List<StockChanged> cambios = new ArrayList<>();
        registrarEvento(cambios, s, inv, s.origen(), s.loteId());
        if ("TRANSFERIR".equals(s.accion()))
            registrarEvento(cambios, s, inv, s.destino(), s.nuevoLoteId());
        return cambios;
    }

    private void registrarEvento(List<StockChanged> cambios, Solicitud s,
                                Inventario inv, String bodega, String loteId) {
        Lote lote = inv.buscarLote(bodega, loteId);
        if (lote != null)
            cambios.add(new StockChanged(s.idempotencia(), s.sku(), bodega,
                loteId, s.accion(), lote.disponible(), lote.reservado(),
                s.actor(), s.tipoActor(), java.time.Instant.now()));
    }

    private void guardarEventos(Connection c, List<StockChanged> cambios) throws SQLException {
        String sql = "INSERT INTO stock_evento(operacion_id,sku,bodega_id,lote_id,accion," +
            "disponible,reservado,actor,tipo_actor,fecha) VALUES (?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (StockChanged e : cambios) {
                ps.setString(1, e.operacionId());
                ps.setString(2, e.sku());
                ps.setString(3, e.bodega());
                ps.setString(4, e.loteId());
                ps.setString(5, e.accion());
                ps.setInt(6, e.disponible());
                ps.setInt(7, e.reservado());
                ps.setString(8, e.actor());
                ps.setString(9, e.tipoActor());
                ps.setTimestamp(10, Timestamp.from(e.fecha()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private String huella(Solicitud s) {
        return String.join("|", s.actor(), s.tipoActor(), s.accion(),
            String.valueOf(s.origen()), String.valueOf(s.destino()),
            String.valueOf(s.sku()), String.valueOf(s.loteId()),
            String.valueOf(s.cantidad()), String.valueOf(s.nuevoLoteId()));
    }

    private String obtenerHuella(Connection c, String id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT huella_solicitud FROM operacion_ejecutada WHERE clave_idempotencia=?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private Inventario cargar(Connection c) throws SQLException {
        Inventario inv = new Inventario();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, capacidad FROM bodega ORDER BY id")) {
            while (rs.next()) inv.agregarBodega(rs.getString(1), rs.getInt(2));
        }
        String sql = "SELECT l.id, l.bodega_id, l.sku, p.nombre, l.disponible, " +
            "l.reservado, l.fecha_ingreso, l.fecha_vencimiento, l.estado " +
            "FROM lote l JOIN producto p ON l.sku=p.sku ORDER BY l.id";
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Lote lote = new Lote(rs.getString(1),
                    new Producto(rs.getString(3), rs.getString(4)),
                    rs.getInt(5) + rs.getInt(6),
                    rs.getDate(7).toLocalDate(), rs.getDate(8).toLocalDate());
                int reservada = rs.getInt(6);
                lote.restaurarExistencias(rs.getInt(5), reservada);
                lote.restaurarEstado(rs.getString(9));
                inv.agregarLote(rs.getString(2), lote);
            }
        }
        return inv;
    }

    private void guardarLotes(Connection c, Inventario inv) throws SQLException {
        String sql = "INSERT INTO lote(id,sku,bodega_id,fecha_ingreso,fecha_vencimiento," +
            "disponible,reservado,estado) VALUES(?,?,?,?,?,?,?,?) " +
            "ON CONFLICT (id) DO UPDATE SET bodega_id=EXCLUDED.bodega_id, " +
            "disponible=EXCLUDED.disponible, reservado=EXCLUDED.reservado, estado=EXCLUDED.estado " +
            "WHERE (lote.bodega_id,lote.disponible,lote.reservado,lote.estado) " +
            "IS DISTINCT FROM (EXCLUDED.bodega_id,EXCLUDED.disponible,EXCLUDED.reservado,EXCLUDED.estado)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (String bodega : inv.listarBodegas()) {
                for (Lote lote : inv.lotesEnBodega(bodega)) {
                    ps.setString(1, lote.id());
                    ps.setString(2, lote.producto().sku());
                    ps.setString(3, bodega);
                    ps.setDate(4, java.sql.Date.valueOf(lote.ingreso()));
                    ps.setDate(5, java.sql.Date.valueOf(lote.vencimiento()));
                    ps.setInt(6, lote.disponible());
                    ps.setInt(7, lote.reservado());
                    ps.setString(8, lote.estado());
                    ps.addBatch();
                }
            }
            ps.executeBatch();
        }
    }

    private void auditar(Connection c, Solicitud s, String resultado, String detalle) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO auditoria(clave_idempotencia,actor,tipo_actor,accion,resultado,detalle) " +
                "VALUES(?,?,?,?,?,?)")) {
            ps.setString(1, s.idempotencia());
            ps.setString(2, s.actor());
            ps.setString(3, s.tipoActor());
            ps.setString(4, s.accion());
            ps.setString(5, resultado);
            ps.setString(6, detalle);
            ps.executeUpdate();
        }
    }
}
