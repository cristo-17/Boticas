package com.botica.backend.service;

import com.botica.backend.config.ContextoOperacion;
import com.botica.backend.dao.CajaDao;
import com.botica.backend.dao.VentaDao;
import com.botica.backend.dto.NuevaVentaRequest;
import com.botica.backend.dto.PaginaResponse;
import com.botica.backend.dto.VentaResponse;
import com.botica.backend.exception.SinPermisoException;
import com.botica.backend.exception.VentaNoEncontradaException;
import com.botica.backend.model.CajaDiaria;
import com.botica.backend.model.Venta;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Punto de entrada público de Ventas (Tarea 11 Bloque B) — deliberadamente
 * SIN @Transactional acá. El trabajo real vive en {@link VentaTransaccion},
 * un bean aparte: si dos peticiones con la misma claveIdempotencia chocan
 * en el INSERT (violación de unicidad), la transacción de VentaTransaccion
 * tiene que revertirse DE VERDAD (stock ya descontado incluido) antes de
 * que este método vuelva a consultar por esa clave — eso solo pasa si la
 * excepción se propaga hasta cruzar el proxy de Spring, y una auto-invocación
 * (llamarse a sí mismo) no cruza ningún proxy.
 */
@Service
public class VentaService {

    private final VentaTransaccion transaccion;
    private final VentaDao ventaDao;
    private final CajaDao cajaDao;
    private final ContextoOperacion contexto;

    public VentaService(VentaTransaccion transaccion, VentaDao ventaDao, CajaDao cajaDao, ContextoOperacion contexto) {
        this.transaccion = transaccion;
        this.ventaDao = ventaDao;
        this.cajaDao = cajaDao;
        this.contexto = contexto;
    }

    public VentaResponse registrar(NuevaVentaRequest request) {
        try {
            return transaccion.ejecutar(request);
        } catch (DuplicateKeyException e) {
            // La otra petición ganó la carrera del INSERT y ya está confirmada
            // (Postgres bloquea el segundo INSERT hasta que el primero termina,
            // y solo lanza la violación si el primero ya hizo commit) -- devolver
            // su venta en vez de un 500. Para cuando llegamos acá, la transacción
            // de ESTA petición ya se revirtió por completo (Spring lo hizo al
            // propagarse la excepción sin atrapar dentro de VentaTransaccion).
            Venta existente = ventaDao.buscarPorClaveIdempotencia(contexto.boticaId(), request.claveIdempotencia())
                    .orElseThrow(() -> e);
            return transaccion.construirRespuestaDesdeVentaExistente(existente);
        }
    }

    /**
     * D-E2-2: el ADMINISTRADOR ve todas las ventas de su botica; el TECNICO solo
     * las de su caja abierta (sin caja abierta, la página sale vacía). Un técnico
     * que ve todos los turnos ve cuánto vendió cada compañero.
     */
    public PaginaResponse<VentaResponse> listar(int pagina, int tamano, String orden) {
        Long cajaId = null;
        if (!esAdministrador()) {
            Optional<CajaDiaria> caja = cajaActualDelTecnico();
            if (caja.isEmpty()) {
                return PaginaResponse.de(List.of(), Math.max(pagina, 0), Math.min(Math.max(tamano, 1), 100), 0L);
            }
            cajaId = caja.get().getId();
        }
        PaginaResponse<Venta> paginaVentas = ventaDao.listarPaginado(contexto.boticaId(), cajaId, pagina, tamano, orden);
        List<VentaResponse> respuestas = paginaVentas.contenido().stream()
                .map(transaccion::construirRespuestaDesdeVentaExistente)
                .toList();
        return PaginaResponse.de(respuestas, paginaVentas.pagina(), paginaVentas.tamano(), paginaVentas.totalElementos());
    }

    public VentaResponse obtenerPorId(Long id) {
        Venta venta = ventaDao.buscarPorId(contexto.boticaId(), id)
                .orElseThrow(VentaNoEncontradaException::new);
        // Mismo criterio que CajaService.cajaAjenaACerrar: otra botica = 404 (indistinguible
        // de inexistente); misma botica pero otra caja = 403.
        if (!esAdministrador()) {
            boolean deSuCaja = cajaActualDelTecnico().map(c -> c.getId().equals(venta.getCajaId())).orElse(false);
            if (!deSuCaja) {
                throw new SinPermisoException();
            }
        }
        return transaccion.construirRespuestaDesdeVentaExistente(venta);
    }

    private boolean esAdministrador() {
        return "ADMINISTRADOR".equals(contexto.rol());
    }

    private Optional<CajaDiaria> cajaActualDelTecnico() {
        return cajaDao.buscarAbiertaDelUsuario(contexto.boticaId(), contexto.usuarioId());
    }
}
