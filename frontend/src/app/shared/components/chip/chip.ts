import { Component, EventEmitter, Input, Output } from '@angular/core';

export type ChipVariant = 'default' | 'danger';

@Component({
  selector: 'app-chip',
  templateUrl: './chip.html',
})
export class ChipComponent {
  @Input() activo = false;
  @Input() variant: ChipVariant = 'default';
  @Input() disabled = false;

  @Output() clicked = new EventEmitter<void>();

  onClick(): void {
    if (this.disabled) return;
    this.clicked.emit();
  }
}
