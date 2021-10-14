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

    parameters {
        string(defaultValue: "",
            description: 'Up Stream project name',
            name: 'UPSTREAM_PROJECT_NAME')
    }

    environment {
        ARTIFACTORY_CREDENTIALS = credentials('artifactory-credentials')
        CORDA_ARTIFACTORY_USERNAME = "${env.ARTIFACTORY_CREDENTIALS_USR}"
        CORDA_ARTIFACTORY_PASSWORD = "${env.ARTIFACTORY_CREDENTIALS_PSW}"
        CORDA_USE_CACHE = "corda-remotes"
        GRADLE_USER_HOME = "/host_tmp/gradle"
        CORDA_REVISION = "${env.GIT_COMMIT}"
        NAME_SPACE = "pat-${UUID.randomUUID().toString()}"
    }

    options {
        buildDiscarder(logRotator(daysToKeepStr: '14', artifactDaysToKeepStr: '14'))
        timeout(time: 10, unit: 'MINUTES')
        timestamps()
    }

    stages {
        stage('Prepare') {
            steps {
               sh 'mkdir -p "${GRADLE_USER_HOME}"'
               // This is very bad
               sh "curl -u '${CORDA_ARTIFACTORY_USERNAME}:${CORDA_ARTIFACTORY_PASSWORD}' https://software.r3.com/artifactory/engineering-tools-maven-unstable/net/corda/cli/corda-cli/[RELEASE]/corda-cli-[RELEASE]-install.sh\\;source.branch+=bmcm%2FNOTICK%2FTest-kafka-deployment | bash"
            }
        }
        stage('Build') {
            steps {
                sh "./gradlew assemble"
            }
        }
        stage('Setup network') {
            steps {
                sh 'corda-cli cluster config k8s "${NAME_SPACE}"'
                sh 'corda-cli cluster deploy -n "${NAME_SPACE}" -f .ci/patterns-lib-functional/cluster-definition.yml | kubectl apply -f -'
            }
        }
        stage('Wait for network') {
            steps {
                // Cheat for now until I add a proper wait command to the cli
                sh 'sleep 120'
            }
        }
        stage('Forward ports and run the tests') {
            steps {
                sh '''
                    nohup corda-cli cluster forward -n "${NAME_SPACE}" > forward.txt 2>&1 &
                    procno=$! #remember process number started in background
                    trap "kill -9 ${procno}" EXIT
                    ./gradlew cleanKafkaIntegrationTest kafkaIntegrationTest
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
                    '''
                }
            }
        }
    }
    post {
        cleanup {
            junit allowEmptyResults: true, testResults: '**/build/test-results/**/TEST-*.xml'
            archiveArtifacts artifacts: '**/build/test-results/**/TEST-*.xml', fingerprint: true, allowEmptyArchive: true
            archiveArtifacts artifacts: 'build/*-logs.txt', fingerprint: true, allowEmptyArchive: true
        }
    }
}
