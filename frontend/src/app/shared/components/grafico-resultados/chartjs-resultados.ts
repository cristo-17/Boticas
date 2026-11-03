/**
 * Único punto del frontend que importa Chart.js. Solo se carga con
 * `import()` desde GraficoResultadosComponent, así que esbuild lo saca
 * a un chunk diferido y nunca entra al bundle inicial (Tarea 18, Bloque C).
 * Registra exclusivamente lo que usan los gráficos: barras, línea,
 * escalas de categoría/lineal, tooltip y leyenda.
 */
import {
  BarController,
  BarElement,
  CategoryScale,
  Chart,
  Legend,
  LineController,
  LineElement,
  LinearScale,
  PointElement,
  Tooltip,
} from 'chart.js';

Chart.register(
  BarController,
  BarElement,
  LineController,
  LineElement,
  PointElement,
  CategoryScale,
  LinearScale,
  Tooltip,
  Legend,
);

export interface SerieGrafico {
  etiquetas: string[];
  ingresos: number[];
  gastos: number[];
  resultado: number[];
}

export interface OpcionesGrafico {
  compacto: boolean;
  formatearMoneda: (valor: number) => string;
  colores: { ingresos: string; gastos: string; resultado: string; texto: string; rejilla: string };
}

export function crearGraficoResultados(
  canvas: HTMLCanvasElement,
  serie: SerieGrafico,
  opciones: OpcionesGrafico,
): Chart {
  const { colores, compacto, formatearMoneda } = opciones;
  return new Chart(canvas, {
    data: {
      labels: serie.etiquetas,
      datasets: [
        {
          type: 'bar',
          label: 'Ingresos (ventas)',
          data: serie.ingresos,
          backgroundColor: colores.ingresos,
          borderRadius: 3,
          order: 2,
        },
        {
          type: 'bar',
          label: 'Gastos',
          data: serie.gastos,
          backgroundColor: colores.gastos,
          borderRadius: 3,
          order: 2,
        },
        {
          type: 'line',
          label: 'Resultado neto',
          data: serie.resultado,
          borderColor: colores.resultado,
          backgroundColor: colores.resultado,
          borderWidth: 2,
          pointRadius: compacto ? 2 : 3,
          tension: 0,
          order: 1,
        },
      ],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      animation: false,
      interaction: { mode: 'index', intersect: false },
      plugins: {
        legend: {
          display: true,
          position: 'bottom',
          // `order` dibuja la línea encima de las barras; la leyenda sigue el orden de los datasets.
          labels: {
            color: colores.texto,
            boxWidth: 12,
            font: { size: compacto ? 11 : 12 },
            sort: (a, b) => (a.datasetIndex ?? 0) - (b.datasetIndex ?? 0),
          },
        },
        tooltip: {
          callbacks: {
            label: (ctx) => `${ctx.dataset.label}: ${formatearMoneda(Number(ctx.parsed.y))}`,
          },
        },
      },
      scales: {
        x: {
          ticks: { color: colores.texto, maxRotation: 0, autoSkip: true, font: { size: compacto ? 10 : 12 } },
          grid: { display: false },
        },
        y: {
          ticks: {
            color: colores.texto,
            maxTicksLimit: compacto ? 4 : 6,
            callback: (v) => formatearMoneda(Number(v)),
            font: { size: compacto ? 10 : 12 },
          },
          grid: { color: colores.rejilla },
        },
      },
    },
  });
}
