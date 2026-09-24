// MapLibre 6 parses tiles in a module worker it loads from next to its own bundle. Next's
// build renames and moves that file, so the worker 404s and the map draws only its
// background. Serving the worker (and the shared chunk it imports) from public/ and
// pointing setWorkerUrl at it (src/components/Map.tsx) fixes that. Runs before dev and build,
// so the copy always matches the installed version.
import { copyFileSync, mkdirSync } from 'node:fs';

const from = 'node_modules/maplibre-gl/dist';
const to = 'public/maplibre';
mkdirSync(to, { recursive: true });
for (const file of ['maplibre-gl-worker.mjs', 'maplibre-gl-shared.mjs']) copyFileSync(`${from}/${file}`, `${to}/${file}`);
