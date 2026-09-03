import { Component, EventEmitter, Input, Output } from '@angular/core';

export type ButtonVariant = 'primary' | 'action' | 'secondary' | 'danger' | 'ghost';

@Component({
  selector: 'app-button',
  templateUrl: './button.html',
})
export class ButtonComponent {
  @Input() variant: ButtonVariant = 'primary';
  @Input() size: 'default' | 'lg' = 'default';
  @Input() block = false;
  @Input() icon = false;
  @Input() disabled = false;
  @Input() loading = false;
  @Input() type: 'button' | 'submit' | 'reset' = 'button';
  @Input() ariaLabel: string | null = null;

  @Output() clicked = new EventEmitter<void>();

  get variantClass(): string {
    return `bs-btn-${this.variant}`;
  }

  onClick(): void {
    if (this.disabled || this.loading) {
      return;
    }
    this.clicked.emit();
  }
}
