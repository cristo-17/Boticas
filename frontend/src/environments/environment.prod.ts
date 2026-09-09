// Render (backend) y Vercel (frontend) son dos orígenes distintos
// (Tarea 10) -- no aplica la suposición original de "mismo origen"
// (ver docs/BITACORA.md si hace falta el porqué). apiUrl es la URL
// completa del servicio de Render, actualizar tras crear la cuenta
// (docs/DESPLIEGUE.md, guía de cuentas) y antes de conectar Vercel.
export const environment = {
  production: true,
  apiUrl: 'https://<tu-servicio>.onrender.com/api',
};
