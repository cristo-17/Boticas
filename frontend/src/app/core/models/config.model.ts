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
}
