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

function install_adp_zookeeper() {
  print_separator
  # Helm install ADP Data Coordinator-ZK
  echo 'Installing ADP Data Coordinator ZK' $DATA_COORDINATOR_VERSION
  helm install eric-data-coordinator-zk         \
      ./charts/eric-data-coordinator-zk-${DATA_COORDINATOR_VERSION}.tgz  \
      --namespace=$K8S_NAMESPACE                \
      --set global.pullSecret=pullsecret        \
      --set brAgent.enabled=false               \
      --set persistence.persistentVolumeClaim.enabled=false \
      --set global.security.tls.enabled=false   \
      --set replicas=1                          \
      --wait                                    \
      --timeout 200s
}

function install_kafka() {
  print_separator
  # Helm install ADP Messagebus-KF
  echo 'Installing ADP Message Bus KF' $MESSAGE_BUS_KF_VERSION
  helm install eric-data-message-bus-kf \
      ./charts/eric-data-message-bus-kf-${MESSAGE_BUS_KF_VERSION}.tgz \
      --namespace=$K8S_NAMESPACE                \
      --set global.pullSecret=pullsecret        \
      --set replicaCount=3                      \
      --set persistence.persistentVolumeClaim.enabled=false \
      --set global.security.tls.enabled=false   \
      --wait                                    \
      --timeout 200s
}

function install_adp_database() {
  print_separator
  # Helm install ADP Document Database PG
  echo 'Installing ADP Document Database PG' $DOCUMENT_DB_VERSION
  helm install eric-oss-common-postgres \
      ./charts/eric-data-document-database-pg-${DOCUMENT_DB_VERSION}.tgz \
      --namespace=$K8S_NAMESPACE                      \
      --set nameOverride=eric-oss-common-postgres     \
      --set global.pullSecret=pullsecret              \
      --set brAgent.enabled=false                     \
      --set credentials.kubernetesSecretName=eric-eo-database-pg-secret \
      --set global.security.tls.enabled=false         \
      --set highAvailability.replicaCount=1           \
      --wait                                          \
      --timeout 600s
}

function install_connected_systems() {
  print_separator
  # Helm Install Connected Systems
  echo 'Installing Connected Systems' $CONNECTED_SYSTEMS_VERSION
  helm install eric-eo-subsystem-management       \
      ./charts/eric-eo-subsystem-management-${CONNECTED_SYSTEMS_VERSION}.tgz \
      --namespace=$K8S_NAMESPACE                  \
      --set db.url=eric-oss-common-postgres       \
      --set global.pullSecret=pullsecret          \
      --set replicaCount=1                        \
      --set persistence.persistentVolumeClaim.enabled=false \
      --set global.security.tls.enabled=false     \
      --no-hooks                                  \
      --wait                                      \
      --timeout 200s
}

function install_context_simulator() {
  print_separator
  # Helm Install context simulator
  echo 'Installing Context Simulator'
  helm install context-simulator                  \
      ./context-simulator/                        \
      --namespace=$K8S_NAMESPACE                  \
      --set global.pullSecret=pullsecret          \
      --set config.kafkaBootstrap=eric-data-message-bus-kf:9092 \
      --set config.enmhost=context-simulator      \
      --set config.kafkaTopic=dmaap-result-topic  \
      --set config.eventCycles=0                  \
      --wait                                      \
      --timeout 200s
}

# read version prefix from file and find the built chart
function copy_latest_build_from_bob_dir() {
  print_separator
  VERSION=$(cat ../../VERSION_PREFIX)
  echo $VERSION

  FILENAME=$(find ../../.bob/eric-oss-adc-ems-notification-collector-internal/ -name "*${VERSION}*" -printf "%T+ %p\n" | sort -r | head -n 1 | cut -d' ' -f2)

  if [ ! -f "$FILENAME" ]; then
    echo "No chart found in bob build dir. Execute bob package-local:package-helm-internal in repo root!"
    exit;
  else
    printf "\n"
    echo "Copying:" $FILENAME
    cp ${FILENAME} ./charts/eric-oss-adc-ems-notification-collector.tgz
  fi
}

function get_emsnc_chart_from_bob() {
  print_separator
  read -p "Do you want to copy EMSNC chart from bob build dir (Y/N)? " choice

  case "$choice" in
    y|Y ) copy_latest_build_from_bob_dir;;
    n|N ) echo "...will try to use existing eric-oss-adc-ems-notification-collector.tgz";;
    * ) exit;;
  esac
}

function verify_emsnc_exists() {
  FILE=./charts/eric-oss-adc-ems-notification-collector.tgz
  if [ ! -f "$FILE" ]; then
    echo "$FILE Does not exist. Execute bob package-local in repo root dir or manually put your version to charts/eric-oss-adc-ems-notification-collector.tgz"
    exit 0;
  else
    echo "Ok, $FILE found."
  fi
}

function install_emnsc() {
  print_separator
  echo 'Installing EMS-NC'
  helm install eric-oss-ems-nc \
      ./charts/eric-oss-adc-ems-notification-collector.tgz \
      --namespace=$K8S_NAMESPACE                    \
      --set global.pullSecret=pullsecret            \
      --set db.host=eric-oss-common-postgres        \
      --set connectedSystems.host=eric-eo-subsystem-management \
      --set connectedSystems.port=80                \
      --set log.logLevel=INFO                       \
      --set log.kafkaLogLevel=ERROR                 \
      --wait \
      --timeout 600s
}

# MAIN
get_emsnc_chart_from_bob
verify_emsnc_exists

install_adp_zookeeper
install_kafka
install_adp_database
install_connected_systems
install_emnsc
install_context_simulator

# Verify
helm list -n $K8S_NAMESPACE
kubectl -n $K8S_NAMESPACE get pods
