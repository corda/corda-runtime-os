@Library('corda-shared-build-pipeline-steps@5.0') _

import groovy.transform.Field

@Field
String postgresHost = 'localhost'
@Field
String postgresPort = '5432'
@Field
String postgresCredentialsId = 'e2e-postgresql-credentials'
@Field
String postgresDb = "test_${UUID.randomUUID()}"

pipeline {
    agent {
        docker {
            image 'build-zulu-openjdk:11'
            label 'docker'
            registryUrl 'https://engineering-docker.software.r3.com/'
            registryCredentialsId 'artifactory-credentials'
            // Volume used to mount storage from the host as a volume to persist the cache between builds
            args '-v /tmp:/host_tmp '
            // make sure build image is always fresh
            alwaysPull true
        }
    }
    environment {
        ARTIFACTORY_CREDENTIALS = credentials('artifactory-credentials')
        BUILD_CACHE_CREDENTIALS = credentials('gradle-ent-cache-credentials')
        BUILD_CACHE_USERNAME = "${env.BUILD_CACHE_CREDENTIALS_USR}"
        BUILD_CACHE_PASSWORD = "${env.BUILD_CACHE_CREDENTIALS_PSW}"
        CORDA_ARTIFACTORY_USERNAME = "${env.ARTIFACTORY_CREDENTIALS_USR}"
        CORDA_ARTIFACTORY_PASSWORD = "${env.ARTIFACTORY_CREDENTIALS_PSW}"
        CORDA_USE_CACHE = "corda-remotes"
        KUBECONFIG = credentials("e2e-tests-credentials")
        CORDA_CLI_USER_HOME = "/tmp/corda-cli-home"
        CORDA_GRADLE_SCAN_KEY = credentials('gradle-build-scans-key')
        GRADLE_USER_HOME = "/host_tmp/gradle"
        CORDA_REVISION = "${env.GIT_COMMIT}"
        GRADLE_PERFORMANCE_TUNING = "--parallel -Dscan.tag.E2E -Dscan.tag.${env.NAMESPACE} --build-cache"
    }
    options {
        buildDiscarder(logRotator(daysToKeepStr: '14', artifactDaysToKeepStr: '14'))
        timeout(time: 30, unit: 'MINUTES')
        timestamps()
    }
    stages {
		  //stage create DB - see shared pipline
        // stage('Create DBs') {
        //     environment {
        //         KUBECONFIG = credentials('e2e-tests-credentials')
        //     }
        //     steps {
        //         // port forwarding from K8s
        //         withEnv(["PGPORT=${postgresPort}"]) {
        //             sh 'nohup kubectl port-forward --namespace postgres svc/postgres-postgresql "${PGPORT}":"${PGPORT}" > forward.txt 2>&1 &'
        //         }
        //         // create new DB
        //         withEnv([
        //                 "PGHOST=${postgresHost}",
        //                 "PGPORT=${postgresPort}",
        //                 "DATABASE=${postgresDb}"
        //         ]) {
        //             withCredentials([usernamePassword(credentialsId: postgresCredentialsId,
        //                     passwordVariable: 'PGPASSWORD',
        //                     usernameVariable: 'PGUSER')]) {
        //                 script {
        //                     try {
        //                         sh 'psql --quiet --tuples-only -c \'select \''
        //                     } catch (error) {
        //                         echo "${error.getMessage()}\nPort forwarding Postgres has not been set up yet, retrying"
        //                         retry(5) {
        //                             sleep(time: 5, unit: "SECONDS")
        //                             sh 'psql --quiet --tuples-only -c \'select \''
        //                         }
        //                     }
        //                     sh 'createdb -w "${DATABASE}"'
        //                 }
        //             }
        //         }
        //     }
        // }
    /// Build the Jar
        stage('Build  Jar') {
            steps {
                sh './gradlew :applications:workers:release:combined-worker:assemble -Si' // builds the jar build/bin/*/*.jar
            }
        }
 			
        //Start the java process in background
        // stage('Start jar / run tests') {
        //     steps {
        //     // we need the DB credetals here also
        //     environment {
        //         JAVA_TOOL_OPTIONS = " see scren shot on jira "
        //     }
        //     //JAVA_TOOL_OPTS env var to pass extra paramaters such as vm paramaters
        //         //somehow ensure this runs in background
        //         sh "java -jar ${env.WORKSPACE}/applications.... <location to combined worker> <other paramaters>  >> workerLogs.txt 2>&1 &"
        //         // execute smoke tests , we may need some paramaters specific to combined worker
        //         ./gradlew smoketest -PcombinedWorkerHealthHttp=some value
        //         // if we do this needs to be all in one stage
        //         procno=$! #remember process number started in background
        //         trap "kill -9 ${procno}" EXIT
        //     }
        // }
    }
    post {
        always {
	// remove Database see here https://github.com/corda/corda-shared-build-pipeline-steps/blob/5.0/vars/cordaPipeline.groovy#L487-L500
            script{
                writeFile file: "workerLogs.log", text: "${env.BUILD_URL}\n${NAMESPACE}"
                archiveArtifacts artifacts: "e2eTestDataForSplunk.log", fingerprint: true
            }
        }
    }
}