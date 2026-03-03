import { promises as fs } from 'node:fs';
import path from 'node:path';
import openapiTS from 'openapi-typescript';

const root = process.cwd();
const inputPath = path.resolve(root, 'openapi/openapi.yaml');
const outputPath = path.resolve(root, 'src/typings/api-generated.d.ts');

async function main() {
  try {
    await fs.access(inputPath);
  } catch {
    console.warn(`[openapi:gen] Spec not found: ${inputPath}`);
    console.warn('[openapi:gen] Skip generation.');
    return;
  }

  const output = await openapiTS(inputPath);
  await fs.mkdir(path.dirname(outputPath), { recursive: true });
  await fs.writeFile(outputPath, output);
  console.log(`[openapi:gen] Generated: ${outputPath}`);
}

main().catch((error) => {
  console.error('[openapi:gen] Failed', error);
  process.exit(1);
});
