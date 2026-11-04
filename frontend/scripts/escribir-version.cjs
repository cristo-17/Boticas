// "prebuild": en Vercel escribe el commit en src/environments/version.ts. Fuera de Vercel no toca nada.
const fs = require('fs');
const path = require('path');
const sha = process.env.VERCEL_GIT_COMMIT_SHA;
if (sha && /^[0-9a-f]{7,40}$/.test(sha)) {
  const archivo = path.join(__dirname, '..', 'src', 'environments', 'version.ts');
  const texto = fs.readFileSync(archivo, 'utf8').replace(/VERSION_APP = '[^']*'/, `VERSION_APP = '${sha}'`);
  fs.writeFileSync(archivo, texto);
  console.log(`version.ts -> ${sha}`);
}
