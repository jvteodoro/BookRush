FROM nginx:1.27-alpine
COPY admin-web/nginx.conf /etc/nginx/conf.d/default.conf
COPY admin-web/security-headers.conf /etc/nginx/bookrush-security-headers.conf
