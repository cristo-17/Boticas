package com.botica.backend;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class BackendApplication {
	public static void main(String[] args) {
		BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
		// Cambia "mi_contraseña_secreta" por la contraseña real que quieres usar
		String miHash = encoder.encode("mi_contraseña_secreta");
		System.out.println("Tu hash es: " + miHash);
	}
}