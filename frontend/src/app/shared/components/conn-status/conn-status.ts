import { Component, Input } from '@angular/core';

export type ConnState = 'online' | 'sync' | 'offline';

@Component({
  selector: 'app-conn-status',
  templateUrl: './conn-status.html',
})
export class ConnStatusComponent {
  @Input() mode: 'chip' | 'banner' = 'chip';
  @Input() state: ConnState = 'online';
  @Input() label = '';
  @Input() message = '';
  /** Debajo de 1024 px el chip queda en el punto de color; el texto sigue para lectores de pantalla (encabezado en una fila). */
  @Input() compactoEnMovil = false;
}
