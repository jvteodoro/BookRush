import { createApp } from '@backstage/frontend-defaults';
import catalogPlugin from '@backstage/plugin-catalog/alpha';
import catalogGraphPlugin from '@backstage/plugin-catalog-graph/alpha';
import apiDocsPlugin from '@backstage/plugin-api-docs/alpha';
import techdocsPlugin from '@backstage/plugin-techdocs/alpha';
import signalsPlugin from '@backstage/plugin-signals/alpha';
import { authModule } from './modules/auth';
import { navModule } from './modules/nav';

export default createApp({
  features: [
    catalogPlugin,
    catalogGraphPlugin,
    apiDocsPlugin,
    techdocsPlugin,
    signalsPlugin,
    navModule,
    authModule,
  ],
});
