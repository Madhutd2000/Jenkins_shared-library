def call(Map config = [:]) {
    String appName = config.appName
    String teamName = config.teamName
    String contextRoot = config.contextRoot
    String deployXmlPath = config.deployXmlPath
    String checkmarxProjectName = config.checkmarxProjectName
    
    String xldEnvironmentId
    String ghOwner 

    pipeline {
        agent {
            kubernetes {
                label 'kpack'
                defaultContainer 'kpack'
                yaml libraryResource('agents/k8s/kpack.yaml')
            }
        }

        parameters {

            choice(
                    name: 'DEPLOYMENT_ENVIRONMENT',
                    choices: "dev\n" +
                            "local\n" +
                            "test\n" +
                            "load\n" +
                            "stage\n" +
                            "cert\n",
                    description: 'Which environment to deploy to?')
            booleanParam(name: 'RELEASE_BUILD', defaultValue: false, description: "Is this a release build?")
            string(
                name: 'CONTEXT_ROOT',
                defaultValue: "${contextRoot}",
                trim: true
            )

            booleanParam(name: 'RUN_SONARQUBE_SCAN', defaultValue: true, description: 'Scan code w/ SonarQube')
            booleanParam(name: 'RUN_CHECKMARX_SCAN', defaultValue: true, description: 'Scan code w/ Checkmarx')
            booleanParam(name: 'RUN_BLACKDUCK_SCAN', defaultValue: true, description: 'Scan code w/ Black Duck')
            booleanParam(name: 'RUN_MVN_TESTS', defaultValue: true, description: 'Run Maven Tests')

            booleanParam(name: 'RUN_DEPLOY', defaultValue: false, description: 'Deploy artifacts to XLD')
            //TODO: debugging nexus remove after complete
            booleanParam(name: 'UPLOAD_NEXUS', defaultValue: false, description: 'upload to nexus')

        }

        environment {
            APP_NAME = "${appName}"
            BUILD_VERSION = readMavenPom().getVersion()
            GROUP_ID = readMavenPom().getGroupId()
            ARTIFACT_ID = readMavenPom().getArtifactId()
            PACKAGING = readMavenPom().getPackaging()
            POM_NAME = readMavenPom().getName()
            XLD_APPLICATION_FOLDER = "${xldPackageIdSuffix}"
            CONTEXT_ROOT = "${contextRoot}"
            XLD_APP_VERSION = buildXldVersion(env.BUILD_VERSION, env.BUILD_NUMBER, env.BRANCH_NAME, params.RELEASE_BUILD)

           // XLD_ENV_PATH = "Environments/Launchpad/API/Non-Prod/${DEPLOYMENT_ENVIRONMENT}/${DEPLOYMENT_ENVIRONMENT}_API";

        }    
        stages {

            stage('Validation and Setup') {
                steps {
                    script {
                        validateTeamName(teamName)
                        xldEnvironmentId = determineXldEnvironmentId(teamName, params.DEPLOYMENT_ENVIRONMENT)
                        
                        // set the github owner (Github Organizations )
                        ghOwner = sh(script: "echo ${env.GIT_URL} | sed -E 's|.*/([^/]+)/[^/]+\$|\\1|'", returnStdout: true).trim()


                        // if the current branch is not the default branch do not deploy
                        if(env.BRANCH_NAME == "master" || env.BRANCH_NAME == "main"){
                            params.RUN_DEPLOY = true
                            echo "Is this the default integration branch? "
                        } 
                        // create a report.html file and archive 

                        // initialize
                        initializeCommitStatuses(params, ghOwner, env.APP_NAME)
                    }
                }

                post {
                    success {
                        echo "Validation stage - Successful"
                        addCommitStatus([ghOwner: ghOwner,
                            ghRepo: env.APP_NAME,
                            context: 'Stage/Validation',
                            state: 'success',
                            description: ' stage is complete'])
                    }
                    unsuccessful { 
                        echo "Validation stage - Failure"
                        addCommitStatus([ghOwner: ghOwner,
                            ghRepo: env.APP_NAME,
                            context: 'Stage/Validation',
                            state: 'failure',
                            description: ' stage failed'])
                    }
                }
            }

            stage('Build'){
                steps{
                    catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                        echo 'Building source...'

                        script {
                            configFileProvider([configFile(fileId: 'docet-settings', variable: 'MAVEN_SETTINGS')]) {
                                if(!params.BUILD_RELEASE){
                                    println("Building snapshot")
                                        sh "mvn package -q -U -X  -Dmaven.test.skip "
                                        sh 'ls -l target'
                                        //sh "mvn package -q -U -X -s ${MAVEN_SETTINGS} -Dmaven.test.skip "

                                        stash includes: '*', name: 'builtSources'
                                        // sh "mvn package -q -U -X -s ${MAVEN_SETTINGS} -Dmaven.test.skip -Djavax.xml.accessExternalSchema=all"
                                } else {
                                    env.version = pom.version.replaceAll("-SNAPSHOT", "")
                                    // withCredentials([usernamePassword(credentialsId: '2f4ddeb8-397a-4158-898b-65b140fb7a37', passwordVariable: 'SCM_PASSWORD', usernameVariable: 'SCM_USERNAME')]) {
                                    //     sh 'mvn -U -Dusername=${SCM_USERNAME} -Dpassword=${SCM_PASSWORD} -Dresume=false -DuseReleaseProfile=false release:prepare release:perform -DbuildNumber=$BUILD_NUMBER -DrevisionNumber=$SVN_REVISION -X'
                                    // }
                                }
                            }
                        }
                    }
                }

                post {
                    success { echo "Build stage - Successful"
                        addCommitStatus([ghOwner: ghOwner,
                        ghRepo: env.APP_NAME,
                        context: 'Stage/Build',
                        state: 'success',
                        description: ' stage is complete'])
                    }
                    unsuccessful { echo "Build stage - Failure"
                        addCommitStatus([ghOwner: ghOwner,
                            ghRepo: env.APP_NAME,
                            context: 'Stage/Build',
                            state: 'failure',
                            description: ' stage failed'])
                    }
                }   
            }


            stage('Run scans'){
                parallel{

                    stage('Run Maven Tests'){
                        when { expression { params.RUN_MVN_TESTS } }
                        steps {
                            catchError (buildResult: 'UNSTABLE', stageResult: 'FAILURE'){
                                sh 'mvn test -s ${MAVEN_SETTINGS}'
                            }
                        }
                        post {
                            success { echo "Maven Test stage - Successful"
                                addCommitStatus([ghOwner: ghOwner,
                                ghRepo: env.APP_NAME,
                                context: 'Stage/Maven Test',
                                state: 'success',
                                description: ' stage is complete'])
                            }
                            unsuccessful { echo "Maven Test stage - Failure"
                                addCommitStatus([ghOwner: ghOwner,
                                    ghRepo: env.APP_NAME,
                                    context: 'Stage/Maven Test',
                                    state: 'failure',
                                    description: ' stage failed'])
                            }
                        }
                    }

                    stage('Sonar Scan'){
                        when { expression { params.RUN_SONARQUBE_SCAN == true } }
                        steps {
                            catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                                sonarQubeScan()
                            }
                        }
                        post {
                            success { echo "SonarQube Scan stage - Successful"
                                addCommitStatus([ghOwner: ghOwner,
                                ghRepo: env.APP_NAME,
                                context: 'Stage/SonarQube Scan',
                                state: 'success',
                                description: ' stage is complete'])
                            }
                            unsuccessful { echo "SonarQube Scan stage - Failure"
                                addCommitStatus([ghOwner: ghOwner,
                                    ghRepo: env.APP_NAME,
                                    context: 'Stage/SonarQube Scan',
                                    state: 'failure',
                                    description: ' stage failed'])
                            }
                        }
                    }
                    
                    stage('Checkmarx Scan'){
                        when { expression { params.RUN_CHECKMARX_SCAN } }
                        
                        steps{
                            catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                                checkMarxScan(checkmarxProjectName: "${checkmarxProjectName}")
                            }
                            // emailext body: 'Please find attached the latest scan PDF report.',
                            //         attachmentsPattern: 'Checkmarx/Reports/**/*.pdf',
                            //         recipientProviders: [[$class: 'RequesterRecipientProvider']],
                            //         subject: "Checkmarx scan PDF report",
                            //         to: 'collin.stolpa@fisglobal.com'
                        }
                        post {
                            success { echo "Checkmarx Scan stage - Successful"
                                addCommitStatus([ghOwner: ghOwner,
                                ghRepo: env.APP_NAME,
                                context: 'Stage/Checkmarx Scan',
                                state: 'success',
                                description: ' stage is complete'])
                            }
                            unsuccessful { echo "Checkmarx Scan stage - Failure"
                                addCommitStatus([ghOwner: ghOwner,
                                    ghRepo: env.APP_NAME,
                                    context: 'Stage/Checkmarx Scan',
                                    state: 'failure',
                                    description: ' stage failed'])
                            }
                        }
                    }

                    stage('Blackduck Scan'){
                        
                        when{expression { params.RUN_BLACKDUCK_SCAN}}
                        
                        steps{
                            catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                                blackduckScan()
                            }
                        }
                        post {
                            success { echo "Blackduck Scan stage - Successful"
                                addCommitStatus([ghOwner: ghOwner,
                                ghRepo: env.APP_NAME,
                                context: 'Stage/Blackduck Scan',
                                state: 'success',
                                description: ' stage is complete'])
                                // sh """ echo '<font color="green">Successful Blackduck Stage</font></td></tr>' >> ${config.reportFile} """

                            }
                            unsuccessful { echo "Blackduck Scan stage - Failure"
                                addCommitStatus([ghOwner: ghOwner,
                                    ghRepo: env.APP_NAME,
                                    context: 'Stage/Blackduck Scan',
                                    state: 'failure',
                                    description: ' stage failed'])
                                    // sh """ echo '<font color="green">Failed Blackduck stage</font></td></tr>' >> ${config.reportFile} """

                            }
                        }

                    }

                }
            }
            
            stage('Publish to Nexus') {
                when { expression { params.UPLOAD_NEXUS }}
                steps  {
                    catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                        script { 
                            // publishToNexus()
                            echo "************************* Giving a 401 *********************************"
                                // sh 'mvn deploy -e -X -DskipTests -Dmaven.install.skip=true'
                            //withCredentials([usernamePassword(credentialsId: 'docat-us-migrator', passwordVariable: 'pass', usernameVariable: 'usr')]) {
                                // some block
                                configFileProvider([configFile(fileId: 'docet-settings', variable: 'MAVEN_SETTINGS')]) {

                                       // sh "mvn package -q -U -X  -Dmaven.test.skip "
                                        //sh "mvn package -q -U -X -s ${MAVEN_SETTINGS} -Dmaven.test.skip "
                                    sh " mvn deploy:deploy-file -s ${MAVEN_SETTINGS} -Durl=https://nexus.luigi.worldpay.io/nexus \
                                        -Dfile=target/${env.ARTIFACT_ID}-${env.BUILD_VERSION}.${env.PACKAGING} \
                                        -DgroupId=${env.GROUP_ID} \
                                        -DartifactId=${env.ARTIFACT_ID} \
                                        -Dpackaging=${env.PACKAGING} \
                                        -Dversion=${env.BUILD_VERSION} \
                                        -DrepositoryId=snapshots  \
                                        "

                                // }
                                }
                        }
                    }
                }
                post {
                    success { echo "Publish to Nexus stage - Successful"
                        addCommitStatus([ghOwner: ghOwner,
                        ghRepo: env.APP_NAME,
                        context: 'Stage/Publish to Nexus',
                        state: 'success',
                        description: ' stage is complete'])
                    }
                    unsuccessful { echo "Publish to Nexus stage - Failure"
                        addCommitStatus([ghOwner: ghOwner,
                            ghRepo: env.APP_NAME,
                            context: 'Stage/Publish to Nexus',
                            state: 'failure',
                            description: ' stage failed'])
                    }
                }
            }

            stage('Publish with Xl Deploy'){
                when{ expression{ params.RUN_DEPLOY }}
                steps { 
                    catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                        script { 
                            publishToXLD(appName, deployXmlPath)

                        }
                    }
                }
                post {
                    success { echo "Publish with XL Deploy stage - Successful"
                        addCommitStatus([ghOwner: ghOwner,
                        ghRepo: env.APP_NAME,
                        context: 'Stage/Publish with XL Deploy',
                        state: 'success',
                        description: ' stage is complete'])
                    }
                    unsuccessful { echo "Publish with XL Deploy stage - Failure"
                        addCommitStatus([ghOwner: ghOwner,
                            ghRepo: env.APP_NAME,
                            context: 'Stage/Publish with XL Deploy',
                            state: 'failure',
                            description: ' stage failed'])
                    }
                }
            }

            stage('Deploy to environment'){
                when{ expression{ params.RUN_DEPLOY }}
                steps {
                    catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                        script { 
                            retry(3) {

                                xldDeploy environmentId: "${xldEnvironmentId}",
                                    packageId: "Applications/${xldPackageIdSuffix}/${env.XLD_APP_VERSION}",
                                    serverCredentials: 'xld-uat'                             
                            }                    
                        }
                    }
                }

                post {
                    success { echo "Deploy to environment stage - Successful"
                        addCommitStatus([ghOwner: ghOwner,
                        ghRepo: env.APP_NAME,
                        context: 'Stage/Deploy to environment',
                        state: 'success',
                        description: ' stage is complete'])
                    }
                    unsuccessful { echo "Deploy to environment stage - Failure"
                        addCommitStatus([ghOwner: ghOwner,
                            ghRepo: env.APP_NAME,
                            context: 'Stage/Deploy to environment',
                            state: 'failure',
                            description: ' stage failed'])
                    }
                }

            }

        }
        post {
            //TODO: return results/files from stage(s) error(s)
            always {
                echo 'Build is el fin'
                //sendEmailNotification repo: repoName, branch: env.BRANCH_NAME, recipients: recipients, reportFile: REPORT_FILE
            }
            success { echo 'Build Succeeded!' }
            unstable { echo 'Build Unstable!' }
            unsuccessful { echo 'Build Failed!' }            
        }
        // TODO: for any branch that is not the main branch in the luigi-shared-library
        // The options directive is for configuration that applies to the whole job.
        options {
            // For example, we'd like to make sure we only keep 10 builds at a time, so
            // we don't fill up our storage!
            buildDiscarder(logRotator(numToKeepStr: '10'))

            // And we'd really like to be sure that this build doesn't hang forever, so
            // let's time it out after an hour.
            timeout(time: 25, unit: 'MINUTES')
        }


    }

}




static String determineXldEnvironmentId(String teamName, String environment) {

    if ('enterprise-api'.equals(teamName)) {

        switch (environment) {
            case 'none':
                return ''
            case 'dev':
                return 'Environments/docet/prod'
            case 'test':
                return 'Environments/docet/prod'
        }
    } else if ('frauddispute'.equals(teamName)) {

        switch (environment) {
            case 'none': 
                return ''
            case 'dev':
                return 'Environments/frauddispute/frauddispute-dev'
            case 'test':
                return 'Environments/frauddispute/frauddispute-test'
        }
    } else if ('launchpad'.equals(teamName)){
        //TODO: when we have configured the proper naming convention for XLD folder structure
        switch(environment){
            case 'none': 
                return ''
            case 'dev':
                return 'Environments/launchpad/api/dev'
            case 'test':
                return 'Environments/launchpad/api/test'
            case 'cert':
                return 'Environments/launchpad/api/cert'
        }
    } 

    error('Unable to determine XLD environment ID')
}


static String buildXldVersion(String buildVersion, String buildNumber, String branchName, boolean isReleaseBuild) {

    if (!isReleaseBuild) {

        return buildVersion + "-" + branchName + "-" + buildNumber + "-devbuild"

    } else {

        return buildVersion + "-" + branchName + "-" + buildNumber + "-release"
    }
}

static void validateTeamName(String teamName) {

    ArrayList<String> validTeamNames = ["enterprise-api", "frauddispute", "launchpad"]

    if (!validTeamNames.contains(teamName)) {

        error('Invalid teamName passed. Valid team names: ' + validTeamNames)
    }
}

