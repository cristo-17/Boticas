import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { ButtonComponent } from '../../shared/components/button/button';
import { FieldComponent } from '../../shared/components/field/field';
import { ConnStatusComponent } from '../../shared/components/conn-status/conn-status';
import { Turno } from '../../core/models/usuario.model';
import { AuthService } from '../../core/services/auth.service';
import { ConexionService } from '../../core/services/conexion.service';

const TURNOS: { valor: Turno; horas: string }[] = [
  { valor: 'Mañana', horas: '07:00–14:00' },
  { valor: 'Tarde', horas: '14:00–22:00' },
  { valor: 'Noche', horas: '22:00–07:00' },
];

const ETIQUETAS_CONEXION = {
  online: 'En línea',
  sync: 'Sincronizando',
  offline: 'Sin conexión',
} as const;

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, ButtonComponent, FieldComponent, ConnStatusComponent],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class LoginScreen {
  private readonly auth = inject(AuthService);
  private readonly conexion = inject(ConexionService);
  private readonly router = inject(Router);

  readonly turnos = TURNOS;
  readonly estadoConexion = this.conexion.estado;

  get etiquetaConexion(): string {
    return ETIQUETAS_CONEXION[this.estadoConexion()];
  }

  readonly form = new FormGroup({
    usuario: new FormControl('', { nonNullable: true, validators: Validators.required }),
    password: new FormControl('', { nonNullable: true, validators: Validators.required }),
    turno: new FormControl<Turno>('Tarde', { nonNullable: true, validators: Validators.required }),
  });

  readonly iniciando = signal(false);
  readonly credencialesInvalidas = signal(false);
  readonly intentosRestantes = signal(3);

  seleccionarTurno(turno: Turno): void {
    this.form.controls.turno.setValue(turno);
  }

  ingresar(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) return;

    this.credencialesInvalidas.set(false);
    this.iniciando.set(true);
    const { usuario, password, turno } = this.form.getRawValue();
    this.auth.iniciarSesion({ usuario, password, turno }).subscribe({
      next: () => {
        this.iniciando.set(false);
        this.router.navigateByUrl('/alertas');
      },
      error: () => {
        this.iniciando.set(false);
        this.credencialesInvalidas.set(true);
        this.intentosRestantes.update((n) => Math.max(0, n - 1));
      },
    });
  }
}
