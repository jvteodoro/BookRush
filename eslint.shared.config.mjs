import { createRequire } from 'node:module';

// Use the consuming application's toolchain; the UI package is source-only.
const require = createRequire(process.argv[1]);
const js = require('@eslint/js');
const tseslint = require('typescript-eslint');
export default tseslint.config(
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    files: ['packages/ui/src/**/*.{ts,tsx}'],
    languageOptions: { globals: { window: 'readonly', document: 'readonly', localStorage: 'readonly', ResizeObserver: 'readonly' } },
  },
);
