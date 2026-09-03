import { Component, Input } from '@angular/core';

export type SkeletonVariant = 'line' | 'line-sm' | 'block';

@Component({
  selector: 'app-skeleton',
  templateUrl: './skeleton.html',
})
export class SkeletonComponent {
  @Input() variant: SkeletonVariant = 'line';
  @Input() count = 1;

  get items(): number[] {
    return Array.from({ length: this.count });
  }
}
