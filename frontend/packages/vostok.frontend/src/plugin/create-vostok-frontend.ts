import type { Plugin } from 'vue';
import type { VostokFrontendOptions } from '../types/public';
import { installVostokFrontend } from './install';

export function createVostokFrontend(options: VostokFrontendOptions): Plugin {
  return {
    install(app) {
      installVostokFrontend(app, options);
    }
  };
}
