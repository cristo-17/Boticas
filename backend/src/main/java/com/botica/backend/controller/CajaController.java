package com.botica.backend.controller;

import com.botica.backend.dto.AbrirCajaRequest;
import com.botica.backend.dto.CajaResponse;
import com.botica.backend.dto.CerrarCajaRequest;
import com.botica.backend.dto.PaginaResponse;
import com.botica.backend.dto.ResumenCierreResponse;
import com.botica.backend.model.MovimientoCaja;
import com.botica.backend.service.CajaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * No valida reglas de negocio ni toca la base de datos (Regla 2): recibe
 * el DTO, llama al Service, devuelve la respuesta.
 */
@RestController
@RequestMapping("/api/caja")
public class CajaController {

    private final CajaService cajaService;

    public CajaController(CajaService cajaService) {
        this.cajaService = cajaService;
    }

    @GetMapping("/hoy")
    public ResponseEntity<CajaResponse> obtenerCajaDeHoy() {
        return ResponseEntity.ok(cajaService.obtenerCajaDeHoy());
    }

    @PostMapping("/abrir")
    public ResponseEntity<CajaResponse> abrir(@Valid @RequestBody AbrirCajaRequest request) {
        return ResponseEntity.ok(cajaService.abrir(request));
    }

    @GetMapping("/{id}/resumen-cierre")
    public ResponseEntity<ResumenCierreResponse> obtenerResumenCierre(@PathVariable Long id) {
        return ResponseEntity.ok(cajaService.obtenerResumenCierre(id));
    }

    @PostMapping("/cerrar")
    public ResponseEntity<CajaResponse> cerrar(@Valid @RequestBody CerrarCajaRequest request) {
        return ResponseEntity.ok(cajaService.cerrar(request));
    }

    @GetMapping("/{id}/movimientos")
    public ResponseEntity<PaginaResponse<MovimientoCaja>> listarMovimientos(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano,
            @RequestParam(required = false) String orden
    ) {
        return ResponseEntity.ok(cajaService.listarMovimientos(id, pagina, tamano, orden));
    }
}
