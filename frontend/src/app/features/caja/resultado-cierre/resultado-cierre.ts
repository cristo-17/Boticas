import { Component, computed, inject, input } from '@angular/core';
import { CardComponent } from '../../../shared/components/card/card';
import { CajaDiaria } from '../../../core/models/caja.model';
import { ConfigService } from '../../../core/services/config.service';
import { formatearMoneda } from '../../../core/utils/moneda.util';

type TonoDescuadre = 'ok' | 'warn' | 'danger';

export interface ResultadoCierre {
  tono: TonoDescuadre;
  titulo: string;
  montoTexto: string;
  nota: string;
  montoEsperado: number;
  montoContado: number;
}

/** Fallback si /api/config no llegó a cargar todavía (no debería pasar: provideAppInitializer la espera antes de arrancar la app). */
export const UMBRAL_DESCUADRE_LEVE_DEFECTO = 10;

/**
 * Solo con la respuesta de POST /api/caja/cerrar: una caja abierta da null
 * (conteo ciego -- el esperado no existe hasta después de contar).
 */
export function resultadoDeCierre(caja: CajaDiaria | null, umbralLeve: number): ResultadoCierre | null {
  if (!caja || caja.abierta || !caja.semaforoDescuadre) return null;
  const esperado = caja.montoEsperado ?? 0;
  const contado = caja.montoContado ?? 0;
  const d = caja.diferencia ?? 0;
  const signo = d < 0 ? '−' : d > 0 ? '+' : '';
  const montoTexto = signo + formatearMoneda(Math.abs(d)).slice(3);
  if (caja.semaforoDescuadre === 'EXACTO') {
    return {
      tono: 'ok',
      titulo: 'Caja cuadrada',
      montoTexto: formatearMoneda(0),
      nota: 'El conteo coincide con lo esperado.',
      montoEsperado: esperado,
      montoContado: contado,
    };
  }
  if (caja.semaforoDescuadre === 'LEVE') {
    return {
      tono: 'warn',
      titulo: d < 0 ? 'Descuadre leve · faltante' : 'Descuadre leve · sobrante',
      montoTexto,
      nota: `Diferencia menor a ${formatearMoneda(umbralLeve)}.`,
      montoEsperado: esperado,
      montoContado: contado,
    };
  }
  return {
    tono: 'danger',
    titulo: d < 0 ? 'Faltante importante' : 'Sobrante importante',
    montoTexto,
    nota: 'El administrador recibirá una alerta por este descuadre.',
    montoEsperado: esperado,
    montoContado: contado,
  };
}

/** Tarjeta post-cierre (esperado/contado/semáforo). La usan el cierre del TECNICO y el del ADMINISTRADOR. */
@Component({
  selector: 'app-resultado-cierre',
  imports: [CardComponent],
  templateUrl: './resultado-cierre.html',
  styleUrl: './resultado-cierre.scss',
})
export class ResultadoCierreComponent {
  private readonly config = inject(ConfigService);

  readonly caja = input.required<CajaDiaria | null>();
  readonly formatearMoneda = formatearMoneda;

  readonly resultado = computed(() =>
    resultadoDeCierre(this.caja(), this.config.config()?.descuadreLeve ?? UMBRAL_DESCUADRE_LEVE_DEFECTO),
  );
}
