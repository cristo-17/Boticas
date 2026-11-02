import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { filter, map } from 'rxjs/operators';
import { AuthService } from '../../core/services/auth.service';
import { navItemsDe, repartirBarraMovil } from '../nav-items';

/** Barra inferior móvil: como mucho 5 accesos; el resto, detrás de "Más". */
@Component({
  selector: 'app-bottom-nav',
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './bottom-nav.html',
  styleUrl: './bottom-nav.scss',
  host: { '(document:keydown.escape)': 'cerrarMas()' },
})
export class BottomNavComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  private readonly reparto = computed(() => repartirBarraMovil(navItemsDe(this.auth.usuarioActual()?.rol)));
  readonly barra = computed(() => this.reparto().barra);
  readonly mas = computed(() => this.reparto().mas);

  readonly masAbierto = signal(false);

  private readonly url = toSignal(
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      map((e) => e.urlAfterRedirects),
    ),
    { initialValue: this.router.url },
  );

  /** "Más" se marca activo si la pantalla actual es una de las que esconde. */
  readonly masActivo = computed(() => this.mas().some((i) => this.url().startsWith(i.path)));

  alternarMas(): void {
    this.masAbierto.update((v) => !v);
  }

  cerrarMas(): void {
    this.masAbierto.set(false);
  }
}
