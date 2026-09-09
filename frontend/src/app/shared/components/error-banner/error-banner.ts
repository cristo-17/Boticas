import { Component, Input } from '@angular/core';

/**
 * Muestra el signal `error` de un servicio (regla de frontend, CLAUDE.md:
 * "toda pantalla que llama a un servicio muestra el estado de error de
 * ese servicio"). A diferencia de app-toast, no se autooculta: se queda
 * mientras el servicio siga en error, y desaparece cuando el propio
 * servicio lo limpia (siguiente intento exitoso).
 */
@Component({
  selector: 'app-error-banner',
  templateUrl: './error-banner.html',
})
export class ErrorBannerComponent {
  @Input() mensaje: string | null = null;
}
