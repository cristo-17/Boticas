/** Los 4 estados de vencimiento y los 3 de stock (nota 4, docs/DECISIONES.md) — calculados por el servidor, nunca recalculados en el cliente. Compartido por Producto y Lote (y Alertas, Tarea 11 Bloque C). */
export type EstadoVencimiento = 'VENCIDO' | 'CRITICO' | 'ADVERTENCIA' | 'OK';
export type StockEstado = 'AGOTADO' | 'CRITICO' | 'OK';
