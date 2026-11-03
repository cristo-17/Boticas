import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CarritoPanelComponent } from './carrito-panel';
import { LineaCarrito } from '../services/carrito.service';

const LINEA: LineaCarrito = {
  key: '1|10', productoId: 1, presentacionId: 10, nombre: 'Paracetamol', presentacion: 'Caja',
  precioUnitario: 25, cantidad: 1, alerta: null, origenCaptura: 'BUSQUEDA', requiereReceta: false,
};

/** DIF-07 (Tarea 0B-B): vaciar con confirmación y desglose Op. gravada / IGV / Total. */
describe('CarritoPanelComponent', () => {
  let fixture: ComponentFixture<CarritoPanelComponent>;
  const el = () => fixture.nativeElement as HTMLElement;
  const botonVaciar = () =>
    Array.from(el().querySelectorAll('button')).find((b) => b.textContent?.includes('Vaciar carrito'));

  beforeEach(async () => {
    TestBed.configureTestingModule({ imports: [CarritoPanelComponent] });
    fixture = TestBed.createComponent(CarritoPanelComponent);
    fixture.componentRef.setInput('lineas', [LINEA]);
    fixture.componentRef.setInput('opGravada', 21.19);
    fixture.componentRef.setInput('igv', 3.81);
    fixture.componentRef.setInput('total', 25);
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('muestra Op. gravada, IGV y Total, y ningún "Subtotal"', () => {
    const texto = el().textContent ?? '';
    expect(texto).toContain('Op. gravada');
    expect(texto).toContain('S/ 21.19');
    expect(texto).toContain('S/ 3.81');
    expect(texto).toContain('S/ 25.00');
    expect(texto).not.toContain('Subtotal');
  });

  it('"Vaciar carrito" pide confirmación antes de emitir', async () => {
    let vaciado = 0;
    fixture.componentInstance.vaciar.subscribe(() => vaciado++);

    botonVaciar()!.click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(vaciado).toBe(0);
    expect(el().textContent).toContain('¿Vaciar el carrito?');

    fixture.componentInstance.confirmarVaciar();
    expect(vaciado).toBe(1);
  });

  it('con el carrito vacío no ofrece vaciar', async () => {
    fixture.componentRef.setInput('lineas', []);
    fixture.detectChanges();
    await fixture.whenStable();
    expect(botonVaciar()).toBeUndefined();
  });
});
