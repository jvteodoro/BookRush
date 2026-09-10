import { SignInPage } from '@backstage/core-components';
import { OAuth2 } from '@backstage/core-app-api';
import {
  BackstageIdentityApi,
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

const oidcApiRef = createApiRef<
  BackstageIdentityApi & ProfileInfoApi & SessionApi
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
              defaultScopes: ['openid', 'profile', 'email'],
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
                config.getOptionalString('auth.environment') === 'production'
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
