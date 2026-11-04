package com.botica.backend.exception;

import org.springframework.http.HttpStatus;

/** Tarea 22: una revisión se resuelve una sola vez y no se reabre. */
public class RevisionYaResueltaException extends NegocioException {
    public RevisionYaResueltaException() {
        super(HttpStatus.CONFLICT, "REVISION_YA_RESUELTA", "Esta revisión ya fue resuelta.");
    }
}
