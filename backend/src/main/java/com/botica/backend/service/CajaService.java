package com.botica.backend.service;

import com.botica.backend.config.ConfigNegocioProperties;
import com.botica.backend.config.ContextoOperacion;
import com.botica.backend.dao.CajaDao;
import com.botica.backend.dto.AbrirCajaRequest;
import com.botica.backend.dto.CajaResponse;
import com.botica.backend.dto.CerrarCajaRequest;
import com.botica.backend.dto.PaginaResponse;
import com.botica.backend.dto.ResumenCierreResponse;
import com.botica.backend.exception.CajaNoAbiertaException;
import com.botica.backend.exception.CajaNoEncontradaException;
import com.botica.backend.exception.CajaYaAbiertaException;
import com.botica.backend.exception.MontoInvalidoException;
import com.botica.backend.event.DescuadreGraveEvent;
import com.botica.backend.model.CajaDiaria;
import com.botica.backend.model.MovimientoCaja;
import com.botica.backend.util.Dinero;
import com.botica.backend.util.FechaNegocio;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * TODA la regla de negocio de caja vive aquí (Regla 2) — el Controller
 * no valida nada, el DAO no sabe qué es HTTP.
 */
@Service
public class CajaService {

    private final CajaDao cajaDao;
    private final ContextoOperacion contexto;
    private final FechaNegocio fechaNegocio;
    private final ConfigNegocioProperties config;
    private final ApplicationEventPublisher eventPublisher;

    public CajaService(CajaDao cajaDao, ContextoOperacion contexto, FechaNegocio fechaNegocio, ConfigNegocioProperties config) {
        this(cajaDao, contexto, fechaNegocio, config, null);
    }

    public CajaService(CajaDao cajaDao, ContextoOperacion contexto, FechaNegocio fechaNegocio,
                       ConfigNegocioProperties config, @Autowired(required = false) ApplicationEventPublisher eventPublisher) {
        this.cajaDao = cajaDao;
        this.contexto = contexto;
        this.fechaNegocio = fechaNegocio;
        this.config = config;
        this.eventPublisher = eventPublisher;
    }

    public CajaResponse obtenerCajaDeHoy() {
        return cajaDao.buscarAbiertaDelUsuario(contexto.boticaId(), contexto.usuarioId())
                .map(CajaResponse::desde)
                .orElse(null);
    }

    @Transactional
    public CajaResponse abrir(AbrirCajaRequest request) {
        validarMontoNoNegativo(request.montoInicial());

        Long boticaId = contexto.boticaId();
        Long usuarioId = contexto.usuarioId();
        LocalDate hoy = fechaNegocio.hoy();

        if (cajaDao.existeCajaAbierta(boticaId, usuarioId, request.turno(), hoy)) {
            throw new CajaYaAbiertaException();
        }

        CajaDiaria nueva = CajaDiaria.builder()
                .boticaId(boticaId)
                .usuarioId(usuarioId)
                .fecha(hoy)
                .turno(request.turno())
                .montoApertura(Dinero.redondear(request.montoInicial()))
                .horaApertura(fechaNegocio.ahora())
                .estado(CajaDiaria.ABIERTA)
                .build();

        CajaDiaria creada = cajaDao.insertar(nueva);
        return CajaResponse.desde(creada);
    }

    public ResumenCierreResponse obtenerResumenCierre(Long id) {
        Long boticaId = contexto.boticaId();
        CajaDiaria caja = cajaDao.buscarPorId(boticaId, id)
                .orElseThrow(CajaNoEncontradaException::new);
        if (!caja.estaAbierta()) {
            throw new CajaNoAbiertaException();
        }
        return cajaDao.calcularResumen(boticaId, id);
    }

    @Transactional
    public CajaResponse cerrar(CerrarCajaRequest request) {
        validarMontoNoNegativo(request.montoContado());

        Long boticaId = contexto.boticaId();
        Long usuarioId = contexto.usuarioId();
        CajaDiaria caja = cajaDao.buscarAbiertaDelUsuario(boticaId, usuarioId)
                .orElseThrow(CajaNoAbiertaException::new);

        BigDecimal montoEsperado = Dinero.redondear(
                caja.getMontoApertura().add(cajaDao.sumarMovimientosEfectivo(boticaId, caja.getId())));
        BigDecimal montoContado = Dinero.redondear(request.montoContado());
        BigDecimal diferencia = Dinero.redondear(montoContado.subtract(montoEsperado));

        caja.setMontoContado(montoContado);
        caja.setMontoEsperado(montoEsperado);
        caja.setDiferencia(diferencia);
        String semaforo = calcularSemaforo(diferencia);
        caja.setSemaforoDescuadre(semaforo);
        caja.setHoraCierre(fechaNegocio.ahora());
        caja.setObservaciones(request.observaciones());
        caja.setEstado(CajaDiaria.CERRADA);

        cajaDao.actualizarCierre(caja);

        if ("GRAVE".equals(semaforo) && eventPublisher != null) {
            eventPublisher.publishEvent(new DescuadreGraveEvent(
                    caja.getBoticaId(),
                    caja.getId(),
                    caja.getUsuarioNombre() != null ? caja.getUsuarioNombre() : "Usuario #" + caja.getUsuarioId(),
                    diferencia,
                    montoEsperado,
                    montoContado
            ));
        }

        return CajaResponse.desde(caja);
    }

    public PaginaResponse<MovimientoCaja> listarMovimientos(Long id, int pagina, int tamano, String orden) {
        Long boticaId = contexto.boticaId();
        cajaDao.buscarPorId(boticaId, id).orElseThrow(CajaNoEncontradaException::new);
        return cajaDao.listarMovimientosPaginado(boticaId, id, pagina, tamano, orden);
    }

    private void validarMontoNoNegativo(BigDecimal monto) {
        if (monto.compareTo(BigDecimal.ZERO) < 0) {
            throw new MontoInvalidoException();
        }
    }

    private String calcularSemaforo(BigDecimal diferencia) {
        BigDecimal absoluta = diferencia.abs();
        if (absoluta.compareTo(BigDecimal.ZERO) == 0) {
            return "EXACTO";
        }
        if (absoluta.compareTo(config.getDescuadreLeve()) <= 0) {
            return "LEVE";
        }
        return "GRAVE";
    }
}
