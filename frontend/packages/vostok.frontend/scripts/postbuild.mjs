import { cpSync, existsSync, mkdirSync } from 'node:fs';
import { resolve } from 'node:path';

const distDir = resolve(process.cwd(), 'dist');
const styleInDist = resolve(distDir, 'style.css');
const styleFallback = resolve(process.cwd(), 'src/styles/index.css');
const styleOut = resolve(distDir, 'styles.css');

// 统一产出 exports 约定的 styles.css，兼容 Vite 默认 style.css 文件名。
mkdirSync(distDir, { recursive: true });
if (existsSync(styleInDist)) {
  cpSync(styleInDist, styleOut);
} else {
  cpSync(styleFallback, styleOut);
}
