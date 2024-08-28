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

# Use username as namespace. If not found, use namespace from config
function set_namespace() {
  export NAMESPACE=$USER
  if [ -z "$NAMESPACE" ]
  then
    NAMESPACE=${K8S_NAMESPACE}
  fi
}

function print_separator() {
  printf "=====================\n"
}

function cleanup_namespace() {
  print_separator
  echo "Deleting namespace ${NAMESPACE} if exists"
  kubectl delete namespace $NAMESPACE
}

function create_namespace() {
  print_separator
  echo "Creating namespace ${NAMESPACE}"
  kubectl create namespace $NAMESPACE
}

function create_secrets() {
  print_separator
  # Create docker pull secret. Needed for k8s to be able to pull the image from arm registry
  echo 'Creating secret for docker pull'
  kubectl create secret docker-registry pullsecret \
      --docker-server=armdocker.rnd.ericsson.se    \
      --docker-username=$DOCKER_USERNAME      \
      --docker-password=$DOCKER_PASSWORD      \
      --docker-email=example@ericsson.com     \
      --namespace=$NAMESPACE

  # Create database secret.
  # This contains user and password for normal user and super user
  echo 'Creating secret for database access'
  kubectl create secret generic eric-eo-database-pg-secret \
      --from-literal=custom-user=customname   \
      --from-literal=custom-pwd=custompwd     \
      --from-literal=super-pwd=superpwd       \
      --from-literal=super-user=postgres      \
      --from-literal=metrics-pwd=metricspwd   \
      --from-literal=replica-user=replicauser \
      --from-literal=replica-pwd=replicapwd   \
      --namespace=$NAMESPACE
}

# MAIN
set_namespace
printf "\n\n!!! Warning !!! This will delete the existing namespace (${NAMESPACE}) for clean install. \n\n"
read -p "Continue (Y/N)? " choice
case "$choice" in
  y|Y ) echo '';;
  n|N ) exit;;
  * ) exit;;
esac

cleanup_namespace
create_namespace
create_secrets
