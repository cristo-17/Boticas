import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ButtonComponent } from '../button/button';

@Component({
  selector: 'app-empty-state',
  imports: [ButtonComponent],
  templateUrl: './empty-state.html',
})
export class EmptyStateComponent {
  @Input() title = '';
  @Input() text = '';
  @Input() actionLabel: string | null = null;

  @Output() actionClick = new EventEmitter<void>();
}
