import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { HeaderColaComponent } from './header-cola';
import { ColaVentasService } from '../../core/services/cola-ventas.service';
import { ConexionService } from '../../core/services/conexion.service';
import { EstadoConexion } from '../../core/models/conexion.model';
import { EntradaCola } from '../../core/models/offline.model';
import { colaFalsa } from './cola-falsa.testing';

function entrada(clave: string): EntradaCola {
  return {
    claveIdempotencia: clave,
    orden: 1,
    usuarioId: 1,
    usuarioNombre: 'Rosa Quispe',
    boticaId: 1,
    cajaId: 10,
    horaDispositivo: '2026-11-03T14:05:00-05:00',
    sinConexion: true,
    estado: 'PENDIENTE',
    ultimoError: null,
    cuerpo: { claveIdempotencia: clave, items: [], metodoPago: 'efectivo' },
    productos: ['Paracetamol 500 mg × 1 Caja'],
    unidadesPorProducto: {},
  };
}

describe('HeaderColaComponent (Tarea 22, §7)', () => {
  let estado: ReturnType<typeof signal<EstadoConexion>>;
  let propias: ReturnType<typeof signal<EntradaCola[]>>;

  beforeEach(() => {
    estado = signal<EstadoConexion>('online');
    propias = signal<EntradaCola[]>([]);
    TestBed.configureTestingModule({
      imports: [HeaderColaComponent],
      providers: [
        { provide: ConexionService, useValue: { estado, enLinea: () => estado() !== 'offline', comprobar: async () => true } },
        { provide: ColaVentasService, useValue: colaFalsa(propias) },
      ],
    });
  });

  it('la etiqueta sigue al estado real y suma cuántas ventas propias faltan enviar', () => {
    const c = TestBed.createComponent(HeaderColaComponent).componentInstance;
    expect(c.etiqueta()).toBe('En línea');
    estado.set('offline');
    expect(c.etiqueta()).toBe('Sin conexión');
    propias.set([entrada('a'), entrada('b')]);
    expect(c.etiqueta()).toBe('Sin conexión · 2 sin enviar');
    estado.set('sync');
    expect(c.etiqueta()).toBe('Enviando… · 2 sin enviar');
  });

  it('el panel lista productos y estado SIN montos (conteo ciego)', () => {
    propias.set([entrada('a')]);
    const fixture = TestBed.createComponent(HeaderColaComponent);
    fixture.componentInstance.alternar();
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const fila = el.querySelector('[data-testid="fila-cola"]') as HTMLElement;
    expect(fila.textContent).toContain('Paracetamol 500 mg');
    expect(fila.textContent).toContain('Esperando conexión');
    expect(el.querySelector('.header-cola__panel')?.textContent).not.toContain('S/');
  });
});
