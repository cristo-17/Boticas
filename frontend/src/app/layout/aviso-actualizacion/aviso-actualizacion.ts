import { Component, inject } from '@angular/core';
import { ActualizacionAppService } from '../../core/services/actualizacion-app.service';

/**
 * "Hay una versión nueva · Actualizar". Solo aparece cuando la versión nueva
 * llegó en medio de una venta o de un envío de la cola (si no, la app recarga
 * sola): recarga recién cuando la persona lo toca.
 */
@Component({
  selector: 'app-aviso-actualizacion',
  template: `
    @if (actualizacion.hayVersionNueva()) {
      <div
        class="bs-conn-banner bs-conn-banner--sync aviso-actualizacion"
        role="status"
        data-testid="aviso-actualizacion"
      >
        <span class="aviso-actualizacion__texto">Hay una versión nueva</span>
        <button
          type="button"
          class="aviso-actualizacion__boton"
          (click)="actualizacion.actualizarAhora()"
        >
          Actualizar
        </button>
      </div>
    }
  `,
  styles: `
    .aviso-actualizacion {
      justify-content: space-between;
    }
    .aviso-actualizacion__boton {
      min-height: var(--tap);
      padding: 0 var(--s-4);
      border: 1px solid var(--azul-500);
      border-radius: var(--r-pill);
      background: var(--azul-500);
      color: #fff;
      font-weight: var(--fw-bold);
      font-size: var(--fs-sm);
    }
  `,
})
export class AvisoActualizacionComponent {
  readonly actualizacion = inject(ActualizacionAppService);
}
