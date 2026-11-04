// Commit que sirve Vercel. En el repositorio queda 'local': solo el build de Vercel lo
// reescribe (scripts/escribir-version.cjs, en "prebuild"), con VERCEL_GIT_COMMIT_SHA.
// Se muestra en el panel de conexión para comprobar que el celular ya tiene la versión nueva.
export const VERSION_APP = 'local';
