FROM mysql:8.0.11

MAINTAINER matt@kitserve.org.uk

ENV MYSQL_DATABASE=event_store \
    MYSQL_ROOT_PASSWORD=root

ADD schema.sql /docker-entrypoint-initdb.d

ENTRYPOINT ["docker-entrypoint.sh", "--default-authentication-plugin=mysql_native_password", "--binlog-do-db=${MYSQL_DATABASE}"]

HEALTHCHECK --interval=5s --timeout=1s --retries=5 \
    CMD mysql -uroot -p${MYSQL_ROOT_PASSWORD} ${MYSQL_DATABASE} -e 'SELECT 1'

EXPOSE 3306