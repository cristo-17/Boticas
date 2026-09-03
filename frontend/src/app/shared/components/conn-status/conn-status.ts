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
}
