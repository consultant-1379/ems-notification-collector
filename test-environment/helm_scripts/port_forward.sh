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

# This script exposes Connected System's port to localhost so that ENM can be registered from postman/curl
source ./install_config
export LOCAL_PORT=4014

POD_NAME=$(kubectl -n "${K8S_NAMESPACE}" get pods | grep subsystem | awk '{print $1}')
echo "Connected Systems pod name:" $POD_NAME

POD_PORT=$(kubectl get pod $POD_NAME --template='{{(index (index .spec.containers 0).ports 0).containerPort}}{{"\n"}}')
echo "Connected Systems service port:" $POD_PORT

kubectl -n ${K8S_NAMESPACE} port-forward $POD_NAME $LOCAL_PORT:$POD_PORT
