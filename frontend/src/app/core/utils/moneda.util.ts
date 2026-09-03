/** Formato de moneda consistente en toda la app: 'S/ 1234.50'. */
export function formatearMoneda(valor: number): string {
  return 'S/ ' + valor.toFixed(2);
}
