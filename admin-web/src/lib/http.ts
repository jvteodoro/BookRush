import axios from 'axios';

let accessToken: string | undefined;
export function setAccessToken(token?: string) { accessToken = token; }

export function createHttp(baseURL: string, getToken: () => string | undefined) {
  const client = axios.create({ baseURL, timeout: 15000 });
  client.interceptors.request.use(cfg => {
    const token = getToken() || accessToken;
    if (token) cfg.headers.Authorization = `Bearer ${token}`;
    cfg.headers['X-Admin-Client'] = 'labsoft-admin-web';
    return cfg;
  });
  return client;
}
