import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RecetaModalComponent } from './receta-modal';
import { RecetaVenta } from '../../../core/models/venta.model';

/** Tarea 20 (D-E2-4): número y CMP obligatorios; médico y fecha opcionales; la fecha no puede ser futura. */
describe('RecetaModalComponent', () => {
  let fixture: ComponentFixture<RecetaModalComponent>;
  let emitidas: RecetaVenta[];
  const el = () => fixture.nativeElement as HTMLElement;

  function escribir(id: string, valor: string): void {
    const input = el().querySelector<HTMLInputElement>(`#${id}`)!;
    input.value = valor;
    input.dispatchEvent(new Event('input'));
  }

  function cobrar(): void {
    const boton = Array.from(el().querySelectorAll('button')).find((b) => b.textContent?.includes('Cobrar con receta'));
    boton!.click();
    fixture.detectChanges();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [RecetaModalComponent] });
    fixture = TestBed.createComponent(RecetaModalComponent);
    fixture.componentRef.setInput('abierto', true);
    fixture.componentRef.setInput('productos', ['Amoxicilina 500 mg']);
    emitidas = [];
    fixture.componentInstance.confirmado.subscribe((r) => emitidas.push(r));
    fixture.detectChanges();
  });

  it('nombra los productos que piden receta', () => {
    expect(el().textContent).toContain('Amoxicilina 500 mg');
  });

  it('sin número ni CMP válido no emite y explica qué falta', () => {
    escribir('receta-cmp', '12A');
    cobrar();
    expect(emitidas).toEqual([]);
    expect(el().textContent).toContain('Falta el número de receta.');
    expect(el().textContent).toContain('El CMP del médico son de 1 a 6 dígitos.');
  });

  it('con fecha de emisión futura no emite', () => {
    escribir('receta-numero', 'R-1');
    escribir('receta-cmp', '1234');
    escribir('receta-fecha', '2999-01-01');
    cobrar();
    expect(emitidas).toEqual([]);
    expect(el().textContent).toContain('no puede ser futura');
  });

  it('con datos válidos emite recortados; lo opcional vacío va como null', () => {
    escribir('receta-numero', '  RX-15 ');
    escribir('receta-cmp', ' 54321 ');
    cobrar();
    expect(emitidas).toEqual([{ numero: 'RX-15', cmp: '54321', medicoNombre: null, fechaEmision: null }]);
  });
});
