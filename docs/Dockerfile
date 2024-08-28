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

ARG VERSION
FROM armdocker.rnd.ericsson.se/proj-adp-cicd-drop/bob-java11mvnbuilder:${VERSION}
RUN zypper install -l -y graphviz dejavu-fonts shadow\
    && zypper clean --all

ARG UID
ARG GID
ARG HOME
RUN groupadd -g ${GID} builder-group  \
    && useradd -u ${UID} -g ${GID} -d ${HOME} builder
