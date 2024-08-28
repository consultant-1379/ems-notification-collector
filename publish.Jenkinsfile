#!/usr/bin/env groovy

def defaultBobImage = 'armdocker.rnd.ericsson.se/proj-adp-cicd-drop/bob.2.0:1.7.0-55'
def bob = new BobCommand()
    .bobImage(defaultBobImage)
    .envVars([
        HOME:'${HOME}',
        ISO_VERSION:'${ISO_VERSION}',
        HELM_REPO_API_TOKEN:'${HELM_REPO_API_TOKEN}',
        RELEASE:'${RELEASE}',
        SONAR_HOST_URL:'${SONAR_HOST_URL}',
        SONAR_AUTH_TOKEN:'${SONAR_AUTH_TOKEN}',
        GERRIT_CHANGE_NUMBER:'${GERRIT_CHANGE_NUMBER}',
        KUBECONFIG:'${KUBECONFIG}',
        USER:'${USER}',
        SELI_ARTIFACTORY_REPO_USER:'${CREDENTIALS_SELI_ARTIFACTORY_USR}',
        SELI_ARTIFACTORY_REPO_PASS:'${CREDENTIALS_SELI_ARTIFACTORY_PSW}',
        SERO_ARTIFACTORY_REPO_USER:'${CREDENTIALS_SERO_ARTIFACTORY_USR}',
        SERO_ARTIFACTORY_REPO_PASS:'${CREDENTIALS_SERO_ARTIFACTORY_PSW}',
        MAVEN_CLI_OPTS: '${MAVEN_CLI_OPTS}',
        ADP_PORTAL_API_KEY: '${ADP_PORTAL_API_KEY}'
    ])
    .needDockerSocket(true)
    .toString()

def LOCKABLE_RESOURCE_LABEL = "kaas"

pipeline {
    agent {
        node {
            label NODE_LABEL
        }
    }

    options {
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '50', artifactNumToKeepStr: '50'))
    }

    environment {
        RELEASE = "true"
        TEAM_NAME = "Bora"
        KUBECONFIG = "${WORKSPACE}/.kube/config"
        CREDENTIALS_SELI_ARTIFACTORY = credentials('SELI_ARTIFACTORY')
        CREDENTIALS_SERO_ARTIFACTORY = credentials('SERO_ARTIFACTORY')
        MAVEN_CLI_OPTS = "-Duser.home=${env.HOME} -B -s ${env.WORKSPACE}/settings.xml"
    }

    // Stage names (with descriptions) taken from ADP Microservice CI Pipeline Step Naming Guideline: https://confluence.lmera.ericsson.se/pages/viewpage.action?pageId=122564754
    stages {
        stage('Clean') {
            steps {
                echo 'Inject settings.xml into workspace:'
                configFileProvider([configFile(fileId: "${env.SETTINGS_CONFIG_FILE_NAME}", targetLocation: "${env.WORKSPACE}")]) {}
                archiveArtifacts allowEmptyArchive: true, artifacts: 'ruleset2.0.yaml, publish.Jenkinsfile'
                sh "${bob} clean"
            }
        }

        stage('Init') {
            steps {
                sh "${bob} init-drop"
                archiveArtifacts 'artifact.properties'
            }
        }

        stage('Lint') {
            steps {
                parallel(
                    "lint markdown": {
                        sh "${bob} lint:markdownlint lint:vale"
                    },
                    "lint zally": {
                        sh "${bob} lint:lint-api-schema"
                    },
                    "lint helm": {
                        sh "${bob} lint:helm"
                    },
                    "lint helm design rule checker": {
                        sh "${bob} lint:helm-chart-check"
                    },
                    "lint code": {
                        sh "${bob} lint:license-check"
                    }
                )
            }
            post {
                always {
                    archiveArtifacts allowEmptyArchive: true, artifacts: 'zally-api-lint-report.txt, .bob/design-rule-check-report.*'
                }
            }
        }

        stage('Generate') {
            steps {
                parallel(
                    "Open API Spec": {
                        sh "${bob} rest-2-html:check-has-open-api-been-modified"
                        script {
                            def val = readFile '.bob/var.has-openapi-spec-been-modified'
                            if (val.trim().equals("true")) {
                                sh "${bob} rest-2-html:zip-open-api-doc"
                                sh "${bob} rest-2-html:generate-html-output-files"

                                manager.addInfoBadge("OpenAPI spec has changed. HTML Output files will be published to the CPI library.")
                                archiveArtifacts artifacts: "rest_conversion_log.txt"
                            }
                        }
                    },
                    "Generate Docs": {
                        sh "${bob} generate-docs"
                        archiveArtifacts allowEmptyArchive: true, artifacts: "docs/target/generated-docs-archives/*"
                        publishHTML (target: [
                            allowMissing: false,
                            alwaysLinkToLastBuild: false,
                            keepAll: true,
                            reportDir: 'docs/target/generated-docs-archives',
                            reportFiles: 'CTA_api.html',
                            reportName: 'REST API Documentation'
                        ])
                    }
                )
            }
        }

        stage('Upload Marketplace Documentation') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'SELI_ARTIFACTORY', usernameVariable: 'SELI_ARTIFACTORY_REPO_USER', passwordVariable: 'SELI_ARTIFACTORY_REPO_PASS'),
                                 string(credentialsId: 'adp-portal-token-id', variable: 'ADP_PORTAL_API_KEY')]) {
                    // upload dev version
                    sh "${bob} marketplace-upload-dev"

                    // upload release version
                    sh "${bob} marketplace-upload-release"
                }
            }
        }

        stage('Build') {
            steps {
                sh "${bob} build"
            }
        }

        stage('Test') {
            steps {
                sh "${bob} test"
            }
        }

        stage('SonarQube') {
            when {
                expression { env.SQ_ENABLED == "true" }
            }
            steps {
                withSonarQubeEnv("${env.SQ_SERVER}") {
                    sh "${bob} sonar-enterprise-release"
                }
            }
        }

        stage('Image') {
            steps {
                sh "${bob} image"
                sh "${bob} image-dr-check"
            }
            post {
                always {
                    archiveArtifacts allowEmptyArchive: true, artifacts: '**/image-design-rule-check-report*'
                }
            }
        }

        stage('Package') {
            steps {
                script {
                    withCredentials([string(credentialsId: 'HELM_SELI_REPO_API_TOKEN', variable: 'HELM_REPO_API_TOKEN')]) {
                        sh "${bob} package"
                    }
                    retryMechanism("${bob} package-jars", 5)
                }
            }
        }

        stage('K8S Resource Lock') {
            options {
                lock(label: LOCKABLE_RESOURCE_LABEL, variable: 'RESOURCE_NAME', quantity: 1)
            }
            environment {
                K8S_CONFIG_FILE_ID = sh(script: "echo \${RESOURCE_NAME} | cut -d'_' -f1", returnStdout: true).trim()
            }
            when{
                expression { env.K8S_TEST == "true"}
            }
            stages {
                stage('Helm Install') {
                    steps {
                        echo "Inject kubernetes config file (${env.K8S_CONFIG_FILE_ID}) based on the Lockable Resource name: ${env.RESOURCE_NAME}"
                        configFileProvider([configFile(fileId: "${K8S_CONFIG_FILE_ID}", targetLocation: "${env.KUBECONFIG}")]) {}

                        sh "${bob} helm-dry-run"
                        sh "${bob} create-namespace"
                        sh "${bob} helm-install-umbrella-chart"
                        sh "${bob} helm-install-context-simulator"
                        sh "${bob} healthcheck"
                    }
                    post {
                        unsuccessful {
                            sh "${bob} collect-k8s-logs || true"
                            archiveArtifacts allowEmptyArchive: true, artifacts: 'printenv.log, kubectl.config, collect_ADP_logs.sh, logs_*.tgz, helm-install-dry-run.log'
                            sh "${bob} delete-namespace"
                        }
                    }
                }

                stage('K8S Test') {
                    steps {
                        sh "${bob} helm-test"
                    }
                    post {
                        unsuccessful {
                            sh "${bob} collect-k8s-logs || true"
                            archiveArtifacts allowEmptyArchive: true, artifacts: 'k8s-logs/**/*.*'
                        }
                        cleanup {
                            sh "${bob} delete-namespace"
                        }
                    }
                }
            }
        }

        stage('Publish') {
            steps {
                withCredentials([string(credentialsId: 'HELM_SELI_REPO_API_TOKEN', variable: 'HELM_REPO_API_TOKEN')]) {
                    sh "${bob} publish"
                }
                sh "${bob} publish-jars"
                sh "${bob} image-publish-latest"
            }
        }
    }
    post {
        success {
            script {
                bumpVersionPrefixPatch()
                sh "${bob} helm-chart-check-report-warnings"
                sendHelmDRWarningEmail()
            }
        }
        unsuccessful {
            sendFailNotificationMail()
        }
    }
}

def retryMechanism(shellCommandToExecute, timeoutValueInMins) {
    timeout(time: "${timeoutValueInMins}", unit: 'MINUTES') {
        waitUntil {
            script {
                try {
                    sh "${shellCommandToExecute}"
                    return true
                } catch(Exception e) {
                    return false
                }
            }
        }
    }
}

def sendHelmDRWarningEmail() {
    def val = readFile '.bob/var.helm-chart-check-report-warnings'
    if (val.trim().equals("true")) {
        echo "WARNING: One or more Helm Design Rules have a WARNING state. Review the Archived Helm Design Rule Check Report: design-rule-check-report.html"
        manager.addWarningBadge("One or more Helm Design Rules have a WARNING state. Review the Archived Helm Design Rule Check Report: design-rule-check-report.html")
        echo "Sending an email to Helm Design Rule Check distribution list: ${env.HELM_DR_CHECK_DISTRIBUTION_LIST}"
        try {
            mail to: "${env.HELM_DR_CHECK_DISTRIBUTION_LIST}",
            from: "${env.GERRIT_PATCHSET_UPLOADER_EMAIL}",
            cc: "${env.GERRIT_PATCHSET_UPLOADER_EMAIL}",
            subject: "[${env.JOB_NAME}] One or more Helm Design Rules have a WARNING state. Review the Archived Helm Design Rule Check Report: design-rule-check-report.html",
            body: "One or more Helm Design Rules have a WARNING state. <br><br>" +
            "Please review Gerrit and the Helm Design Rule Check Report: design-rule-check-report.html: <br><br>" +
            "&nbsp;&nbsp;<b>Gerrit master branch:</b> https://gerrit.ericsson.se/gitweb?p=${env.GERRIT_PROJECT}.git;a=shortlog;h=refs/heads/master <br>" +
            "&nbsp;&nbsp;<b>Helm Design Rule Check Report:</b> ${env.BUILD_URL}artifact/.bob/design-rule-check-report.html <br><br>" +
            "For more information on the Design Rules and ADP handling process please see: <br>" +
            "&nbsp;&nbsp; - <a href='https://confluence.lmera.ericsson.se/display/AA/Helm+Chart+Design+Rules+and+Guidelines'>Helm Design Rule Guide</a><br>" +
            "&nbsp;&nbsp; - <a href='https://confluence.lmera.ericsson.se/display/ACD/Design+Rule+Checker+-+How+DRs+are+checked'>More Details on Design Rule Checker</a><br>" +
            "&nbsp;&nbsp; - <a href='https://confluence.lmera.ericsson.se/display/AA/General+Helm+Chart+Structure'>General Helm Chart Structure</a><br><br>" +
            "<b>Note:</b> This mail was automatically sent as part of the following Jenkins job: ${env.BUILD_URL}",
            mimeType: 'text/html'
        } catch(Exception e) {
            echo "Email notification was not sent."
            print e
        }
    }
}

def sendFailNotificationMail() {
    echo "WARNING: Publish job execution failed! Please have someone from the team take a look!"
    manager.addWarningBadge("Publish job execution failed !!!")
    echo "Sending an email to Team Bora distribution list: ${env.HELM_DR_CHECK_DISTRIBUTION_LIST}"
    try {
        mail to: "${env.HELM_DR_CHECK_DISTRIBUTION_LIST}",
                from: "${env.GERRIT_PATCHSET_UPLOADER_EMAIL}",
                cc: "${env.GERRIT_PATCHSET_UPLOADER_EMAIL}",
                subject: "[${env.JOB_NAME}] Publish job execution failed !!!",
                body: "Publish job execution failed! <br><br>" +
                        "Please review your latest commit. <br><br>" +
                        "&nbsp;&nbsp;<b>Gerrit master branch:</b> https://gerrit.ericsson.se/gitweb?p=${env.GERRIT_PROJECT}.git;a=shortlog;h=refs/heads/master <br>" +
                        "&nbsp;&nbsp;<b>Failed job:</b> ${env.BUILD_URL} <br><br>" +
                        "<b>Note:</b> This mail was automatically sent as part of the following Jenkins job: ${env.BUILD_URL}",
                mimeType: 'text/html'
    } catch (Exception e) {
        echo "Failed job email notification was not sent."
        print e
    }
}

def bumpVersionPrefixPatch() {
    env.oldPatchVersionPrefix = readFile "VERSION_PREFIX"
    env.VERSION_PREFIX_CURRENT = env.oldPatchVersionPrefix.trim()

    sh 'docker run --rm -v $PWD/VERSION_PREFIX:/app/VERSION -w /app --user $(id -u):$(id -g) armdocker.rnd.ericsson.se/proj-eric-oss-drop/utilities/bump patch'

    env.newPatchVersionPrefix = readFile "VERSION_PREFIX"
    env.VERSION_PREFIX_UPDATED = env.newPatchVersionPrefix.trim()

    if (env.PUSH_VERSION_PREFIX_FILE == "true") {
        echo "VERSION_PREFIX has been bumped from ${VERSION_PREFIX_CURRENT} to ${VERSION_PREFIX_UPDATED}"

        sh """
            git add VERSION_PREFIX
            git commit -m "[ci-skip] Automatic new patch version bumping: ${VERSION_PREFIX_UPDATED}"
            git push origin HEAD:master
        """
    }
}

// More about @Builder: http://mrhaki.blogspot.com/2014/05/groovy-goodness-use-builder-ast.html
import groovy.transform.builder.Builder
import groovy.transform.builder.SimpleStrategy

@Builder(builderStrategy = SimpleStrategy, prefix = '')
class BobCommand {
    def bobImage = 'bob.2.0:latest'
    def envVars = [:]
    def needDockerSocket = false

    String toString() {
        def env = envVars
                .collect({ entry -> "-e ${entry.key}=\"${entry.value}\"" })
                .join(' ')

        def cmd = """\
            |docker run
            |--init
            |--rm
            |--workdir \${PWD}
            |--user \$(id -u):\$(id -g)
            |-v \${PWD}:\${PWD}
            |-v /etc/group:/etc/group:ro
            |-v /etc/passwd:/etc/passwd:ro
            |-v \${HOME}:\${HOME}
            |${needDockerSocket ? '-v /var/run/docker.sock:/var/run/docker.sock' : ''}
            |${env}
            |\$(for group in \$(id -G); do printf ' --group-add %s' "\$group"; done)
            |--group-add \$(stat -c '%g' /var/run/docker.sock)
            |${bobImage}
            |"""
        return cmd
                .stripMargin()           // remove indentation
                .replace('\n', ' ')      // join lines
                .replaceAll(/[ ]+/, ' ') // replace multiple spaces by one
    }
}