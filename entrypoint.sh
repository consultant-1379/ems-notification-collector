#!/bin/bash
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


HOSTNAME_REGEX="^[a-zA-Z0-9\-]+$"
POSITIVE_NUMBER_REGEX="^[1-9]{1}[0-9]*$"
POLLING_FREQUENCY_REGEX="^10_[s|S][e|E][c|C]|20_[s|S][e|E][c|C]|30_[s|S][e|E][c|C]|1_[m|M][i|I][n|N]|2_[m|M][i|I][n|N]$"
LOG_LEVEL_REGEX="^ERROR|WARN|INFO|DEBUG|TRACE$"
ISOLATION_REGEX="^NONE|TRANSACTIONAL|IDEMPOTENT$"

echo -e "
#============================================================================
Kafka clusters checks
"

[[ ! "$EMSNC_KAFKA_HOST" =~ $HOSTNAME_REGEX ]] && echo "Invalid EMSNC_KAFKA_HOST!" && exit 1
[[ ! "$EMSNC_KAFKA_PORT" =~ $POSITIVE_NUMBER_REGEX ]] && echo "Invalid EMSNC_KAFKA_PORT!" && exit 1
[[ ! "$EMSNC_KAFKA_TOPIC_COUNT" =~ $POSITIVE_NUMBER_REGEX ]] && echo "Invalid EMSNC_KAFKA_TOPIC_COUNT!" && exit 1
[[ ! "$EMSNC_KAFKA_PRODUCER_ISOLATION" =~ $ISOLATION_REGEX ]] && echo "Invalid EMSNC_KAFKA_PRODUCER_ISOLATION!" && exit 1
[[ ! "$EMSNC_KAFKA_SEND_TIMEOUT" =~ $POSITIVE_NUMBER_REGEX ]] && echo "Invalid EMSNC_KAFKA_SEND_TIMEOUT!" && exit 1
[[ ! "$EMSNC_KAFKA_PARTITIONS" =~ $POSITIVE_NUMBER_REGEX ]] && echo "Invalid EMSNC_KAFKA_PARTITIONS!" && exit 1
[[ ! "$EMSNC_KAFKA_REPLICATION_FACTOR" =~ $POSITIVE_NUMBER_REGEX ]] && echo "Invalid EMSNC_KAFKA_REPLICATION_FACTOR!" && exit 1
[[ ! "$EMSNC_KAFKA_CONCURRENCY_PER_TOPIC" =~ $POSITIVE_NUMBER_REGEX ]] && echo "Invalid EMSNC_KAFKA_CONCURRENCY_PER_TOPIC!" && exit 1
[[ ! "$EMSNC_KAFKA_CONSUMER_THREAD_PRIORITY" =~ $POSITIVE_NUMBER_REGEX ]] && echo "Invalid EMSNC_KAFKA_CONSUMER_THREAD_PRIORITY!" && exit 1
if [[ "$EMSNC_KAFKA_CONSUMER_THREAD_PRIORITY" -lt 1 ]] || [[ "$EMSNC_KAFKA_CONSUMER_THREAD_PRIORITY" -gt 10 ]]
then
  echo "Invalid EMSNC_KAFKA_CONSUMER_THREAD_PRIORITY! It must be a number between 1 and 10."
  exit 1
fi

[[ ! "$DMAAP_KAFKA_HOST" =~ $HOSTNAME_REGEX ]] && echo "Invalid DMAAP_KAFKA_HOST!" && exit 1
[[ ! "$DMAAP_KAFKA_PORT" =~ $POSITIVE_NUMBER_REGEX ]] && echo "Invalid DMAAP_KAFKA_PORT!" && exit 1
[[ -z "$DMAAP_KAFKA_TOPIC" ]] && echo "DMAAP_KAFKA_TOPIC is not set or empty!" && exit 1
[[ ! "$DMAAP_KAFKA_PARTITION_COUNT" =~ $POSITIVE_NUMBER_REGEX ]] && echo "Invalid DMAAP_KAFKA_PARTITION_COUNT!" && exit 1
[[ ! "$DMAAP_KAFKA_PRODUCER_ISOLATION" =~ $ISOLATION_REGEX ]] && echo "Invalid DMAAP_KAFKA_PRODUCER_ISOLATION!" && exit 1
[[ ! "$DMAAP_KAFKA_SEND_TIMEOUT" =~ $POSITIVE_NUMBER_REGEX ]] && echo "Invalid DMAAP_KAFKA_SEND_TIMEOUT!" && exit 1
[[ ! "$DMAAP_KAFKA_REPLICATION_FACTOR" =~ $POSITIVE_NUMBER_REGEX ]] && echo "Invalid DMAAP_KAFKA_REPLICATION_FACTOR!" && exit 1


echo -e "
#============================================================================
Log level checks
"
[[ ! "$ROOT_LOG_LEVEL" =~ $LOG_LEVEL_REGEX ]] && echo "Invalid ROOT_LOG_LEVEL!" && exit 1
[[ ! "$EMSNC_LOG_LEVEL" =~ $LOG_LEVEL_REGEX ]] && echo "Invalid EMSNC_LOG_LEVEL!" && exit 1
[[ ! "$KAFKA_LOG_LEVEL" =~ $LOG_LEVEL_REGEX ]] && echo "Invalid KAFKA_LOG_LEVEL!" && exit 1


echo -e "DONE
#============================================================================"

echo -e "
#============================================================================
Subscriptions check
"

[[ -z "$SUBSCRIPTION_NE_TYPES" ]] && echo "SUBSCRIPTION_NE_TYPES is not set or empty!" && exit 1

echo -e "DONE
#============================================================================"

echo -e "
#============================================================================
Timings checks
"

if [[ "$CONNECTED_SYSTEMS_POLLING_FREQUENCY" =~ $POLLING_FREQUENCY_REGEX ]]
then
  interval=$(echo "$CONNECTED_SYSTEMS_POLLING_FREQUENCY" | grep -o  '[0-9]\+')
  if [[ "$interval" -gt 2 ]]  # seconds
  then
    export INTERNAL_CONNECTED_SYSTEMS_POLLING_CRON_SCHEDULE="0/$interval * * * * ?"
  else  # minutes
    export INTERNAL_CONNECTED_SYSTEMS_POLLING_CRON_SCHEDULE="0 0/$interval * * * ?"
  fi
  echo "Connected Systems polling cron will be $INTERNAL_CONNECTED_SYSTEMS_POLLING_CRON_SCHEDULE"
else
  echo "Invalid CONNECTED_SYSTEMS_POLLING_FREQUENCY! It must be one of the following values: 10_SEC, 20_SEC, 30_SEC, 1_MIN, 2_MIN."
  exit 1
fi

if [[ "$ENM_POLLING_FREQUENCY" =~ $POLLING_FREQUENCY_REGEX ]]
then
  interval=$(echo "$ENM_POLLING_FREQUENCY" | grep -o  '[0-9]\+')
  if [[ "$interval" -gt 2 ]]  # seconds
  then
    export INTERNAL_ENM_POLLING_CRON_SCHEDULE="0/$interval * * * * ?"
    export INTERNAL_ENM_POLLING_PERIOD="$interval"
  else  # minutes
    export INTERNAL_ENM_POLLING_CRON_SCHEDULE="0 0/$interval * * * ?"
    export INTERNAL_ENM_POLLING_PERIOD="$((interval*60))"
  fi
  echo "ENM polling cron will be $INTERNAL_ENM_POLLING_CRON_SCHEDULE"
  echo "ENM polling period will be $INTERNAL_ENM_POLLING_PERIOD seconds."
else
  echo "Invalid ENM_POLLING_FREQUENCY! It must be one of the following values: 10_SEC, 20_SEC, 30_SEC, 1_MIN, 2_MIN."
  exit 1
fi

if [[ "$ENM_POLLING_OFFSET" -lt 5 ]] || [[ "$ENM_POLLING_OFFSET" -gt 60 ]]
then
  echo "Invalid ENM_POLLING_OFFSET! It must be a number between 5 and 60."
  exit 1
fi

[[ ! "$SCHEDULING_THREAD_COUNT" =~ $POSITIVE_NUMBER_REGEX ]] && echo "Invalid SCHEDULING_THREAD_COUNT!" && exit 1

[[ ! "$SCHEDULING_THREAD_PRIORITY" =~ $POSITIVE_NUMBER_REGEX ]] && echo "Invalid SCHEDULING_THREAD_PRIORITY!" && exit 1
if [[ "$SCHEDULING_THREAD_PRIORITY" -lt 1 ]] || [[ "$SCHEDULING_THREAD_PRIORITY" -gt 10 ]]
then
  echo "Invalid SCHEDULING_THREAD_PRIORITY! It must be a number between 1 and 10."
  exit 1
fi

echo -e "
DONE
#============================================================================"

echo -e "
#============================================================================
REST clients checks
"

[[ ! "$CONNECTED_SYSTEMS_HOST" =~ $HOSTNAME_REGEX ]] && echo "Invalid CONNECTED_SYSTEMS_HOST!" && exit 1
[[ ! "$CONNECTED_SYSTEMS_PORT" =~ $POSITIVE_NUMBER_REGEX ]] && echo "Invalid CONNECTED_SYSTEMS_PORT!" && exit 1

echo -e "DONE
#============================================================================"

echo -e "
#============================================================================
Database checks
"

[[ ! "$DB_HOST" =~ $HOSTNAME_REGEX ]] && echo "Invalid DB_HOST!" && exit 1
[[ ! "$DB_PORT" =~ $POSITIVE_NUMBER_REGEX ]] && echo "Invalid DB_PORT!" && exit 1
[[ -z "$DB_NAME" ]] && echo "DB_NAME is not set or empty!" && exit 1
[[ -z "$DB_USERNAME" ]] && echo "DB_USERNAME is not set or empty!" && exit 1
[[ -z "$DB_PASSWORD" ]] && echo "DB_PASSWORD is not set or empty!" && exit 1

echo -e "DONE
#============================================================================"

echo -e "
███████╗███╗░░░███╗░██████╗░░░░░░███╗░░██╗░░░░░░░█████╗░
██╔════╝████╗░████║██╔════╝░░░░░░████╗░██║░░░░░░██╔══██╗
█████╗░░██╔████╔██║╚█████╗░█████╗██╔██╗██║█████╗██║░░╚═╝
██╔══╝░░██║╚██╔╝██║░╚═══██╗╚════╝██║╚████║╚════╝██║░░██╗
███████╗██║░╚═╝░██║██████╔╝░░░░░░██║░╚███║░░░░░░╚█████╔╝
╚══════╝╚═╝░░░░░╚═╝╚═════╝░░░░░░░╚═╝░░╚══╝░░░░░░░╚════╝░

Starting EMS Notification Collector...
"

java ${JAVA_OPTS} -Dcom.sun.management.jmxremote=true -Dcom.sun.management.jmxremote.port=1099 \
-Dcom.sun.management.jmxremote.authenticate=true -Dcom.sun.management.jmxremote.ssl=false \
-Dcom.sun.management.jmxremote.rmi.port=1099 -Dcom.sun.management.jmxremote.password.file=/jmx/jmxremote.password \
-Dcom.sun.management.jmxremote.access.file=/jmx/jmxremote.access -jar eric-oss-adc-ems-notification-collector-app.jar