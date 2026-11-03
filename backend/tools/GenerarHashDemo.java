import org.springframework.security.crypto.bcrypt.BCrypt;

import java.io.Console;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Tarea D: genera DEMO_PASSWORD_HASH sin que la contraseña pase por argumentos,
 * archivos ni el historial de la terminal. La pide dos veces sin mostrarla y
 * escribe SOLO el hash BCrypt, con la misma versión y fuerza que el backend
 * (new BCryptPasswordEncoder(): $2a, 10 rondas; lo fija BcryptFuerzaTest).
 *
 * Se ejecuta desde la raíz del repo, en una terminal real (PowerShell):
 *   java -cp "$env:USERPROFILE\.m2\repository\org\springframework\security\spring-security-crypto\7.1.1\spring-security-crypto-7.1.1.jar" backend\tools\GenerarHashDemo.java
 */
public class GenerarHashDemo {

    static final String VERSION = "$2a";
    static final int FUERZA = 10;
    private static final int MAXIMO_BYTES_BCRYPT = 72;

    public static void main(String[] args) {
        Console consola = System.console();
        if (consola == null) {
            System.err.println("Sin consola: ejecútalo en una terminal (PowerShell), no redirigido ni desde un IDE.");
            System.exit(1);
        }
        char[] primera = consola.readPassword("Contraseña demo: ");
        char[] segunda = consola.readPassword("Repítela: ");
        byte[] bytes = null;
        try {
            if (primera == null || segunda == null || primera.length == 0) {
                System.err.println("No se ingresó ninguna contraseña.");
                System.exit(1);
            }
            if (!Arrays.equals(primera, segunda)) {
                System.err.println("Las dos contraseñas no coinciden. No se generó nada.");
                System.exit(1);
            }
            if (primera.length < 10) {
                System.err.println("Usa al menos 10 caracteres: la contraseña demo se usa contra producción.");
                System.exit(1);
            }
            bytes = utf8(primera);
            if (bytes.length > MAXIMO_BYTES_BCRYPT) {
                System.err.println("BCrypt solo usa los primeros 72 bytes: usa una contraseña más corta.");
                System.exit(1);
            }
            String hash = BCrypt.hashpw(bytes, BCrypt.gensalt(VERSION, FUERZA));
            if (!BCrypt.checkpw(bytes, hash)) {
                System.err.println("El hash generado no verifica. No lo uses.");
                System.exit(1);
            }
            System.out.println(hash);
        } finally {
            if (primera != null) Arrays.fill(primera, '\0');
            if (segunda != null) Arrays.fill(segunda, '\0');
            if (bytes != null) Arrays.fill(bytes, (byte) 0);
        }
    }

    private static byte[] utf8(char[] caracteres) {
        ByteBuffer buffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(caracteres));
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        if (buffer.hasArray()) {
            Arrays.fill(buffer.array(), (byte) 0);
        }
        return bytes;
    }
}
