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

tmux new-session -d -s groovy-shell 'java -DenmPorts=$ENM_PORTS -DcsHost=$CS_HOST -DcsPort=$CS_PORT -DmaxEventCycles=$EVENT_CYCLES -DenmAdvertisedHost=$ENM_HOST -DkafkaConsumerTopic=$KAFKA_TOPIC -DkafkaConsumerBootstrapServers=$KAFKA_BOOTSTRAP_SERVERS -Dlogback.configurationFile=logback.xml -jar standalone-simulator.jar'
# attach a pipe-pane
tmux pipe-pane -t groovy-shell 'cat>/tmp/tmux-initial'
# wait until first prompt appeared
sleep 0.2
tail -f /tmp/tmux-initial | sed '/groovy >/ q'
# add file for healtcheck
touch /tmp/wiremock-is-ready
gotty --port 8080 --permit-write tmux attach-session -t groovy-shell