import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', '');
  if (mode === 'production' && env.VITE_USE_MOCKS === 'true') {
    throw new Error('VITE_USE_MOCKS=true is forbidden in production builds');
  }
  return {
    plugins: [react()],
    base: env.VITE_BASE_PATH || '/',
    server: { port: 5173 },
  };
});
