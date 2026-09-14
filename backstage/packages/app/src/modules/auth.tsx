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
import {
  OpenApiDefinitionWidget,
  defaultDefinitionWidgets,
} from '@backstage/plugin-api-docs';
import apiDocsPlugin from '@backstage/plugin-api-docs/alpha';
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
    apiDocsPlugin.getExtension('api:api-docs/config').override({
      factory: (_originalFactory, { apis }) => {
        const authApi = apis.get(oidcApiRef);
        const widgets = defaultDefinitionWidgets().map(widget => {
          if (widget.type !== 'openapi') return widget;
          return {
            ...widget,
            component: (definition: string) => (
              <OpenApiDefinitionWidget
                definition={definition}
                requestInterceptor={async request => {
                  const target = new URL(request.url, window.location.origin);
                  const allowedHosts = new Set([
                    window.location.hostname,
                    'bookrush.jteodoro.tec.br',
                    'localhost',
                    '127.0.0.1',
                  ]);
                  if (!allowedHosts.has(target.hostname)) return request;
                  const token = await authApi?.getAccessToken();
                  if (!token) return request;
                  if (request.headers?.set) {
                    request.headers.set('Authorization', `Bearer ${token}`);
                  } else {
                    request.headers = {
                      ...(request.headers ?? {}),
                      Authorization: `Bearer ${token}`,
                    };
                  }
                  return request;
                }}
              />
            ),
          };
        });
        return [
          ApiBlueprint.dataRefs.factory(
            ({
              getApiDefinitionWidget: (apiEntity: any) =>
                widgets.find(widget => widget.type === apiEntity.spec.type),
            } as unknown) as never,
          ),
        ];
      },
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
