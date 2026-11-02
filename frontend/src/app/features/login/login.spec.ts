import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { LoginScreen } from './login';
import { errorInterceptor } from '../../core/interceptors/error.interceptor';
import { environment } from '../../../environments/environment';

const LOGIN_URL = `${environment.apiUrl}/auth/login`;

describe('LoginScreen (DIF-01)', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [LoginScreen],
      providers: [provideHttpClient(withInterceptors([errorInterceptor])), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('401 CREDENCIALES_INVALIDAS muestra el error sin contador local de intentos restantes', () => {
    const fixture = TestBed.createComponent(LoginScreen);
    fixture.detectChanges();
    const pantalla = fixture.componentInstance;

    pantalla.form.setValue({ usuario: 'rosa.quispe', password: 'mala', turno: 'Tarde' });
    pantalla.ingresar();

    http.expectOne(LOGIN_URL).flush(
      { error: 'CREDENCIALES_INVALIDAS', mensaje: 'Usuario o contraseña incorrectos' },
      { status: 401, statusText: 'Unauthorized' },
    );
    fixture.detectChanges();

    const banner = fixture.nativeElement.querySelector('.login__error-banner')?.textContent ?? '';
    expect(banner).toContain('Usuario o contraseña incorrectos.');
    expect(banner).not.toContain('Te quedan');
  });

  it('423 CUENTA_BLOQUEADA muestra el mensaje del servidor ("Cuenta bloqueada hasta las HH:MM")', () => {
    const fixture = TestBed.createComponent(LoginScreen);
    fixture.detectChanges();
    const pantalla = fixture.componentInstance;

    pantalla.form.setValue({ usuario: 'rosa.quispe', password: 'mala', turno: 'Tarde' });
    pantalla.ingresar();

    http.expectOne(LOGIN_URL).flush(
      { error: 'CUENTA_BLOQUEADA', mensaje: 'Cuenta bloqueada hasta las 10:15' },
      { status: 423, statusText: 'Locked' },
    );
    fixture.detectChanges();

    const banner = fixture.nativeElement.querySelector('.login__error-banner')?.textContent ?? '';
    expect(banner).toContain('Cuenta bloqueada hasta las 10:15');
    expect(pantalla.cuentaBloqueada()).toBe(true);
  });
});
