@Library('existing-build-control')
import static com.r3.build.BuildControl.killAllExistingBuildsForJob
killAllExistingBuildsForJob(env.JOB_NAME, env.BUILD_NUMBER.toInteger())

/**
 * Sense environment
 */
boolean isReleaseBranch = (env.BRANCH_NAME =~ /^release\/.*/)
boolean isRelease = (env.TAG_NAME =~ /^release_.*/)
def nexusDefaultIqStage = "build"
def nexusIqStageChoices = [nexusDefaultIqStage].plus(
                [
                        'develop',
                        'build',
                        'stage-release',
                        'release',
                        'operate'
                ].minus([nexusDefaultIqStage]))

pipeline {
    agent {
        label "docker"
    }
//     agent {
//         dockerfile {
//             filename '.ci/Dockerfile'
//         }
//     }

    parameters {
        booleanParam defaultValue: (isReleaseBranch || isRelease), description: 'Publish artifacts to Artifactory?', name: 'DO_PUBLISH'
        choice choices: nexusIqStageChoices, description: 'NexusIQ stage for code evaluation', name: 'nexusIqStage'
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
                sh "./gradlew detekt"
            }
        }

        stage('Tests') {
            steps {
                sh "./gradlew clean test --info"
            }
        }

        stage('Sonatype Check') {
                    steps {
                        script {
                            sh "./gradlew --no-daemon properties | grep -E '^(version|group):' >version-properties"
                            /* every build related to Corda X.Y (GA, RC, HC, patch or snapshot) uses the same NexusIQ application */
                            def version = sh (returnStdout: true, script: "grep ^version: version-properties | sed -e 's/^version: \\([0-9]\\+\\(\\.[0-9]\\+\\)\\+\\).*\$/\\1/'").trim()
                            //def groupId = sh (returnStdout: true, script: "grep ^group: version-properties | sed -e 's/^group: //'").trim()
                            def artifactId = 'flow-worker'
                            nexusAppId = "${artifactId}-${version}"
                        }
                        nexusPolicyEvaluation (
                                failBuildOnNetworkError: false,
                                iqApplication: selectedApplication(nexusAppId), // application *has* to exist before a build starts!
                                iqScanPatterns: [[scanPattern: 'build/libs/flow-worker*.jar']],
                                iqStage: params.nexusIqStage
                        )
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