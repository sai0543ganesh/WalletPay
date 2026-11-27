import { randomBytes } from 'node:crypto';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const secretDirectory = path.join(root, '.secrets');
mkdirSync(secretDirectory, { recursive: true, mode: 0o700 });
for (const name of ['database_password', 'jwt_secret', 'webhook_secret']) {
  const target = path.join(secretDirectory, name);
  if (!existsSync(target)) {
    // Compose file-backed secrets are bind mounts: its uid/gid options cannot
    // remap ownership. The private parent directory protects the host files,
    // while read-only files can be read by non-root PostgreSQL and Java users.
    writeFileSync(target, randomBytes(48).toString('base64'), { flag: 'wx', mode: 0o444 });
  }
}
const envFile = path.join(root, '.env');
if (!existsSync(envFile)) {
  writeFileSync(envFile, readFileSync(path.join(root, '.env.example')), { flag: 'wx', mode: 0o600 });
}
console.log('Local configuration is ready. Existing files were preserved. No secret values were printed.');
console.log('Keep .env and .secrets outside Git. On Windows, also restrict their NTFS permissions to your account.');
