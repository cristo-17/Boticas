export interface VencimientoConfig {
  criticoDias: number;
  advertenciaDias: number;
}

/** Forma exacta de GET /api/config — docs/API-CONTRATO.md, "Configuración de negocio". */
export interface ConfigNegocio {
  igv: number;
  umbralStockBajo: number;
  descuadreLeve: number;
  motivosMerma: string[];
  motivosQueRequierenObservacion: string[];
  vencimiento: VencimientoConfig;
  /** R25: commit que sirve el backend ("local" fuera de Render). Solo para verificar el deploy; la UI no lo usa. */
  version: string;
}
