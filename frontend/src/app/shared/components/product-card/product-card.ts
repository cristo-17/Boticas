import { Component, EventEmitter, Input, Output } from '@angular/core';
import { BadgeComponent, BadgeVariant } from '../badge/badge';

@Component({
  selector: 'app-product-card',
  imports: [BadgeComponent],
  templateUrl: './product-card.html',
})
export class ProductCardComponent {
  @Input() name = '';
  @Input() meta = '';
  @Input() price = '';
  @Input() badgeLabel: string | null = null;
  @Input() badgeVariant: BadgeVariant = 'neutral';
  @Input() flat = true;

  @Output() selected = new EventEmitter<void>();
}
