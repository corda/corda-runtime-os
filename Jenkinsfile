@Library('existing-build-control')
import static com.r3.build.BuildControl.killAllExistingBuildsForJob
killAllExistingBuildsForJob(env.JOB_NAME, env.BUILD_NUMBER.toInteger())

/**
 * Sense environment
 */
boolean isReleaseBranch = (env.BRANCH_NAME =~ /^release\/.*/)
boolean isRelease = (env.TAG_NAME =~ /^release_.*/)

pipeline {
//     agent {
//         dockerfile {
//             filename '.ci/Dockerfile'
//         }
//     }

    parameters {
        booleanParam defaultValue: (isReleaseBranch || isRelease), description: 'Publish artifacts to Artifactory?', name: 'DO_PUBLISH'
    }

    options {
        ansiColor('xterm')
        timestamps()
        timeout(3*60) // 3 hours
    }

    environment {
        EXECUTOR_NUMBER = "${env.EXECUTOR_NUMBER}"
        ARTIFACTORY_CREDENTIALS = credentials('artifactory-credentials')
        CORDA_ARTIFACTORY_USERNAME = "${env.ARTIFACTORY_CREDENTIALS_USR}"
        CORDA_ARTIFACTORY_PASSWORD = "${env.ARTIFACTORY_CREDENTIALS_PSW}"
        ARTIFACTORY_BUILD_NAME = "Flow worker/Jenkins/${!isRelease?"snapshot/":""}${env.BRANCH_NAME}".replaceAll("/", " :: ")
    }

    stages {
        stage('Detekt') {
            steps {
                //sh "./gradlew detekt"
                sh "print 'Detekt step'"
            }
        }

        stage('Tests') {
            steps {
                //sh "./gradlew clean test --info"
                sh "print 'Test step'"
            }
        }

        stage('Publish to Artifactory') {
            when {
                expression { params.DO_PUBLISH }
                beforeAgent true
            }
            steps {
                rtServer(
                        id: 'R3-Artifactory',
                        url: 'https://software.r3.com/artifactory',
                        credentialsId: 'artifactory-credentials'
                )
                rtGradleDeployer(
                        id: 'deployer',
                        serverId: 'R3-Artifactory',
                        repo: isRelease ? 'r3-corda-releases' : 'r3-corda-dev'
                )
                rtGradleRun(
                        usesPlugin: true,
                        useWrapper: true,
                        switches: '-s --info',
                        tasks: 'artifactoryPublish',
                        deployerId: 'deployer',
                        buildName: env.ARTIFACTORY_BUILD_NAME
                )
                rtPublishBuildInfo(
                        serverId: 'R3-Artifactory',
                        buildName: env.ARTIFACTORY_BUILD_NAME
                )
            }
        }
    }

    post {
        always {
            junit testResults: '**/build/test-results/**/*.xml', allowEmptyResults: true
        }
        cleanup {
            deleteDir() /* clean up our workspace */
        }
    }
}