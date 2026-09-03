import { Component, Input } from '@angular/core';

export type BadgeVariant = 'ok' | 'warn' | 'danger' | 'info' | 'neutral' | 'solid';

@Component({
  selector: 'app-badge',
  templateUrl: './badge.html',
})
export class BadgeComponent {
  @Input() variant: BadgeVariant = 'neutral';
}
