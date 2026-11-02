package com.botica.backend.controller;

import com.botica.backend.dto.EstadoProductoRequest;
import com.botica.backend.dto.PaginaResponse;
import com.botica.backend.dto.ProductoCatalogoResponse;
import com.botica.backend.dto.ProductoRequest;
import com.botica.backend.dto.ProductoResponse;
import com.botica.backend.service.CatalogoProductoService;
import com.botica.backend.service.ProductoService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * No valida reglas de negocio ni toca la base de datos (Regla 2): recibe
 * la petición, llama al Service, devuelve la respuesta. Las rutas de
 * catálogo (/catalogo, POST, PUT, PATCH) son solo ADMINISTRADOR, y eso
 * lo exige SecurityConfig (Tarea 17, R17).
 */
@RestController
@RequestMapping("/api/productos")
public class ProductoController {

    private final ProductoService productoService;
    private final CatalogoProductoService catalogoService;

    public ProductoController(ProductoService productoService, CatalogoProductoService catalogoService) {
        this.productoService = productoService;
        this.catalogoService = catalogoService;
    }

    @GetMapping
    public ResponseEntity<List<ProductoResponse>> buscar(
            @RequestParam(name = "buscar", required = false, defaultValue = "") String buscar,
            @RequestParam(name = "incluirInactivos", required = false, defaultValue = "false") boolean incluirInactivos
    ) {
        return ResponseEntity.ok(productoService.buscar(buscar, incluirInactivos));
    }

    @GetMapping("/codigo/{codigoBarras}")
    public ResponseEntity<ProductoResponse> buscarPorCodigoBarras(@PathVariable String codigoBarras) {
        return ResponseEntity.ok(productoService.buscarPorCodigoBarras(codigoBarras));
    }

    @GetMapping("/mas-vendidos")
    public ResponseEntity<List<ProductoResponse>> masVendidos() {
        return ResponseEntity.ok(productoService.masVendidos());
    }

    @GetMapping("/catalogo")
    public ResponseEntity<PaginaResponse<ProductoCatalogoResponse>> catalogo(
            @RequestParam(name = "buscar", required = false, defaultValue = "") String buscar,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano,
            @RequestParam(required = false) String orden
    ) {
        return ResponseEntity.ok(catalogoService.listar(buscar, pagina, tamano, orden));
    }

    @PostMapping
    public ResponseEntity<ProductoCatalogoResponse> crear(@Valid @RequestBody ProductoRequest request) {
        return ResponseEntity.ok(catalogoService.crear(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductoCatalogoResponse> actualizar(@PathVariable Long id, @Valid @RequestBody ProductoRequest request) {
        return ResponseEntity.ok(catalogoService.actualizar(id, request));
    }

    @PatchMapping("/{id}/estado")
    public ResponseEntity<ProductoCatalogoResponse> cambiarEstado(@PathVariable Long id, @Valid @RequestBody EstadoProductoRequest request) {
        return ResponseEntity.ok(catalogoService.cambiarEstado(id, request.activo()));
    }
}
