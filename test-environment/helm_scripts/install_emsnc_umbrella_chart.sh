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

source ./install_config
source ./prepare_cluster.sh

print_separator
bob helm-install-umbrella-chart:update-requirements-yaml -w ../../

print_separator
helm dependency update ./emsnc_umbrella_chart/

print_separator
helm dependency build ./emsnc_umbrella_chart/

print_separator
helm install emsnc ./emsnc_umbrella_chart -n ${NAMESPACE}                                          \
      --set eric-oss-adc-ems-notification-collector.connectedSystems.host=eric-eo-subsystem-management \
      --set eric-oss-adc-ems-notification-collector.connectedSystems.port=80                           \
      --set eric-oss-adc-ems-notification-collector.log.emsncLogLevel=INFO                             \
      --no-hooks                                                                                       \
      --wait                                                                                           \
      --timeout 300s

print_separator
helm install context-simulator                    \
      ./context-simulator/                        \
      --namespace=${NAMESPACE}                    \
      --set global.pullSecret=pullsecret          \
      --set config.kafkaBootstrap=eric-data-message-bus-kf:9092 \
      --set config.enmhost=context-simulator      \
      --set config.kafkaTopic=dmaap-result-topic  \
      --set config.eventCycles=0                  \
      --wait                                      \
      --timeout 200s
