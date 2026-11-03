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

  describe('plegado en el celular (Móvil y tablet, P1)', () => {
    const host = () => fixture.nativeElement as HTMLElement;
    const barra = () => host().querySelector('.carrito-panel__barra') as HTMLElement;
    const botonCobrarBarra = () => barra().querySelector('.carrito-panel__barra-cobrar button') as HTMLButtonElement | null;

    it('empieza plegado: el host no tiene la clase de expandido y la barra resume líneas y total', () => {
      expect(fixture.componentInstance.expandido()).toBe(false);
      expect(host().classList).not.toContain('carrito-panel--expandido');
      expect(barra().textContent).toContain('1 línea · S/ 25.00');
      expect(botonCobrarBarra()?.textContent).toContain('Cobrar');
    });

    it('plural y vacío: "3 líneas · S/ …"; sin líneas dice "Carrito vacío" y no ofrece Cobrar', async () => {
      fixture.componentRef.setInput('lineas', [LINEA, { ...LINEA, key: '2|20' }, { ...LINEA, key: '3|30' }]);
      fixture.componentRef.setInput('total', 75.7);
      fixture.detectChanges();
      expect(barra().textContent).toContain('3 líneas · S/ 75.70');

      fixture.componentRef.setInput('lineas', []);
      fixture.detectChanges();
      await fixture.whenStable();
      expect(barra().textContent).toContain('Carrito vacío');
      expect(botonCobrarBarra()).toBeNull();
    });

    it('tocar el resumen abre la hoja; "Ocultar" la vuelve a plegar', async () => {
      (barra().querySelector('.carrito-panel__barra-resumen') as HTMLButtonElement).click();
      fixture.detectChanges();
      expect(host().classList).toContain('carrito-panel--expandido');

      (host().querySelector('.carrito-panel__toggle') as HTMLButtonElement).click();
      fixture.detectChanges();
      expect(host().classList).not.toContain('carrito-panel--expandido');
    });

    it('"Cobrar" de la barra NO cobra: abre la hoja con el foco en el método de pago elegido', async () => {
      let cobros = 0;
      fixture.componentInstance.cobrar.subscribe(() => cobros++);
      fixture.componentInstance.metodoSeleccionado.set('yape');
      fixture.detectChanges();

      botonCobrarBarra()!.click();
      fixture.detectChanges();
      await fixture.whenStable();

      expect(cobros).toBe(0);
      expect(host().classList).toContain('carrito-panel--expandido');
      expect((document.activeElement as HTMLElement).textContent).toContain('Yape');
    });

    it('el nombre de cada línea está en el DOM de la hoja (P3)', () => {
      expect(host().querySelector('.carrito-panel__linea-nombre')?.textContent).toContain('Paracetamol');
    });
  });
});
