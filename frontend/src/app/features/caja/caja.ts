import { Component, OnInit, computed, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { ChipComponent } from '../../shared/components/chip/chip';
import { AuthService } from '../../core/services/auth.service';
import { CajaService } from '../../core/services/caja.service';
import { AperturaComponent } from './apertura/apertura';
import { CierreComponent } from './cierre/cierre';

type TabCaja = 'apertura' | 'cierre';

/**
 * La pantalla de caja muestra la apertura/cierre de caja del día, según el estado de la caja.
 */
@Component({
  selector: 'app-caja',
  imports: [ChipComponent, AperturaComponent, CierreComponent],
  templateUrl: './caja.html',
  styleUrl: './caja.scss',
})
export class CajaScreen implements OnInit {
  private readonly caja = inject(CajaService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly cajaActual = this.caja.cajaActual;

  private readonly queryParams = toSignal(this.route.queryParamMap, {
    initialValue: this.route.snapshot.queryParamMap,
  });
  readonly tab = computed<TabCaja>(() =>
    this.queryParams().get('tab') === 'cierre' ? 'cierre' : 'apertura',
  );

  readonly fechaTexto = new Date().toLocaleDateString('es-PE', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  });

  readonly usuarioTexto = computed(() => {
    const usuario = this.auth.usuarioActual();
    return usuario ? `${usuario.nombre} · ${usuario.rol}` : 'Sin sesión';
  });

  ngOnInit(): void {
    this.caja.obtenerCajaDeHoy().subscribe();
  }

  irA(tab: TabCaja): void {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { tab },
      queryParamsHandling: 'merge',
    });
  }
}
