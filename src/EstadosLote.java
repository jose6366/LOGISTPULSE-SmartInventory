final class EstadosLote {
    static final LoteState RECIBIDO = new Recibido();
    static final LoteState ALMACENADO = new Almacenado();
    static final LoteState RESERVADO = new Reservado();
    static final LoteState PICKEADO = new Pickeado();
    static final LoteState ENVIADO = new Enviado();
    static final LoteState CUARENTENA = new Cuarentena();

    static LoteState desde(String nombre) {
        return switch (nombre) {
            case "RECIBIDO" -> RECIBIDO;
            case "ALMACENADO" -> ALMACENADO;
            case "RESERVADO" -> RESERVADO;
            case "PICKEADO" -> PICKEADO;
            case "ENVIADO" -> ENVIADO;
            case "CUARENTENA" -> CUARENTENA;
            default -> throw new IllegalArgumentException("Estado desconocido: " + nombre);
        };
    }

    private static class Recibido implements LoteState {
        public String nombre() { return "RECIBIDO"; }
        public void almacenar(Lote l) { l.cambiarEstado(ALMACENADO); }
        public void cuarentena(Lote l) { l.cambiarEstado(CUARENTENA); }
    }
    private static class Almacenado implements LoteState {
        public String nombre() { return "ALMACENADO"; }
        public void reservar(Lote l, int n) {
            l.descontarDisponible(n);
            l.aumentarReservado(n);
            l.cambiarEstado(RESERVADO);
        }
        public void ajustar(Lote l, int diferencia) { l.modificarDisponible(diferencia); }
        public void sacar(Lote l, int n) { l.descontarDisponible(n); }
        public void cuarentena(Lote l) { l.cambiarEstado(CUARENTENA); }
    }
    private static class Reservado implements LoteState {
        public String nombre() { return "RESERVADO"; }
        public void reservar(Lote l, int n) {
            l.descontarDisponible(n);
            l.aumentarReservado(n);
        }
        public void liberar(Lote l, int n) {
            l.descontarReservado(n);
            l.aumentarDisponible(n);
            if (l.reservado() == 0) l.cambiarEstado(ALMACENADO);
        }
        public void ajustar(Lote l, int diferencia) { l.modificarDisponible(diferencia); }
        public void sacar(Lote l, int n) { l.descontarDisponible(n); }
        public void pickear(Lote l) {
            if (l.reservado() <= 0 || l.disponible() != 0)
                throw new IllegalStateException("Para pickear debe estar reservado el lote completo");
            l.cambiarEstado(PICKEADO);
        }
        public void cuarentena(Lote l) { l.cambiarEstado(CUARENTENA); }
    }
    private static class Pickeado implements LoteState {
        public String nombre() { return "PICKEADO"; }
        public void enviar(Lote l) {
            if (l.disponible() != 0 || l.reservado() <= 0)
                throw new IllegalStateException("Lote no listo para envio");
            l.consumirReservado();
            l.cambiarEstado(ENVIADO);
        }
        public void cuarentena(Lote l) { l.cambiarEstado(CUARENTENA); }
    }
    private static class Enviado implements LoteState {
        public String nombre() { return "ENVIADO"; }
    }
    private static class Cuarentena implements LoteState {
        public String nombre() { return "CUARENTENA"; }
        public void liberar(Lote l, int n) {
            l.descontarReservado(n);
            l.aumentarDisponible(n);
        }
        public void rehabilitar(Lote l) {
            if (!l.vencimiento().isAfter(java.time.LocalDate.now()))
                throw new IllegalStateException("Lote vencido; no puede rehabilitarse");
            l.cambiarEstado(l.reservado() > 0 ? RESERVADO : ALMACENADO);
        }
    }
}
