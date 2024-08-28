#
# COPYRIGHT Ericsson 2021
#
#
#
# The copyright to the computer program(s) herein is the property of
#
# Ericsson Inc. The programs may be used and/or copied only with written
#
# permission from Ericsson Inc. or in accordance with the terms and
#
# conditions stipulated in the agreement/contract under which the
#
# program(s) have been supplied.
#

ARG CBOS_VERSION=3.19.0-24
FROM armdocker.rnd.ericsson.se/proj-ldc/common_base_os_release/sles:${CBOS_VERSION}

ARG CBOS_VERSION
ARG CBO_REPO_URL=https://arm.sero.gic.ericsson.se/artifactory/proj-ldc-repo-rpm-local/common_base_os/sles/${CBOS_VERSION}

RUN zypper ar -C -G -f $CBO_REPO_URL?ssl_verify=no \
    COMMON_BASE_OS_SLES_REPO \
    && zypper install -l -y java-11-openjdk-headless \
    && zypper install -l -y curl \
    && zypper clean --all \
    && zypper rr COMMON_BASE_OS_SLES_REPO

ARG USER_ID=40514
ARG USER_NAME="eric-oss-adc-ems-notification-collector"
ARG JAR_FILE

ADD ems-notification-collector-app/target/${JAR_FILE} eric-oss-adc-ems-notification-collector-app.jar
COPY ems-notification-collector-app/src/main/resources/jmx/* /jmx/

RUN chmod 600 /jmx/jmxremote.password \
    && chown $USER_ID /jmx/jmxremote.password \
    && bash -c 'echo "$USER_ID:x:$USER_ID:0:An Identity for $USER_NAME:/nonexistent:/bin/false" >>/etc/passwd' \
    && bash -c 'echo "$USER_ID:!::0:::::" >>/etc/shadow'

ENV EMSNC_KAFKA_HOST=""
ENV EMSNC_KAFKA_PORT=""
ENV EMSNC_KAFKA_TOPIC_COUNT=15
ENV EMSNC_KAFKA_PRODUCER_ISOLATION="NONE"
ENV EMSNC_KAFKA_SEND_TIMEOUT=5000
ENV EMSNC_KAFKA_PARTITIONS=4
ENV EMSNC_KAFKA_REPLICATION_FACTOR=1
ENV EMSNC_KAFKA_CONCURRENCY_PER_TOPIC=2
ENV EMSNC_KAFKA_CONSUMER_THREAD_PRIORITY=3

ENV DMAAP_KAFKA_HOST=""
ENV DMAAP_KAFKA_PORT=""
ENV DMAAP_KAFKA_TOPIC="dmaap-result-topic"
ENV DMAAP_KAFKA_PARTITION_COUNT=3
ENV DMAAP_KAFKA_PRODUCER_ISOLATION="NONE"
ENV DMAAP_KAFKA_SEND_TIMEOUT=5000
ENV DMAAP_KAFKA_REPLICATION_FACTOR=1

ENV SUBSCRIPTION_NE_TYPES=""

ENV CONNECTED_SYSTEMS_POLLING_FREQUENCY="2_MIN"
ENV ENM_POLLING_FREQUENCY="30_SEC"
ENV ENM_POLLING_OFFSET=5
ENV SCHEDULING_THREAD_COUNT=10
ENV SCHEDULING_THREAD_PRIORITY=5

ENV CONNECTED_SYSTEMS_HOST=""
ENV CONNECTED_SYSTEMS_PORT=""

ENV DB_HOST=""
ENV DB_PORT=5432
ENV DB_NAME="emsnc_db"
ENV DB_USERNAME=""
ENV DB_PASSWORD=""

ENV ROOT_LOG_LEVEL="INFO"
ENV EMSNC_LOG_LEVEL="INFO"
ENV KAFKA_LOG_LEVEL="ERROR"

COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh \
    && chown $USER_ID /entrypoint.sh
ENTRYPOINT ["/entrypoint.sh"]

USER $USER_ID

ARG COMMIT
ARG BUILD_DATE
ARG APP_VERSION
ARG RSTATE
ARG IMAGE_PRODUCT_NUMBER
LABEL \
    org.opencontainers.image.title=eric-oss-adc-ems-notification-collector-jsb \
    org.opencontainers.image.created=$BUILD_DATE \
    org.opencontainers.image.revision=$COMMIT \
    org.opencontainers.image.vendor=Ericsson \
    org.opencontainers.image.version=$APP_VERSION \
    com.ericsson.product-revision="${RSTATE}" \
    com.ericsson.product-number="$IMAGE_PRODUCT_NUMBER"

HEALTHCHECK --start-period=2m --interval=20s --timeout=10s --retries=1 CMD curl -s 127.0.0.1:8080/actuator/health|grep -q {\"status\":\"UP\"}