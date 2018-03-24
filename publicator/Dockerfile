FROM openjdk:8-jre-alpine

RUN apk add --update --no-cache curl bash

RUN curl -O https://download.clojure.org/install/linux-install-1.9.0.358.sh \
 && sh linux-install-1.9.0.358.sh \
 && rm linux-install-1.9.0.358.sh
