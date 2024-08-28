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

function set_namespace() {
  export NAMESPACE=$USER
  if [ -z "$NAMESPACE" ]
  then
    NAMESPACE=${K8S_NAMESPACE}
  fi
}

set_namespace

printf "\n\n!!! Warning !!! This will uninstall EMSNC and delete this namespace: ${NAMESPACE}. \n\n"
read -p "Continue (Y/N)? " choice

case "$choice" in
  y|Y ) echo '';;
  n|N ) exit;;
  * ) exit;;
esac

# helm deployment delete
helm delete $(helm ls --namespace=${NAMESPACE} --short) --namespace=${NAMESPACE}

# namespace delete
kubectl delete namespace ${NAMESPACE}
