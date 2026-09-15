import { createApp } from '@backstage/frontend-defaults';
import catalogPlugin from '@backstage/plugin-catalog/alpha';
import catalogGraphPlugin from '@backstage/plugin-catalog-graph/alpha';
import techdocsPlugin from '@backstage/plugin-techdocs/alpha';
import { apiDocsWithOidcPlugin, authModule } from './modules/auth';
import { navModule } from './modules/nav';
import { signalsModule } from './modules/signals';

export default createApp({
  features: [
    catalogPlugin,
    catalogGraphPlugin,
    apiDocsWithOidcPlugin,
    techdocsPlugin,
    signalsModule,
    navModule,
    authModule,
  ],
});
