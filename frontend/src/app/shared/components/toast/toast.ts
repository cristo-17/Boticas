import {
  Component,
  EventEmitter,
  Input,
  OnChanges,
  OnDestroy,
  Output,
  SimpleChanges,
} from '@angular/core';

export type ToastVariant = 'success' | 'error' | 'offline';

@Component({
  selector: 'app-toast',
  templateUrl: './toast.html',
})
export class ToastComponent implements OnChanges, OnDestroy {
  @Input() message = '';
  @Input() variant: ToastVariant = 'success';
  @Input() visible = false;
  @Input() duration = 2600;

  @Output() closed = new EventEmitter<void>();

  private timer: ReturnType<typeof setTimeout> | null = null;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['visible']) {
      this.clearTimer();
      if (this.visible) {
        this.timer = setTimeout(() => this.closed.emit(), this.duration);
      }
    }
  }

  ngOnDestroy(): void {
    this.clearTimer();
  }

  private clearTimer(): void {
    if (this.timer) {
      clearTimeout(this.timer);
      this.timer = null;
    }
  }
}
