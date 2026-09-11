import { createApp } from '@backstage/frontend-defaults';
import catalogPlugin from '@backstage/plugin-catalog/alpha';
import signalsPlugin from '@backstage/plugin-signals/alpha';
import { authModule } from './modules/auth';
import { navModule } from './modules/nav';

export default createApp({
  features: [catalogPlugin, signalsPlugin, navModule, authModule],
});
