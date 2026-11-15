public class MainSql {
    public static void main(String[] args) throws Exception {
        String url = System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://localhost:5432/logistpulse");
        String user = System.getenv().getOrDefault("DB_USER", "logistpulse");
        String password = System.getenv().getOrDefault("DB_PASSWORD", "logistpulse");
        ControlSql control = new ControlSql(url, user, password, new FEFO(), new EOQ());
        ReposicionObserver reposicionObs = new ReposicionObserver(60);
        PulseFleetObserver fleetObs = new PulseFleetObserver();
        LedgerObserver ledgerObs = new LedgerObserver();
        control.suscribir(reposicionObs);
        control.suscribir(fleetObs);
        control.suscribir(ledgerObs);
        Lote seleccionado = control.elegirLote("QUITO", "SKU001", 20);
        if (seleccionado == null) throw new IllegalStateException("No hay lote disponible");
        Solicitud solicitud = new Solicitud("sql-op1", "ana", "HUMANO", "RESERVAR",
            "QUITO", null, "SKU001", seleccionado.id(), 20, null);
        System.out.println(control.ejecutar(new ReservarCommand(solicitud)));
        System.out.println(control.ejecutar(new ReservarCommand(solicitud)));
        Solicitud ia = new Solicitud("sql-op2", "agente", "IA", "AJUSTAR", "QUITO", null,
            "SKU001", "L1", -10000, null);
        System.out.println(control.ejecutar(new AjustarCommand(ia)));
        System.out.println("Observer: eventos de ledger en esta ejecucion = " + ledgerObs.movimientos().size());
        System.out.println("Observer: eventos de PULSEFLEET en esta ejecucion = " + fleetObs.eventos().size());
    }
}
