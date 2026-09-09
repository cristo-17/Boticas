package com.botica.backend.controller;

import com.botica.backend.dto.ProductoResponse;
import com.botica.backend.service.ProductoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * No valida reglas de negocio ni toca la base de datos (Regla 2): recibe
 * la petición, llama al Service, devuelve la respuesta.
 */
@RestController
@RequestMapping("/api/productos")
public class ProductoController {

    private final ProductoService productoService;

    public ProductoController(ProductoService productoService) {
        this.productoService = productoService;
    }

    @GetMapping
    public ResponseEntity<List<ProductoResponse>> buscar(@RequestParam(name = "buscar", required = false, defaultValue = "") String buscar) {
        return ResponseEntity.ok(productoService.buscar(buscar));
    }

    @GetMapping("/codigo/{codigoBarras}")
    public ResponseEntity<ProductoResponse> buscarPorCodigoBarras(@PathVariable String codigoBarras) {
        return ResponseEntity.ok(productoService.buscarPorCodigoBarras(codigoBarras));
    }

    @GetMapping("/mas-vendidos")
    public ResponseEntity<List<ProductoResponse>> masVendidos() {
        return ResponseEntity.ok(productoService.masVendidos());
    }
}
