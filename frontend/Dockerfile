FROM nginx:alpine

RUN rm -rf /usr/share/nginx/html/*

COPY ./frontend /usr/share/nginx/html

COPY ./frontend/nginx.conf /etc/nginx/templates/default.conf.template

EXPOSE 80

CMD ["/bin/sh", "-c", \
  "envsubst < /etc/nginx/templates/default.conf.template \
  > /etc/nginx/conf.d/default.conf && nginx -g 'daemon off;'"]