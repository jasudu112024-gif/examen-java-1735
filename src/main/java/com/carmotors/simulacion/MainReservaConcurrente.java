
package com.carmotors.simulacion;

import com.carmotors.config.ConexionFactory;
import com.carmotors.dao.jdbc.RepuestoDAOJdbc;
import com.carmotors.excepcion.CarMotorsException;
import com.carmotors.modelo.TipoMantenimiento;
import com.carmotors.servicio.InventarioServicio;
import com.carmotors.servicio.OrdenServicioServicio;

import java.sql.SQLException;

public class MainReservaConcurrente {
    // Visto en clase: constantes final / static final -> Ticket (final IVA, Sesión 01) y S12Demo04 ConfiguracionSistema (Sesión 12)
    private static final String NOMBRE_REPUESTO = "Filtro de Aceite XY-123";
    private static final String CODIGO_LOTE = "SIM-XY123-001";
    private static final String[] TECNICOS = {"Hilo-Técnico-A", "Hilo-Técnico-B"};

    // Visto en clase: métodos y miembros static -> S05Demo02ConversorTemperatura (Sesión 05)
    public static void main(String[] args) {
        // Visto en clase: try / catch / throw / throws -> S10Demo03ExcepcionesMain (Sesión 10)
        try {
            simular();
        } catch (SQLException | CarMotorsException error) {
            System.out.println("[ERROR] " + error.getMessage());
        }
    }

    private static void simular() throws SQLException {
        InventarioServicio inventario = new InventarioServicio();
        OrdenServicioServicio ordenes = new OrdenServicioServicio();

        // ---- 1) Escenario: un repuesto con stock exacto de 1 y una orden de servicio por técnico
        int repuestoId = prepararRepuesto(inventario);
        String placa = ordenes.listarVehiculos().get(0).getPlaca();
        int[] ordenIds = new int[TECNICOS.length];
        // Visto en clase: ciclos for / for-each / while -> S03DemoFor y S03DemoWhile (Sesión 03)
        for (int i = 0; i < TECNICOS.length; i++) {
            ordenIds[i] = ordenes.crearOrden(placa, TipoMantenimiento.PREVENTIVO,
                    "Simulación de reserva concurrente (" + TECNICOS[i] + ")", 0);
        }

        System.out.println("Iniciando simulación de reserva concurrente para el repuesto '" + NOMBRE_REPUESTO + "'...");
        System.out.println("Stock inicial: " + inventario.obtenerRepuesto(repuestoId).getDisponible());
        System.out.println();

        // ---- 2) Un hilo por técnico; cada uno guarda su resultado en SU posición del arreglo
        boolean[] reservo = new boolean[TECNICOS.length];
        Thread[] hilos = new Thread[TECNICOS.length];
        for (int i = 0; i < TECNICOS.length; i++) {
            final int posicion = i;   // la lambda solo puede usar variables que no cambian
            final int servicioId = ordenIds[i];
            // Visto en clase: lambdas -> S19Demo01AccionProveedorMain (Sesión 19)
            hilos[i] = new Thread(() -> intentarReserva(posicion, servicioId, repuestoId, reservo), TECNICOS[i]);
        }
        for (Thread hilo : hilos) {
            hilo.start();   // los dos hilos corren en paralelo
        }

        // ---- 3) El main espera a que los dos terminen antes de mostrar el resultado
        for (Thread hilo : hilos) {
            try {
                hilo.join();
            } catch (InterruptedException error) {
                System.out.println("[AVISO] Se interrumpió la espera del " + hilo.getName() + ".");
            }
        }

        // ---- 4) Resultado final
        System.out.println();
        System.out.println("Simulación finalizada.");
        for (int i = 0; i < TECNICOS.length; i++) {
            System.out.println(TECNICOS[i] + (reservo[i] ? " RESERVÓ la última unidad." : " fue RECHAZADO."));
        }
        System.out.println("Stock final del repuesto (disponible): " + inventario.obtenerRepuesto(repuestoId).getDisponible());
    }

    /** Lo que ejecuta cada hilo: pide la reserva con la MISMA operación de la aplicación. */
    private static void intentarReserva(int posicion, int servicioId, int repuestoId, boolean[] reservo) {
        String tecnico = TECNICOS[posicion];
        System.out.println(tecnico + ": Intentando reservar el repuesto para la orden #" + servicioId + "...");
        try {
            new OrdenServicioServicio().asignarRepuesto(servicioId, repuestoId, 1);
            reservo[posicion] = true;
            System.out.println(tecnico + ": ¡Reserva exitosa!");
        } catch (SQLException | CarMotorsException error) {
            // El mensaje de la regla de negocio ya dice cuánto había: "disponible 0, solicitado 1".
            System.out.println(tecnico + ": Fallo en la reserva. " + error.getMessage());
        }
    }

    /**
     * Deja el repuesto con stock 1 y reservado 0. Si no existe lo crea y le ingresa 1 unidad por
     * un lote (el stock solo entra por lotes); si ya existe, solo restablece sus cantidades.
     */
    private static int prepararRepuesto(InventarioServicio inventario) throws SQLException {
        int repuestoId;
        if (inventario.buscar(NOMBRE_REPUESTO).isEmpty()) {
            int proveedorId = inventario.listarProveedores().get(0).getId();
            repuestoId = inventario.registrarRepuesto(NOMBRE_REPUESTO, inventario.listarTipos().get(0).getId(),
                    "Simulación", proveedorId, 10000, 15000, 0);
            inventario.registrarLote(CODIGO_LOTE, repuestoId, proveedorId, 1, null);
        } else {
            repuestoId = inventario.buscar(NOMBRE_REPUESTO).get(0).getId();
        }
        new RepuestoDAOJdbc(ConexionFactory.getInstancia()).actualizarCantidades(repuestoId, 1, 0);
        return repuestoId;
    }
}