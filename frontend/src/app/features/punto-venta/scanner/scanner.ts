import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ButtonComponent } from '../../../shared/components/button/button';

export type EstadoEscaneo = 'idle' | 'buscando' | 'encontrado' | 'error';

const MENSAJES: Record<EstadoEscaneo, string> = {
  idle: 'Apunta al código de barras',
  buscando: 'Buscando código…',
  encontrado: 'Producto encontrado',
  error: 'Código no legible',
};

@Component({
  selector: 'app-scanner',
  imports: [ButtonComponent],
  templateUrl: './scanner.html',
  styleUrl: './scanner.scss',
})
export class ScannerComponent {
  @Input() estado: EstadoEscaneo = 'idle';
  @Input() codigo: string | null = null;
  @Input() buscando = false;

  @Output() simularEscaneo = new EventEmitter<void>();
  @Output() simularFallo = new EventEmitter<void>();

  get mensaje(): string {
    return MENSAJES[this.estado];
  }

  get barriendo(): boolean {
    return this.estado === 'idle' || this.estado === 'buscando';
  }
}
