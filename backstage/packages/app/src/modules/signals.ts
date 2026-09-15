import {
  ApiBlueprint,
  createFrontendModule,
} from '@backstage/frontend-plugin-api';
import {
  signalApiRef,
  type SignalApi,
} from '@backstage/plugin-signals-react';

const unavailableSignals: SignalApi = {
  subscribe: () => ({ unsubscribe: () => undefined }),
};

/**
 * This portal has no Signals event transport. Core storage still requires the
 * SignalApi contract, so provide a deliberate no-op implementation rather
 * than starting a reconnecting WebSocket client against a non-existent route.
 */
export const signalsModule = createFrontendModule({
  pluginId: 'app',
  extensions: [
    ApiBlueprint.make({
      name: 'unavailable-signals',
      params: define =>
        define({
          api: signalApiRef,
          deps: {},
          factory: () => unavailableSignals,
        }),
    }),
  ],
});
