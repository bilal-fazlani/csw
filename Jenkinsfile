#!groovy
node {
    def failBuild = false
    try{
        ansiColor('xterm') {
            node('master'){
                stage('Checkout') {
                    git 'https://github.com/tmtsoftware/csw-prod.git'
                }

                stage('Build') {
                    sh "sbt -Dcheck.cycles=true clean scalastyle compile"
                }

                stage('Test') {
                    try {
                        sh "sbt csw-location/test"
                    }
                    catch (Exception e) {
                        currentBuild.result = 'FAILED'
                        failBuild = true
                    }
                    try {
                        sh "sbt track-location-agent/test"
                    }
                    catch (Exception e) {
                        currentBuild.result = 'FAILED'
                        failBuild = true
                    }
                    try{
                        sh "sbt coverageReport"
                    }
                    catch (Exception ex){
                        failBuild = true
                    }
                    sh "sbt coverageAggregate"
                    if(failBuild == true)
                        sh "exit 1"
                }

                stage('Multi-Jvm Test') {
                    sh "sbt csw-location/multi-jvm:test"
                }

                stage('Package') {
                    sh "./universal_package.sh"
                    stash name: "repo"
                }
            }

            node('JenkinsNode1') {
                stage('Integration') {
                    unstash "repo"
                    sh "./integration/scripts/runner.sh '-v /home/ubuntu/workspace/tw-csw-prod/:/source/csw'"
                }
            }

            node('JenkinsNode1'){
                stage('Infra Test') {
                    parallel(
                        "Multiple NIC's": {
                            stage("NIC") {
                                try {
                                    sh "./integration/scripts/multiple_nic_test.sh '-v /home/ubuntu/workspace/tw-csw-prod/:/source/csw'"
                                }
                                catch (Exception ex) {
                                    currentBuild.result = 'FAILED'
                                    throw ex
                                }
                            }
                        },
                        "Multiple Subnet's": {
                            stage("Subnet") {
                                try {
                                       sh "./integration/scripts/multiple_subnets_test.sh '-v /home/ubuntu/workspace/tw-csw-prod/:/source/csw'"
                                }
                                catch (Exception ex) {
                                    currentBuild.result = 'FAILED'
                                    throw ex
                                }
                            }
                        }
                    )
                }
            }

            node('master'){
                stage('Deploy'){
                    sh "./publish.sh"
                }
            }
        }
    }
    catch (Exception e) {
        currentBuild.result = "FAILED"
        throw e
    } finally {
        node('master'){
            stage("Report") {
                sendNotification(currentBuild.result)
                publishJunitReport()
                publishScoverageReport()
            }
        }
    }
}

def sendNotification(String buildStatus = 'STARTED') {
    // build status of null means successful
    buildStatus =  buildStatus ?: 'SUCCESSFUL'

    // Default values
    def colorName = 'RED'
    def colorCode = '#FF0000'
    def subject = "${buildStatus}: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]'"
    def summary = "${subject} (${env.BUILD_URL})"
    def details = '${JELLY_SCRIPT,template="html"}'

    // Override default values based on build status
    if (buildStatus == 'STARTED') {
        color = 'YELLOW'
        colorCode = '#FFFF00'
    } else if (buildStatus == 'SUCCESSFUL') {
        color = 'GREEN'
        colorCode = '#00FF00'
    } else {
        color = 'RED'
        colorCode = '#FF0000'
    }

    slackSend (color: colorCode, message: summary)

    emailext(
            subject: subject,
            body: details,
            to: "tmt-csw@thoughtworks.com"
    )
}

def publishJunitReport(){
    junit '**/target/test-reports/*.xml'
}

def publishScoverageReport() {
    publishHTML (target: [
            allowMissing: true,
            alwaysLinkToLastBuild: false,
            keepAll: true,
            reportDir: './target/scala-2.12/scoverage-report',
            reportFiles: 'index.html',
            reportName: "Scoverage Report"
    ])
}
