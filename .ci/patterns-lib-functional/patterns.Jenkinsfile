/* Temporary pipeline to run integration tests for the patterns lib
*   This uses the same deploy and forward model as the DP1 tests.
*   In the long term, we should swap this out for a managed kafka,
*   as it would be cheaper and more reliable
*/

pipeline {
    agent {
        docker {
            image 'build-zulu-openjdk:11'
            label 'docker'
            registryUrl 'https://engineering-docker.software.r3.com/'
            registryCredentialsId 'artifactory-credentials'
            // Used to mount storage from the host as a volume to persist the cache between builds
            args '-v /tmp:/host_tmp'
            // make sure build image is always fresh
            alwaysPull true
        }
    }


    environment {
        ARTIFACTORY_CREDENTIALS = credentials('artifactory-credentials')
        CORDA_ARTIFACTORY_USERNAME = "${env.ARTIFACTORY_CREDENTIALS_USR}"
        CORDA_ARTIFACTORY_PASSWORD = "${env.ARTIFACTORY_CREDENTIALS_PSW}"
        CORDA_USE_CACHE = "corda-remotes"
        KUBECONFIG=credentials("e2e-tests-credentials")
        CORDA_CLI_USER_HOME="/tmp/corda-cli-home"
        GRADLE_USER_HOME = "/host_tmp/gradle"
        CORDA_REVISION = "${env.GIT_COMMIT}"
        NAME_SPACE = "pat-${UUID.randomUUID().toString()}"
    }

    options {
        buildDiscarder(logRotator(daysToKeepStr: '14', artifactDaysToKeepStr: '14'))
        timeout(time: 30, unit: 'MINUTES')
        timestamps()
    }
    
    triggers {
        cron '@midnight'
    }

    stages {
        stage('Prepare') {
            steps {
                sh 'mkdir -p "${GRADLE_USER_HOME}"'
                // This is very bad
                sh "curl -u '${CORDA_ARTIFACTORY_USERNAME}:${CORDA_ARTIFACTORY_PASSWORD}' https://software.r3.com/artifactory/engineering-tools-maven-unstable/net/corda/cli/corda-cli-developer/[RELEASE]/corda-cli-developer-[RELEASE].tar\\;source.branch+=bmcm%2FNOTICK%2FTest-kafka-deployment --output ./corda-cli.tar"
                sh "rm -rf ./corda-cli && mkdir ./corda-cli"
                sh "tar -C ./corda-cli --strip 1 -xf ./corda-cli.tar"
                sh "./corda-cli/bin/corda-cli -v"
            }
        }
        stage('Build') {
            steps {
                sh "./gradlew assemble"
            }
        }
        stage('Setup network') {
            steps {
                sh './corda-cli/bin/corda-cli cluster config k8s "${NAME_SPACE}"'
                sh './corda-cli/bin/corda-cli cluster deploy -n "${NAME_SPACE}" -f .ci/patterns-lib-functional/cluster-definition.yml | kubectl apply -f -'
            }
        }
        stage('Wait for network') {
            steps {
                sh './corda-cli/bin/corda-cli cluster wait -n ${NAME_SPACE}'
            }
        }
        stage('Forward ports and run the tests') {
            steps {
                sh '''
                    nohup ./corda-cli/bin/corda-cli cluster forward -n "${NAME_SPACE}" > forward.txt 2>&1 &
                    procno=$! #remember process number started in background
                    trap "kill -9 ${procno}" EXIT
                    ./corda-cli/bin/corda-cli cluster wait -n ${NAME_SPACE}
                    port=$(./corda-cli/bin/corda-cli cluster status -n "${NAME_SPACE}" -f json | jq '.services | .[] | .publicPorts | select(.BROKERINTERNAL != null) | .[]' | sort | head -n 1)
                    BROKERS_ADDRS="localhost:9093" ./gradlew kafkaIntegrationTest
                '''
            }
            post {
                always {
                    sh '''
                        for POD in $(kubectl get pods -l 'type in (broker, zookeeper)' -o name --namespace="${NAME_SPACE}" | cut -d'/' -f 2)
                        do
                          echo "${POD}"
                          kubectl --namespace="${NAME_SPACE}" logs "${POD}" | tee build/${POD}-logs.txt
                        done
                        kubectl get events -n "${NAME_SPACE}" | tee build/${NAME_SPACE}-events-log.txt
                        kubectl delete ns "${NAME_SPACE}"
                    '''
                }
                success {
                    sh './gradlew cleanKafkaIntegrationTest'
                }
            }
        }
    }
    post {
        cleanup {
            junit allowEmptyResults: true, testResults: '**/build/test-results/**/TEST-*.xml'
            archiveArtifacts artifacts: '**/build/test-results/**/TEST-*.xml', fingerprint: true, allowEmptyArchive: true
            archiveArtifacts artifacts: "build/${NAME_SPACE}-*-logs.txt", fingerprint: true, allowEmptyArchive: true
            archiveArtifacts artifacts: "build/${NAME_SPACE}-events-log.txt", fingerprint: true, allowEmptyArchive: true
            archiveArtifacts artifacts: 'forward.txt', fingerprint: true, allowEmptyArchive: true
        }
    }
}
