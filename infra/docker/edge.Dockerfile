FROM caddy:2-alpine
RUN addgroup -S -g 10001 walletpay \
    && adduser -S -u 10001 -G walletpay walletpay \
    && mkdir -p /data/caddy /config/caddy \
    && chown -R 10001:10001 /data /config
USER 10001:10001
EXPOSE 8080 8443
