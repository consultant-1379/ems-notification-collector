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
        MAVEN_CLI_OPTS: '${MAVEN_CLI_OPTS}'
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
        timeout(time: 60, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '50', artifactNumToKeepStr: '50'))
    }

    environment {
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
                archiveArtifacts allowEmptyArchive: true, artifacts: 'ruleset2.0.yaml, precodereview.Jenkinsfile'
                sh "${bob} clean"
            }
        }

        stage('Init') {
            steps {
                sh "${bob} init-precodereview"
                script {
                    authorName = sh(returnStdout: true, script: 'git show -s --pretty=%an')
                    currentBuild.displayName = currentBuild.displayName + ' / ' + authorName
                }
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
                        sh "${bob} lint:formatting-check"
                    }
                )
            }
            post {
                always {
                    archiveArtifacts allowEmptyArchive: true, artifacts: 'zally-api-lint-report.txt, .bob/design-rule-check-report.*'
                }
            }
        }

        stage('Build') {
            steps {
                sh "${bob} build"
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
                                sh "${bob} rest-2-html:zip-open-api-doc || true"
                                sh "${bob} rest-2-html:generate-html-output-files || true"

                                manager.addInfoBadge("OpenAPI spec has changed. Review the Archived HTML Output files: rest2html*.zip")
                                archiveArtifacts allowEmptyArchive: true, artifacts: "rest_conversion_log.txt, rest2html*.zip"

                                echo "Sending email to CPI document reviewers distribution list: ${env.CPI_DOCUMENT_REVIEWERS_DISTRIBUTION_LIST}"
                                try {
                                    mail to: "${env.CPI_DOCUMENT_REVIEWERS_DISTRIBUTION_LIST}",
                                            from: "${env.GERRIT_PATCHSET_UPLOADER_EMAIL}",
                                            cc: "${env.GERRIT_PATCHSET_UPLOADER_EMAIL}",
                                            subject: "[${env.JOB_NAME}] OpenAPI specification has been updated and is up for review",
                                            body: "The OpenAPI spec documentation has been updated.<br><br>" +
                                                    "Please review the patchset and archived HTML output files (rest2html*.zip) linked here below:<br><br>" +
                                                    "&nbsp;&nbsp;Gerrit Patchset: ${env.GERRIT_CHANGE_URL}<br>" +
                                                    "&nbsp;&nbsp;HTML output files: ${env.BUILD_URL}artifact <br><br><br><br>" +
                                                    "<b>Note:</b> This mail was automatically sent as part of the following Jenkins job: ${env.BUILD_URL}",
                                            mimeType: 'text/html'
                                } catch(Exception e) {
                                    echo "Email notification was not sent."
                                    print e
                                }
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

        stage('Test') {
            steps {
                sh "${bob} test"
            }
        }

        stage('SonarQube Analysis') {
            when {
                expression { env.SQ_ENABLED == "true" }
            }
            steps {
                withSonarQubeEnv("${env.SQ_SERVER}") {
                    sh "${bob} sonar-enterprise-pcr"
                }
            }
        }

        stage('SonarQube Quality Gate') {
            when {
                expression { env.SQ_ENABLED == "true" }
            }
            steps {
                timeout(time: 5, unit: 'MINUTES') {
                    waitUntil {
                        withSonarQubeEnv("${env.SQ_SERVER}") {
                            script {
                                return getQualityGate()
                            }
                        }
                    }
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

        stage('Build and Push simulator') {
            steps {
                sh "${bob} simulator-image-build"
                sh "${bob} simulator-image-push"
            }
        }

        stage('Integration and Robustness') {
            parallel {

                stage('Integration test') {
                    steps {
                        script {
                            withCredentials([string(credentialsId: 'HELM_SELI_REPO_API_TOKEN', variable: 'HELM_REPO_API_TOKEN')]) {
                                sh "${bob} integration-test"
                            }
                        }
                    }
                }

                stage('K8S Resource Lock') {
                    when {
                        expression { true }
                    }
                    options {
                        lock(label: LOCKABLE_RESOURCE_LABEL, variable: 'RESOURCE_NAME', quantity: 1)
                    }
                    environment {
                        K8S_CONFIG_FILE_ID = sh(script: "echo \${RESOURCE_NAME} | cut -d'_' -f1", returnStdout: true).trim()
                    }
                    stages {
                        stage('Helm Install') {
                            steps {
                                echo "Inject kubernetes config file (${env.K8S_CONFIG_FILE_ID}) based on the Lockable Resource name: ${env.RESOURCE_NAME}"
                                configFileProvider([configFile(fileId: "${K8S_CONFIG_FILE_ID}", targetLocation: "${env.KUBECONFIG}")]) {}

                                sh "${bob} create-namespace"
                                sh "${bob} helm-install-umbrella-chart"
                                sh "${bob} helm-install-context-simulator"
                                sh "${bob} healthcheck"
                            }
                            post {
                                unsuccessful {
                                    sh "${bob} collect-k8s-logs || true"
                                    archiveArtifacts allowEmptyArchive: true, artifacts: 'printenv.log, kubectl.config, collect_ADP_logs.sh, logs_*.tgz'
                                    sh "${bob} delete-namespace"
                                }
                            }
                        }

                        stage('K8S Robustness test') {
                            steps {
                                script {
                                    withCredentials([string(credentialsId: 'HELM_SELI_REPO_API_TOKEN', variable: 'HELM_REPO_API_TOKEN')]) {
                                        sh "${bob} k8s-robustness-test"
                                    }
                                }
                            }
                            post {
                                unsuccessful {
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
            }
        }
    }
    post {
        success {
            script {
                sh "${bob} helm-chart-check-report-warnings"
                addHelmDRWarningIcon()
            }
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

def addHelmDRWarningIcon() {
    def val = readFile '.bob/var.helm-chart-check-report-warnings'
    if (val.trim().equals("true")) {
        echo "WARNING: One or more Helm Design Rules have a WARNING state. Review the Archived Helm Design Rule Check Report: design-rule-check-report.html"
        manager.addWarningBadge("One or more Helm Design Rules have a WARNING state. Review the Archived Helm Design Rule Check Report: design-rule-check-report.html")
    } else {
        echo "No Helm Design Rules have a WARNING state"
    }
}

def getQualityGate() {
    echo "Wait for SonarQube Analysis is done and Quality Gate is pushed back:"
    try {
        timeout(time: 30, unit: 'SECONDS') {
            qualityGate = waitForQualityGate()
        }
    } catch(Exception e) {
        return false
    }
    
    echo 'If Analysis file exists, parse the Dashboard URL:'
    if (fileExists(file: 'target/sonar/report-task.txt')) {
        sh 'cat target/sonar/report-task.txt'
        def props = readProperties file: 'target/sonar/report-task.txt'
        currentBuild.description = '<a href="' + props['dashboardUrl'] + '">SonarQube analysis</a>'
        env.DASHBOARD_URL = props['dashboardUrl']
    }

    if (qualityGate.status.replaceAll("\\s","") == 'IN_PROGRESS') {
        return false
    }

    if (!env.GERRIT_HOST) {
        env.GERRIT_HOST = "gerrit.ericsson.se"
    }

    if (qualityGate.status.replaceAll("\\s","") != 'OK') {
        env.SQ_MESSAGE="'"+"SonarQube Quality Gate Failed: ${DASHBOARD_URL}"+"'"
        if (env.GERRIT_CHANGE_NUMBER) {
            sh '''
                ssh -p 29418 ${GERRIT_HOST} gerrit review --label 'SQ-Quality-Gate=-1' --message ${SQ_MESSAGE} --project ${GERRIT_PROJECT} ${GERRIT_PATCHSET_REVISION}
            '''
        }
        manager.addWarningBadge("Pipeline aborted due to Quality Gate failure, see SonarQube Dashboard for more information.")
        error "Pipeline aborted due to quality gate failure!\n Report: ${env.DASHBOARD_URL}\n Pom might be incorrectly defined for code coverage: https://confluence-oss.seli.wh.rnd.internal.ericsson.com/pages/viewpage.action?pageId=309793813"
    } else {
        env.SQ_MESSAGE="'"+"SonarQube Quality Gate Passed: ${DASHBOARD_URL}"+"'"
        if (env.GERRIT_CHANGE_NUMBER) { // If Quality Gate Passed
            sh '''
                ssh -p 29418 ${GERRIT_HOST} gerrit review --label 'SQ-Quality-Gate=+1' --message ${SQ_MESSAGE} --project ${GERRIT_PROJECT} ${GERRIT_PATCHSET_REVISION}
            '''
        }
    }
    return true
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