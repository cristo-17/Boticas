import { TestBed } from '@angular/core/testing';
import { NO_ERRORS_SCHEMA, signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { RouterOutlet, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { Shell } from './shell';
import { ErrorBannerComponent } from '../../shared/components/error-banner/error-banner';
import { AlertaService } from '../../core/services/alerta.service';
import { ConexionService } from '../../core/services/conexion.service';
import { ConfigService } from '../../core/services/config.service';
import { ConfigNegocio } from '../../core/models/config.model';
import { environment } from '../../../environments/environment';

const URL_CONFIG = `${environment.apiUrl}/config`;

const CONFIG: ConfigNegocio = {
  igv: 0.18, umbralStockBajo: 15, descuadreLeve: 10,
  motivosMerma: [], motivosQueRequierenObservacion: [],
  vencimiento: { criticoDias: 30, advertenciaDias: 90 },
};

/** DIF-03 (Tarea 0B-B): si /api/config no cargó al iniciar, se avisa y se vuelve a pedir después del login. */
describe('Shell — configuración de negocio', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [Shell],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AlertaService, useValue: { listarAlertas: () => of([]) } },
        { provide: ConexionService, useValue: { estado: signal('online') } },
      ],
    });
    // Header, navegación y banner de conexión no son lo que se prueba acá.
    TestBed.overrideComponent(Shell, {
      set: { imports: [RouterOutlet, ErrorBannerComponent], schemas: [NO_ERRORS_SCHEMA] },
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function crear() {
    const fixture = TestBed.createComponent(Shell);
    fixture.detectChanges();
    await fixture.whenStable();
    return fixture;
  }

  it('si la config no cargó al iniciar, la pide de nuevo y avisa mientras siga fallando', async () => {
    const fixture = await crear();

    http.expectOne(URL_CONFIG).flush(null, { status: 0, statusText: 'Unknown Error' });
    fixture.detectChanges();
    await fixture.whenStable();

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('No se pudo cargar la configuración');
    expect(texto).toContain('valores por defecto');
  });

  it('si el reintento carga, no queda ningún aviso y la config queda disponible', async () => {
    const fixture = await crear();

    http.expectOne(URL_CONFIG).flush(CONFIG);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(TestBed.inject(ConfigService).config()).toEqual(CONFIG);
    expect((fixture.nativeElement as HTMLElement).querySelector('[role="alert"]')).toBeNull();
  });

  it('si la config ya estaba cargada, no la vuelve a pedir', async () => {
    const config = TestBed.inject(ConfigService);
    config.cargar().subscribe();
    http.expectOne(URL_CONFIG).flush(CONFIG);

    await crear();
    http.expectNone(URL_CONFIG);
  });
});
