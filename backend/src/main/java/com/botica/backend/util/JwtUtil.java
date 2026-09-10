package com.botica.backend.util;

import com.botica.backend.exception.TokenInvalidoException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JWT mínimo (HS256), a mano — a propósito, sin jjwt ni ninguna librería
 * de terceros: este proyecto ya se topó una vez con una librería que
 * asumía Jackson 2.x cuando Spring Boot 4.1.1 trae Jackson 3.x
 * (`[BE-002]`, docs/BITACORA.md) — reusar el {@link ObjectMapper} que
 * Spring ya configura (Jackson 3.x real, `tools.jackson`) evita ese
 * riesgo por completo en vez de "verificarlo" a ciegas con una
 * dependencia nueva. El algoritmo (header.payload.firma en Base64url,
 * HMAC-SHA256) es exactamente el estándar JWT — nada propietario.
 */
@Component
public class JwtUtil {

    private static final String HEADER_JSON = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
    private static final Base64.Encoder ENC = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DEC = Base64.getUrlDecoder();
    private static final String HEADER_B64 = ENC.encodeToString(HEADER_JSON.getBytes(StandardCharsets.UTF_8));

    private final ObjectMapper objectMapper;
    private final SecretKeySpec clave;
    private final long expiracionSegundos;

    public JwtUtil(
            ObjectMapper objectMapper,
            @Value("${app.jwt.secret}") String secreto,
            @Value("${app.jwt.expiracion-horas:12}") long expiracionHoras
    ) {
        this.objectMapper = objectMapper;
        this.clave = new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.expiracionSegundos = expiracionHoras * 3600;
    }

    /** Agrega iat/exp automáticamente -- el llamador nunca los pone a mano. */
    public String generar(Map<String, Object> claims) {
        Map<String, Object> todos = new LinkedHashMap<>(claims);
        long ahora = Instant.now().getEpochSecond();
        todos.put("iat", ahora);
        todos.put("exp", ahora + expiracionSegundos);
        String payloadB64 = ENC.encodeToString(objectMapper.writeValueAsBytes(todos));
        String firma = firmar(HEADER_B64 + "." + payloadB64);
        return HEADER_B64 + "." + payloadB64 + "." + firma;
    }

    /** Verifica la firma (comparación en tiempo constante) y la expiración; devuelve los claims si todo es válido. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> verificarYExtraer(String token) {
        String[] partes = token.split("\\.");
        if (partes.length != 3) {
            throw new TokenInvalidoException();
        }
        String firmaEsperada = firmar(partes[0] + "." + partes[1]);
        boolean firmaValida = MessageDigest.isEqual(
                firmaEsperada.getBytes(StandardCharsets.UTF_8),
                partes[2].getBytes(StandardCharsets.UTF_8));
        if (!firmaValida) {
            throw new TokenInvalidoException();
        }
        Map<String, Object> claims;
        try {
            claims = objectMapper.readValue(DEC.decode(partes[1]), Map.class);
        } catch (RuntimeException e) {
            throw new TokenInvalidoException();
        }
        long exp = ((Number) claims.get("exp")).longValue();
        if (Instant.now().getEpochSecond() > exp) {
            throw new TokenInvalidoException();
        }
        return claims;
    }

    private String firmar(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(clave);
            byte[] hmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return ENC.encodeToString(hmac);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo firmar el JWT", e);
        }
    }
}
