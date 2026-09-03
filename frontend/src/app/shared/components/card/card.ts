import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-card',
  templateUrl: './card.html',
})
export class CardComponent {
  @Input() flat = false;
}
