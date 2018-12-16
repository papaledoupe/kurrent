FROM openjdk:8

MAINTAINER matt@kitserve.org.uk

ADD ./target/kurrent-example-service.jar .

ENTRYPOINT java -jar kurrent-example-service.jar \
  --event-store-url=${EVENT_STORE_URL} \
  --starting-offset=${STARTING_OFFSET}

EXPOSE 3306