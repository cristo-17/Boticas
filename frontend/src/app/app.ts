import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { AvisoActualizacionComponent } from './layout/aviso-actualizacion/aviso-actualizacion';
import { ActualizacionAppService } from './core/services/actualizacion-app.service';

@Component({
  imports: [RouterOutlet, AvisoActualizacionComponent],
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App {
  constructor() {
    // PWA: busca versiones nuevas y recarga cuando no interrumpe una venta (ActualizacionAppService).
    inject(ActualizacionAppService).iniciar();
  }
}
