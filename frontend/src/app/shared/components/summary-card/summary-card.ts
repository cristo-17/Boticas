import { Component, Input } from '@angular/core';

export type SummaryCardVariant = 'default' | 'warn' | 'danger' | 'info';

@Component({
  selector: 'app-summary-card',
  templateUrl: './summary-card.html',
})
export class SummaryCardComponent {
  @Input() label = '';
  @Input() value = '';
  @Input() note = '';
  @Input() variant: SummaryCardVariant = 'default';

  get variantClass(): string {
    return this.variant === 'default' ? '' : `bs-card-summary--${this.variant}`;
  }
}
