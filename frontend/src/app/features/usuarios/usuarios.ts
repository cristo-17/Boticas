import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged, switchMap } from 'rxjs/operators';
import { BadgeComponent } from '../../shared/components/badge/badge';
import { ButtonComponent } from '../../shared/components/button/button';
import { CardComponent } from '../../shared/components/card/card';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state';
import { ErrorBannerComponent } from '../../shared/components/error-banner/error-banner';
import { PaginacionComponent } from '../../shared/components/paginacion/paginacion';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton';
import { TableComponent } from '../../shared/components/table/table';
import { ActualizarUsuarioRequest, CrearUsuarioRequest, Rol, UsuarioAdmin } from '../../core/models/usuario.model';
import { AuthService } from '../../core/services/auth.service';
import { UsuarioService } from '../../core/services/usuario.service';

const TAMANO_PAGINA = 20;

/** Administración de usuarios y bloqueo de login, solo ADMINISTRADOR (Tarea 17, Bloque B; R17). */
@Component({
  selector: 'app-usuarios',
  imports: [
    BadgeComponent,
    ButtonComponent,
    CardComponent,
    EmptyStateComponent,
    ErrorBannerComponent,
    PaginacionComponent,
    SkeletonComponent,
    TableComponent,
  ],
  templateUrl: './usuarios.html',
  styleUrl: './usuarios.scss',
})
export class UsuariosScreen implements OnInit {
  private readonly usuarioService = inject(UsuarioService);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  readonly usuarios = this.usuarioService.usuarios;
  readonly pagina = this.usuarioService.pagina;
  readonly cargando = this.usuarioService.cargando;
  readonly error = this.usuarioService.error;
  readonly guardando = this.usuarioService.guardando;
  readonly errorGuardado = this.usuarioService.errorGuardado;
  readonly miId = computed(() => this.auth.usuarioActual()?.id ?? null);

  readonly busqueda = signal('');
  private readonly paginaActual = signal(0);
  private readonly busqueda$ = new Subject<string>();
  private readonly cargas$ = new Subject<void>();

  readonly vacio = computed(() => !this.cargando() && !this.error() && this.usuarios().length === 0);

  constructor() {
    this.cargas$
      .pipe(
        switchMap(() => this.usuarioService.listar(this.busqueda().trim(), this.paginaActual(), TAMANO_PAGINA)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({ error: () => {} });
    this.busqueda$
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.paginaActual.set(0);
        this.cargas$.next();
      });
  }

  ngOnInit(): void {
    this.cargas$.next();
  }

  onBusquedaInput(event: Event): void {
    const valor = (event.target as HTMLInputElement).value;
    this.busqueda.set(valor);
    this.busqueda$.next(valor.trim());
  }

  irAPagina(pagina: number): void {
    this.paginaActual.set(Math.max(0, pagina));
    this.cargas$.next();
  }

  recargar(): void {
    this.cargas$.next();
  }

  etiquetaRol(rol: Rol): string {
    return rol === 'ADMINISTRADOR' ? 'Administrador' : 'Técnico';
  }

  horaBloqueo(bloqueadoHasta: string | null): string {
    if (!bloqueadoHasta) return '';
    return new Intl.DateTimeFormat('es-PE', {
      hour: '2-digit',
      minute: '2-digit',
      hour12: false,
      timeZone: 'America/Lima',
    }).format(new Date(bloqueadoHasta));
  }

  esMiUsuario(u: UsuarioAdmin): boolean {
    return this.miId() === u.id;
  }

  // --- Acciones rápidas: desbloquear y activar/desactivar ---
  readonly usuarioEnAccion = signal<number | null>(null);
  readonly confirmarDesactivar = signal<UsuarioAdmin | null>(null);

  desbloquear(u: UsuarioAdmin): void {
    this.usuarioService.limpiarErrorGuardado();
    this.usuarioEnAccion.set(u.id);
    this.usuarioService.desbloquear(u.id).subscribe({
      next: () => this.usuarioEnAccion.set(null),
      error: () => this.usuarioEnAccion.set(null),
    });
  }

  solicitarCambioEstado(u: UsuarioAdmin): void {
    if (this.esMiUsuario(u)) return;
    this.usuarioService.limpiarErrorGuardado();
    if (u.activo && u.tieneCajaAbierta) {
      this.confirmarDesactivar.set(u);
      return;
    }
    this.ejecutarCambioEstado(u, !u.activo);
  }

  confirmarDesactivacionConCaja(): void {
    const u = this.confirmarDesactivar();
    if (!u) return;
    this.confirmarDesactivar.set(null);
    this.ejecutarCambioEstado(u, false);
  }

  cancelarDesactivacion(): void {
    this.confirmarDesactivar.set(null);
  }

  private ejecutarCambioEstado(u: UsuarioAdmin, activo: boolean): void {
    this.usuarioEnAccion.set(u.id);
    const request: ActualizarUsuarioRequest = { nombre: u.nombre, rol: u.rol, activo };
    this.usuarioService.actualizar(u.id, request).subscribe({
      next: () => this.usuarioEnAccion.set(null),
      error: () => this.usuarioEnAccion.set(null),
    });
  }

  // --- Modal de alta / edición ---
  readonly formularioAbierto = signal(false);
  readonly editandoUsuario = signal<UsuarioAdmin | null>(null);
  readonly nombre = signal('');
  readonly usuarioLogin = signal('');
  readonly password = signal('');
  readonly rol = signal<Rol>('TECNICO');
  readonly activo = signal(true);
  readonly intentoGuardar = signal(false);

  readonly avisoCajaEnEdicion = computed(() => {
    const u = this.editandoUsuario();
    return u !== null && u.tieneCajaAbierta && !this.activo();
  });

  readonly erroresFormulario = computed<string[]>(() => {
    const errores: string[] = [];
    if (!this.nombre().trim()) errores.push('Falta el nombre completo.');
    if (this.editandoUsuario() === null) {
      if (!this.usuarioLogin().trim()) errores.push('Falta el usuario de acceso.');
      if (this.password().length < 8) errores.push('La contraseña debe tener al menos 8 caracteres.');
    }
    return errores;
  });

  abrirNuevo(): void {
    this.usuarioService.limpiarErrorGuardado();
    this.editandoUsuario.set(null);
    this.nombre.set('');
    this.usuarioLogin.set('');
    this.password.set('');
    this.rol.set('TECNICO');
    this.activo.set(true);
    this.intentoGuardar.set(false);
    this.formularioAbierto.set(true);
  }

  abrirEdicion(u: UsuarioAdmin): void {
    this.usuarioService.limpiarErrorGuardado();
    this.editandoUsuario.set(u);
    this.nombre.set(u.nombre);
    this.usuarioLogin.set(u.usuario);
    this.password.set('');
    this.rol.set(u.rol);
    this.activo.set(u.activo);
    this.intentoGuardar.set(false);
    this.formularioAbierto.set(true);
  }

  cerrarFormulario(): void {
    this.formularioAbierto.set(false);
  }

  onRolChange(event: Event): void {
    const valor = (event.target as HTMLSelectElement).value as Rol;
    this.rol.set(valor === 'ADMINISTRADOR' ? 'ADMINISTRADOR' : 'TECNICO');
  }

  guardar(): void {
    if (this.erroresFormulario().length > 0) {
      this.intentoGuardar.set(true);
      return;
    }
    const actual = this.editandoUsuario();
    if (actual === null) {
      const request: CrearUsuarioRequest = {
        nombre: this.nombre().trim(),
        usuario: this.usuarioLogin().trim(),
        password: this.password(),
        rol: this.rol(),
      };
      this.usuarioService.crear(request).subscribe({
        next: () => {
          this.formularioAbierto.set(false);
          this.recargar();
        },
        error: () => {},
      });
    } else {
      const request: ActualizarUsuarioRequest = {
        nombre: this.nombre().trim(),
        rol: this.rol(),
        activo: this.activo(),
      };
      this.usuarioService.actualizar(actual.id, request).subscribe({
        next: () => this.formularioAbierto.set(false),
        error: () => {},
      });
    }
  }

  // --- Modal de restablecer contraseña ---
  readonly usuarioClave = signal<UsuarioAdmin | null>(null);
  readonly nuevaClave = signal('');
  readonly intentoClave = signal(false);
  readonly errorClaveLocal = computed(() =>
    this.nuevaClave().length < 8 ? 'La nueva contraseña debe tener al menos 8 caracteres.' : null,
  );

  abrirRestablecerClave(u: UsuarioAdmin): void {
    this.usuarioService.limpiarErrorGuardado();
    this.usuarioClave.set(u);
    this.nuevaClave.set('');
    this.intentoClave.set(false);
  }

  cerrarRestablecerClave(): void {
    this.usuarioClave.set(null);
  }

  guardarNuevaClave(): void {
    const u = this.usuarioClave();
    if (!u) return;
    if (this.errorClaveLocal()) {
      this.intentoClave.set(true);
      return;
    }
    this.usuarioService.restablecerClave(u.id, this.nuevaClave()).subscribe({
      next: () => this.usuarioClave.set(null),
      error: () => {},
    });
  }
}
