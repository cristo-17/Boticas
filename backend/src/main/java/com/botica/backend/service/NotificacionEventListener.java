package com.botica.backend.service;

import com.botica.backend.dao.NotificacionDao;
import com.botica.backend.dto.NotificacionResponse;
import com.botica.backend.event.DescuadreGraveEvent;
import com.botica.backend.event.StockCriticoEvent;
import com.botica.backend.model.Notificacion;
import com.botica.backend.util.Dinero;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NotificacionEventListener {

    private final NotificacionDao notificacionDao;
    private final SseEmitterService sseEmitterService;

    public NotificacionEventListener(NotificacionDao notificacionDao, SseEmitterService sseEmitterService) {
        this.notificacionDao = notificacionDao;
        this.sseEmitterService = sseEmitterService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alOcurrirDescuadreGrave(DescuadreGraveEvent event) {
        String mensaje = String.format(
                "El cajero %s cerró la caja #%d con una diferencia de S/ %s (Esperado: S/ %s, Contado: S/ %s).",
                event.usuarioCajero(),
                event.cajaId(),
                Dinero.redondear(event.diferencia()),
                Dinero.redondear(event.esperado()),
                Dinero.redondear(event.contado())
        );

        Notificacion n = Notificacion.builder()
                .boticaId(event.boticaId())
                .rolDestinatario("ADMINISTRADOR")
                .tipo("DESCUADRE_GRAVE")
                .titulo("Descuadre grave en Caja #" + event.cajaId())
                .mensaje(mensaje)
                .leido(false)
                .build();

        n = notificacionDao.insertar(n);

        NotificacionResponse resp = new NotificacionResponse(
                n.getId(),
                n.getTipo(),
                n.getTitulo(),
                n.getMensaje(),
                n.isLeido(),
                n.getFechaCreacion()
        );

        sseEmitterService.despachar(n.getBoticaId(), n.getRolDestinatario(), resp);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alLlegarAStockCritico(StockCriticoEvent event) {
        String mensaje = String.format(
                "El producto %s ha llegado a un nivel crítico: solo quedan %d unidades disponibles.",
                event.nombreProducto(),
                event.stockRestante()
        );

        Notificacion n = Notificacion.builder()
                .boticaId(event.boticaId())
                .rolDestinatario(null) // Para toda la botica
                .tipo("STOCK_CRITICO")
                .titulo("Stock crítico: " + event.nombreProducto())
                .mensaje(mensaje)
                .leido(false)
                .build();

        n = notificacionDao.insertar(n);

        NotificacionResponse resp = new NotificacionResponse(
                n.getId(),
                n.getTipo(),
                n.getTitulo(),
                n.getMensaje(),
                n.isLeido(),
                n.getFechaCreacion()
        );

        sseEmitterService.despachar(n.getBoticaId(), n.getRolDestinatario(), resp);
    }
}
