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

# Check if so-chart-repo is added already
function check_so_repo() {
  if [ -z $(helm repo list | awk '{print $1}' | grep 'so-chart-repo') ]
    then
      echo 'Adding SO chart repo'
      helm repo add so-chart-repo https://arm.sero.gic.ericsson.se/artifactory/proj-so-gs-all-helm --username ${HELM_REPO_USER} --password ${HELM_REPO_PASS} &> /dev/null
  else
    echo 'SO chart repo added already'
  fi
}

# Check if adp-chart-repo is added already
function check_adp_repo() {
  if [ -z $(helm repo list | awk '{print $1}' | grep 'adp-chart-repo') ]
    then
      echo 'Adding ADP chart repo'
      helm repo add adp-chart-repo https://arm.sero.gic.ericsson.se/artifactory/proj-adp-gs-all-helm --username ${HELM_REPO_USER} --password ${HELM_REPO_PASS} &> /dev/null
  else
    echo 'ADP chart repo added already'
  fi
}

function check_oss_repo() {
  if [ -z $(helm repo list | awk '{print $1}' | grep 'oss-chart-repo') ]
    then
      echo 'Adding OSS chart repo'
      helm repo add oss-chart-repo https://arm.seli.gic.ericsson.se/artifactory/proj-eric-oss-ci-internal-helm-local --username ${HELM_REPO_USER} --password ${HELM_REPO_PASS} &> /dev/null
  else
    echo 'OSS chart repo added already'
  fi
}

function check_oss_dev_helm_repo() {
  if [ -z $(helm repo list | awk '{print $1}' | grep 'oss-dev-helm-chart-repo') ]
    then
      echo 'Adding OSS dev helm chart repo'
      helm repo add oss-dev-helm-chart-repo https://arm.seli.gic.ericsson.se/artifactory/proj-eric-oss-dev-helm --username ${HELM_REPO_USER} --password ${HELM_REPO_PASS} &> /dev/null
  else
    echo 'OSS dev helm chart repo added already'
  fi
}

function pull_charts() {
  helm pull adp-chart-repo/eric-data-coordinator-zk --version ${DATA_COORDINATOR_VERSION} --destination ./charts &> /dev/null
  helm pull adp-chart-repo/eric-data-document-database-pg --version ${DOCUMENT_DB_VERSION} --destination ./charts &> /dev/null
  helm pull adp-chart-repo/eric-data-message-bus-kf --version ${MESSAGE_BUS_KF_VERSION} --destination ./charts &> /dev/null
  helm pull so-chart-repo/eric-eo-subsystem-management --version ${CONNECTED_SYSTEMS_VERSION} --destination ./charts &> /dev/null
  helm pull oss-chart-repo/eric-oss-adc-ems-notification-collector --version ${EMSNC_VERSION} --destination ./charts
}

function verify_chart_download() {
  FILE=./charts/eric-eo-subsystem-management-${CONNECTED_SYSTEMS_VERSION}.tgz
  if [ ! -f "$FILE" ]; then
      echo "$FILE pull unsuccessful."
    else echo "Connected Systems ${CONNECTED_SYSTEMS_VERSION} chart pull successful."
  fi

  FILE=./charts/eric-data-document-database-pg-${DOCUMENT_DB_VERSION}.tgz
  if [ ! -f "$FILE" ]; then
      echo "$FILE pull unsuccessful."
    else echo "ADP Document Database PG ${DOCUMENT_DB_VERSION} chart pull successful."
  fi

  FILE=./charts/eric-data-message-bus-kf-${MESSAGE_BUS_KF_VERSION}.tgz
  if [ ! -f "$FILE" ]; then
      echo "$FILE pull unsuccessful."
    else echo "ADP Messagebus KF ${MESSAGE_BUS_KF_VERSION} chart pull successful."
  fi

  FILE=./charts/eric-data-coordinator-zk-${DATA_COORDINATOR_VERSION}.tgz
  if [ ! -f "$FILE" ]; then
      echo "$FILE pull unsuccessful."
    else echo "ADP Data Coordinator ZK ${DATA_COORDINATOR_VERSION} chart pull successful."
  fi

  FILE=./charts/eric-oss-adc-ems-notification-collector-${EMSNC_VERSION}.tgz
  if [ ! -f "$FILE" ]; then
      echo "$FILE pull unsuccessful."
    else
      echo "EMSNC ${EMSNC_VERSION} chart pull successful."
      echo "Creating a copy of the chart."
      cp ./charts/eric-oss-adc-ems-notification-collector-${EMSNC_VERSION}.tgz ./charts/eric-oss-adc-ems-notification-collector.tgz
  fi
}

# Main
check_so_repo
check_adp_repo
check_oss_repo
check_oss_dev_helm_repo
pull_charts
verify_chart_download
