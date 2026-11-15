import java.time.LocalDate;

public class Main {
    public static void main(String[] args) {
        Producto arroz = new Producto("SKU001", "Arroz");
        Inventario inventario = new Inventario();
        inventario.agregarBodega("QUITO", 500);
        inventario.agregarBodega("GUAYAQUIL", 200);
        Lote l1 = new Lote("L1", arroz, 100, LocalDate.of(2026, 1, 10), LocalDate.of(2027, 8, 10));
        Lote l2 = new Lote("L2", arroz, 80, LocalDate.of(2026, 2, 10), LocalDate.of(2027, 3, 10));
        l1.almacenar();
        l2.almacenar();
        inventario.agregarLote("QUITO", l1);
        inventario.agregarLote("QUITO", l2);
        Auditoria auditoria = new Auditoria();
        Control control = new Control(inventario, auditoria, new FEFO(), new EOQ());
        ReposicionObserver reposicionObs = new ReposicionObserver(60);
        PulseFleetObserver fleetObs = new PulseFleetObserver();
        LedgerObserver ledgerObs = new LedgerObserver();
        control.suscribir(reposicionObs);
        control.suscribir(fleetObs);
        control.suscribir(ledgerObs);

        Lote seleccionado = control.elegirLote("QUITO", "SKU001", 20);
        System.out.println("FEFO eligio: " + seleccionado.id());
        Solicitud humano = new Solicitud("op1", "ana", "HUMANO", "RESERVAR", "QUITO", null, "SKU001", seleccionado.id(), 20, null);
        System.out.println(control.ejecutar(new ReservarCommand(humano)));
        System.out.println(control.ejecutar(new ReservarCommand(humano)));

        Solicitud ia = new Solicitud("op2", "agente-reposicion", "IA", "AJUSTAR", "QUITO", null, "SKU001", "L1", -1000, null);
        System.out.println(control.ejecutar(new AjustarCommand(ia)));

        Solicitud liberar = new Solicitud("op3", "ana", "HUMANO", "LIBERAR", "QUITO", null, "SKU001", "L2", 5, null);
        System.out.println(control.ejecutar(new LiberarCommand(liberar)));

        Solicitud transferir = new Solicitud("op4", "agente-logistica", "IA", "TRANSFERIR", "QUITO", "GUAYAQUIL", "SKU001", "L1", 25, "L1-GYE");
        System.out.println(control.ejecutar(new TransferirCommand(transferir)));

        Solicitud ajustar = new Solicitud("op5", "ana", "HUMANO", "AJUSTAR", "QUITO", null, "SKU001", "L1", 5, null);
        System.out.println(control.ejecutar(new AjustarCommand(ajustar)));

        DatosReposicion datos = new DatosReposicion(12000, 45, 3, 40, 5, 30, 180);
        System.out.println("EOQ: " + control.recomendar(datos));
        control.cambiarReposicion(new PuntoReorden());
        System.out.println("ROP: " + control.recomendar(datos));
        control.cambiarReposicion(new MinMax(230, 500));
        System.out.println("Min-Max: " + control.recomendar(datos));

        Lote l3 = new Lote("L3", arroz, 10, LocalDate.of(2026, 4, 1), LocalDate.of(2027, 11, 1));
        inventario.agregarLote("QUITO", l3);
        System.out.println("State: " + l3.estado());
        l3.almacenar();
        l3.reservar(10);
        l3.pickear();
        l3.enviar();
        System.out.println("State final: " + l3.estado() + ", existencias: " + l3.total());

        System.out.println("Observer: eventos de Ledger = " + ledgerObs.movimientos().size());
        System.out.println("Observer: eventos de PULSEFLEET = " + fleetObs.eventos().size());
        System.out.println("Observer: avisos de reposicion = " + reposicionObs.avisos());
        System.out.println("Auditoria:");
        for (Registro r : auditoria.historial()) System.out.println(r);
    }
}
