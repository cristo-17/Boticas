import { writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

/**
 * Tarea 19: Chromium puede usar un archivo .y4m como cámara
 * (--use-file-for-fake-video-capture). Este módulo dibuja un EAN-13 en un
 * cuadro de 640x480 sin dependencias: barras negras sobre blanco en el
 * plano de luminancia. Lo genera global-setup.ts antes de lanzar el
 * navegador del proyecto "camara" (playwright.config.ts).
 */
export const RUTA_VIDEO_CAMARA = join(tmpdir(), 'boticasys-e2e-camara-ean13.y4m');

/**
 * EAN-13 de "Cetirizina 10 mg" de Botica La Esperanza (V12: semilla fija,
 * el mismo en toda base dev o prod con los datos demo). Los specs
 * comprueban el nombre del producto que abre.
 */
export const CODIGO_CAMARA = '7751183253402';

const L = [
  '0001101',
  '0011001',
  '0010011',
  '0111101',
  '0100011',
  '0110001',
  '0101111',
  '0111011',
  '0110111',
  '0001011',
];
const R = L.map((p) => [...p].map((b) => (b === '0' ? '1' : '0')).join(''));
const G = R.map((p) => [...p].reverse().join(''));
const PARIDAD = [
  'LLLLLL',
  'LLGLGG',
  'LLGGLG',
  'LLGGGL',
  'LGLLGG',
  'LGGLLG',
  'LGGGLL',
  'LGLGLG',
  'LGLGGL',
  'LGGLGL',
];

function modulos(codigo: string): string {
  const d = [...codigo].map(Number);
  let m = '101';
  for (let i = 0; i < 6; i++) m += (PARIDAD[d[0]][i] === 'L' ? L : G)[d[i + 1]];
  m += '01010';
  for (let i = 7; i < 13; i++) m += R[d[i]];
  return m + '101';
}

export function generarVideoCamara(codigo = CODIGO_CAMARA, ruta = RUTA_VIDEO_CAMARA): void {
  const ancho = 640;
  const alto = 480;
  const px = 4; // ancho de un módulo
  const m = modulos(codigo);
  const x0 = Math.floor((ancho - m.length * px) / 2);
  const y0 = 140;
  const y1 = 340;

  const y = Buffer.alloc(ancho * alto, 235); // blanco
  for (let fila = y0; fila < y1; fila++) {
    for (let i = 0; i < m.length; i++) {
      if (m[i] === '1') y.fill(16, fila * ancho + x0 + i * px, fila * ancho + x0 + (i + 1) * px);
    }
  }
  const croma = Buffer.alloc((ancho / 2) * (alto / 2), 128);
  const cuadro = Buffer.concat([Buffer.from('FRAME\n'), y, croma, croma]);
  const cabecera = Buffer.from(`YUV4MPEG2 W${ancho} H${alto} F15:1 Ip A1:1 C420jpeg\n`);
  writeFileSync(ruta, Buffer.concat([cabecera, ...Array(15).fill(cuadro)]));
}
