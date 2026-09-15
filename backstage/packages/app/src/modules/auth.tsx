import { SignInPage } from '@backstage/core-components';
import { OAuth2 } from '@backstage/core-app-api';
import {
  BackstageIdentityApi,
  OAuthApi,
  ProfileInfoApi,
  SessionApi,
  createApiRef,
  configApiRef,
  discoveryApiRef,
  oauthRequestApiRef,
  useApi,
} from '@backstage/core-plugin-api';
import {
  ApiBlueprint,
  createFrontendModule,
} from '@backstage/frontend-plugin-api';
import { SignInPageBlueprint } from '@backstage/plugin-app-react';

export const oidcApiRef = createApiRef<
  BackstageIdentityApi & ProfileInfoApi & SessionApi & OAuthApi
>({ id: 'auth.bookrush-oidc' });
export const authModule = createFrontendModule({
  pluginId: 'app',
  extensions: [
    ApiBlueprint.make({
      name: 'bookrush-oidc',
      params: define =>
        define({
          api: oidcApiRef,
          deps: {
            discoveryApi: discoveryApiRef,
            oauthRequestApi: oauthRequestApiRef,
            configApi: configApiRef,
          },
          factory: ({ discoveryApi, oauthRequestApi, configApi }) =>
            OAuth2.create({
              discoveryApi,
              oauthRequestApi,
              configApi,
              provider: { id: 'oidc', title: 'Keycloak', icon: () => null },
              // The backend requests profile/email as additional OIDC scopes;
              // keep the browser request minimal and rely on the realm mappers.
              defaultScopes: ['openid'],
              environment: configApi.getOptionalString('auth.environment'),
            }),
        }),
    }),
    SignInPageBlueprint.make({
      params: {
        loader: async () => props => {
          const config = useApi(configApiRef);
          return (
            <SignInPage
              {...props}
              providers={
                // The static frontend is built from the base config, while
                // production OIDC is supplied by the runtime overlay. Public
                // deployments therefore also use the non-local hostname as
                // an explicit production signal; this prevents a stale
                // guest provider from looping against /api/auth/guest.
                (config.getOptionalString('auth.environment') === 'production'
                  || !['localhost', '127.0.0.1'].includes(window.location.hostname))
                  ? [
                      {
                        id: 'oidc',
                        title: 'Keycloak',
                        message: 'Entre com sua identidade de engenharia',
                        apiRef: oidcApiRef,
                      },
                    ]
                  : ['guest']
              }
            />
          );
        },
      },
    }),
  ],
});
