/**
 * "Nombre · Dirección" de la botica (encabezado y apertura de caja). La
 * dirección es opcional en la base (la botica real de producción no la
 * tiene): sin ella queda solo el nombre, nunca "Charito · null".
 */
export function textoSede(
  nombre: string | null | undefined,
  direccion: string | null | undefined,
): string {
  return [nombre, direccion]
    .map((parte) => (parte ?? '').trim())
    .filter((parte) => parte !== '')
    .join(' · ');
}
