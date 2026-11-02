package com.botica.backend.dao.impl;

import com.botica.backend.dao.UsuarioDao;
import com.botica.backend.dto.PaginaResponse;
import com.botica.backend.exception.OrdenInvalidoException;
import com.botica.backend.model.Usuario;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class UsuarioDaoJdbc implements UsuarioDao {

    private static final String SELECT_COLUMNAS =
            "SELECT u.id, u.botica_id, b.nombre AS botica_nombre, b.direccion AS botica_direccion, " +
                    "u.nombre, u.usuario, u.password_hash, r.nombre AS rol, " +
                    "u.activo, u.intentos_fallidos, u.bloqueado_hasta, u.creado_en, u.creado_por, " +
                    "EXISTS (SELECT 1 FROM caja_diaria c WHERE c.botica_id = u.botica_id AND c.usuario_id = u.id AND c.estado = 'ABIERTA') AS tiene_caja_abierta " +
                    "FROM usuarios u " +
                    "JOIN roles r ON r.id = u.rol_id " +
                    "JOIN boticas b ON b.id = u.botica_id ";

    private static final String SELECT_ACTIVOS = SELECT_COLUMNAS + "WHERE u.activo = true AND ";

    /** Lista blanca de columnas ordenables en la administración de usuarios (Anexo D). */
    private static final Map<String, String> COLUMNAS_ORDEN = Map.of(
            "nombre", "u.nombre",
            "usuario", "u.usuario",
            "rol", "r.nombre"
    );

    private final NamedParameterJdbcTemplate jdbc;
    private final UsuarioRowMapper rowMapper = new UsuarioRowMapper();

    public UsuarioDaoJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Usuario> buscarPorUsuario(String usuario) {
        String sql = SELECT_ACTIVOS + "u.usuario = :usuario";
        var params = new MapSqlParameterSource().addValue("usuario", usuario);
        return jdbc.query(sql, params, rowMapper).stream().findFirst();
    }

    @Override
    public Optional<Usuario> buscarPorId(Long id) {
        String sql = SELECT_ACTIVOS + "u.id = :id";
        var params = new MapSqlParameterSource().addValue("id", id);
        return jdbc.query(sql, params, rowMapper).stream().findFirst();
    }

    @Override
    public Optional<Usuario> registrarIntentoFallido(Long usuarioId, OffsetDateTime ahora, int maxIntentos, OffsetDateTime bloqueadoHasta) {
        // DIF-01: el incremento ocurre en SQL (intentos_fallidos = intentos_fallidos + 1), nunca leyendo y escribiendo desde Java.
        // Si un bloqueo previo ya venció (bloqueado_hasta <= :ahora), este fallo inicia un ciclo limpio en 1.
        String nuevoContador = "CASE WHEN u.bloqueado_hasta IS NOT NULL AND u.bloqueado_hasta <= :ahora THEN 1 ELSE u.intentos_fallidos + 1 END";
        String sql = "UPDATE usuarios u SET " +
                "intentos_fallidos = " + nuevoContador + ", " +
                "bloqueado_hasta = CASE WHEN (" + nuevoContador + ") >= :maxIntentos THEN :bloqueadoHasta ELSE NULL END " +
                "WHERE u.id = :id";
        var params = new MapSqlParameterSource()
                .addValue("id", usuarioId)
                .addValue("ahora", ahora)
                .addValue("maxIntentos", maxIntentos)
                .addValue("bloqueadoHasta", bloqueadoHasta);
        jdbc.update(sql, params);
        return buscarPorId(usuarioId);
    }

    @Override
    public void reiniciarIntentosYBloqueo(Long usuarioId) {
        String sql = "UPDATE usuarios SET intentos_fallidos = 0, bloqueado_hasta = NULL WHERE id = :id";
        jdbc.update(sql, new MapSqlParameterSource().addValue("id", usuarioId));
    }

    @Override
    public PaginaResponse<Usuario> listarPaginado(Long boticaId, String buscar, int pagina, int tamano, String orden) {
        int paginaSegura = Math.max(pagina, 0);
        int tamanoSeguro = Math.min(Math.max(tamano, 1), 100);

        String campo = "nombre";
        String direccionTexto = "asc";
        if (orden != null && !orden.isBlank()) {
            String[] partes = orden.split(",", 2);
            campo = partes[0].trim();
            if (partes.length > 1) {
                direccionTexto = partes[1].trim();
            }
        }
        String columnaSql = COLUMNAS_ORDEN.get(campo);
        if (columnaSql == null) {
            throw new OrdenInvalidoException(campo);
        }
        String direccion = "desc".equalsIgnoreCase(direccionTexto) ? "DESC" : "ASC";

        String texto = buscar == null ? "" : buscar.trim();
        String filtros = "WHERE u.botica_id = :boticaId " +
                "AND (:vacio = true OR u.nombre ILIKE :patron OR u.usuario ILIKE :patron) ";
        var params = new MapSqlParameterSource()
                .addValue("boticaId", boticaId)
                .addValue("vacio", texto.isEmpty())
                .addValue("patron", "%" + texto + "%")
                .addValue("limite", tamanoSeguro)
                .addValue("offset", (long) paginaSegura * tamanoSeguro);

        String sqlDatos = SELECT_COLUMNAS + filtros +
                "ORDER BY " + columnaSql + " " + direccion + ", u.id ASC LIMIT :limite OFFSET :offset";
        List<Usuario> contenido = jdbc.query(sqlDatos, params, rowMapper);

        String sqlCount = "SELECT COUNT(*) FROM usuarios u " + filtros;
        Long total = jdbc.queryForObject(sqlCount, params, Long.class);
        return PaginaResponse.de(contenido, paginaSegura, tamanoSeguro, total == null ? 0L : total);
    }

    @Override
    public Optional<Usuario> obtenerPorIdEnBotica(Long boticaId, Long usuarioId) {
        String sql = SELECT_COLUMNAS + "WHERE u.botica_id = :boticaId AND u.id = :id";
        var params = new MapSqlParameterSource()
                .addValue("boticaId", boticaId)
                .addValue("id", usuarioId);
        return jdbc.query(sql, params, rowMapper).stream().findFirst();
    }

    @Override
    public Long insertar(Usuario usuario) {
        String sql = "INSERT INTO usuarios (botica_id, rol_id, nombre, usuario, password_hash, activo, creado_por) " +
                "VALUES (:boticaId, (SELECT id FROM roles WHERE nombre = :rol), :nombre, :usuario, :passwordHash, true, :creadoPor) " +
                "RETURNING id";
        var params = new MapSqlParameterSource()
                .addValue("boticaId", usuario.getBoticaId())
                .addValue("rol", usuario.getRol())
                .addValue("nombre", usuario.getNombre())
                .addValue("usuario", usuario.getUsuario())
                .addValue("passwordHash", usuario.getPasswordHash())
                .addValue("creadoPor", usuario.getCreadoPor());
        return jdbc.queryForObject(sql, params, Long.class);
    }

    @Override
    public void actualizar(Long boticaId, Long usuarioId, String nombre, String rol, boolean activo) {
        String sql = "UPDATE usuarios SET nombre = :nombre, " +
                "rol_id = (SELECT id FROM roles WHERE nombre = :rol), " +
                "activo = :activo " +
                "WHERE botica_id = :boticaId AND id = :id";
        var params = new MapSqlParameterSource()
                .addValue("boticaId", boticaId)
                .addValue("id", usuarioId)
                .addValue("nombre", nombre)
                .addValue("rol", rol)
                .addValue("activo", activo);
        jdbc.update(sql, params);
    }

    @Override
    public void actualizarPasswordYDesbloquear(Long boticaId, Long usuarioId, String passwordHash) {
        String sql = "UPDATE usuarios SET password_hash = :passwordHash, intentos_fallidos = 0, bloqueado_hasta = NULL " +
                "WHERE botica_id = :boticaId AND id = :id";
        var params = new MapSqlParameterSource()
                .addValue("boticaId", boticaId)
                .addValue("id", usuarioId)
                .addValue("passwordHash", passwordHash);
        jdbc.update(sql, params);
    }

    @Override
    public void desbloquear(Long boticaId, Long usuarioId) {
        String sql = "UPDATE usuarios SET intentos_fallidos = 0, bloqueado_hasta = NULL " +
                "WHERE botica_id = :boticaId AND id = :id";
        var params = new MapSqlParameterSource()
                .addValue("boticaId", boticaId)
                .addValue("id", usuarioId);
        jdbc.update(sql, params);
    }
}
