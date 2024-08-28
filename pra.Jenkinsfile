pipeline {
    agent {
        node {
            label 'docker'
        }
    }
    parameters {
        string(name: 'RELEASE_CANDIDATE', description: 'The helm chart release candidate version (e.g. 1.0.0-7)')
        string(name: 'GIT_COMMIT', description: 'The commit hash or the git label pointing to the commit on which PRA is set')
        booleanParam(name: 'DRY_RUN', defaultValue: false, description: 'Enable dry-run')
    }
    stages {
        stage('Prepare') {
            steps {
                sh 'git clean -xdff'
                sh 'git submodule sync'
                sh 'git submodule update --init --recursive'
                sh 'bob --help'
            }
        }
        stage('Cleanup')
        {
            steps {
              sh 'bob -r ruleset2.0.pra.yaml clean'
            }
        }
        stage('Init')
        {
            steps {
              sh 'bob -r ruleset2.0.pra.yaml init'
              archiveArtifacts 'artifact.properties'
            }
        }
        stage('Validate DP-RAF configuration') {
            steps {
                withCredentials([string(credentialsId: 'dpraf-api-token-id',
                                 variable: 'DPRAF_API_TOKEN')])
                {
                    sh 'bob -r ruleset2.0.pra.yaml check-dpraf-configuration'
                }
            }
        }

        stage('Store release artifacts')
        {
            steps {
              withCredentials([
                               usernamePassword(credentialsId: 'artifactory-api-token-id', usernameVariable: 'RELEASED_ARTIFACTS_USER', passwordVariable: 'RELEASED_ARTIFACTS_REPO_API_TOKEN'),
                               usernamePassword(credentialsId: 'artifactory-api-token-id', usernameVariable: 'HELM_USER', passwordVariable: 'HELM_REPO_API_TOKEN'),
                               usernamePassword(credentialsId: 'gerrit-user', usernameVariable: 'GERRIT_USERNAME', passwordVariable: 'GERRIT_PASSWORD')
                              ])
                {
                    sh 'bob -r ruleset2.0.pra.yaml store-release-artifacts'
                }
            }
        }
        stage('Release product structure - Step 1') {
            steps {
                withCredentials([string(credentialsId: 'dpraf-api-token-id',
                                 variable: 'DPRAF_API_TOKEN')])
                {
                    sh 'bob -r ruleset2.0.pra.yaml prim-release-step1'
                    archiveArtifacts 'build/dpraf-output/dpraf_report.json'
                }
            }
        }
        stage('Generate PRI') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'jira-user', usernameVariable: 'JIRA_USERNAME', passwordVariable: 'JIRA_PASSWORD'),
                                 usernamePassword(credentialsId: 'gerrit-user', usernameVariable: 'GERRIT_USERNAME', passwordVariable: 'GERRIT_PASSWORD'),
                                 usernamePassword(credentialsId: 'eridoc-user', usernameVariable: 'ERIDOC_USERNAME', passwordVariable: 'ERIDOC_PASSWORD')
                                ])
                {
                    sh 'bob -r ruleset2.0.pra.yaml generate-pri'
                    publishHTML (target: [
                        allowMissing: false,
                        alwaysLinkToLastBuild: false,
                        keepAll: true,
                        reportDir: 'build/',
                        reportFiles: 'pri.html',
                        reportName: "PRI"
                    ])
                    archiveArtifacts 'build/pri.html'
                    archiveArtifacts 'build/pri.json'
                    archiveArtifacts 'build/pri.pdf'
                }
            }
        }
        stage('Release product structure - Step 2') {
            steps {
                withCredentials([string(credentialsId: 'dpraf-api-token-id',
                                 variable: 'DPRAF_API_TOKEN')])                {
                    sh 'bob -r ruleset2.0.pra.yaml prim-release-step2'
                }
            }
        }
        stage('Publish released Docker Images') {
            steps {
                     sh 'bob -r ruleset2.0.pra.yaml image publish-released-docker-image'
                  }
        }
        stage('Publish released helm chart') {
            steps {
                withCredentials([
                    usernamePassword(credentialsId: 'artifactory-api-token-id',
                                    usernameVariable: 'HELM_USER',
                                    passwordVariable: 'HELM_REPO_API_TOKEN')]) {
                    sh 'bob -r ruleset2.0.pra.yaml publish-released-helm-chart'
                }
            }
        }
// Temporarily disabled until the EVMS guys fix the infrastructure
/*
        stage('Register new version in EVMS') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'evms-user', usernameVariable: 'EVMS_USERNAME', passwordVariable: 'EVMS_PASSWORD')])
                {
                    sh 'bob -r ruleset2.0.pra.yaml evms-registration'
                }
            }
        }
*/
        stage('Create PRA Git Tag')
        {
            steps {
                withCredentials([usernamePassword(credentialsId: 'gerrit-user', usernameVariable: 'GERRIT_USERNAME', passwordVariable: 'GERRIT_PASSWORD')])
                {
                    sh 'bob -r ruleset2.0.pra.yaml create-pra-git-tag'
                }
            }
        }
        stage('Increment version prefix')
        {
            steps {
                withCredentials([usernamePassword(credentialsId: 'gerrit-user', usernameVariable: 'GERRIT_USERNAME', passwordVariable: 'GERRIT_PASSWORD')])
                {
                    sh 'bob -r ruleset2.0.pra.yaml increment-version-prefix'
                }
            }
        }
        // This stage is executed just for housekeeping to remove the image artifacts from the slave
        stage('Final Cleanup')
        {
          steps {
            sh 'bob -r ruleset2.0.pra.yaml clean'
          }
        }
    }
 }