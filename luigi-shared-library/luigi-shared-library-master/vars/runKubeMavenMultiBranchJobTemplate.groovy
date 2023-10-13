def call(Map config = [:]) {

    String teamName = config.teamName
    String appName = config.appName
    String k8sNamespace = config.k8sNamespace
    String deployXmlPath = config.deployXmlPath
    String resourcesYamlPath = config.resourcesYamlPath
    String xldPackageIdSuffix = config.xldPackageIdSuffix
    String imageTag = config.imageTag
    String checkmarxProjectName = config.checkmarxProjectName

    String xldEnvironmentId
    String kpackNamespace



    pipeline {
        agent {
            kubernetes {
                label 'kpack'
                defaultContainer 'kpack'
                yaml libraryResource('agents/k8s/kpack.yaml')
            }
        }

        parameters {

            choice(name: 'DEPLOYMENT_ENVIRONMENT',
                    choices: "dev\n" +
                            "local\n" +
                            "test\n" +
                            "load\n" +
                            "stage\n" +
                            "cert\n",
                    description: 'Which environment to deploy to?')
            booleanParam(name: 'RUN_SONARQUBE_SCAN', defaultValue: true, description: 'Scan code w/ SonarQube')
            booleanParam(name: 'RUN_CHECKMARX_SCAN', defaultValue: true, description: 'Scan code w/ Checkmarx')
            booleanParam(name: 'RUN_BLACKDUCK_SCAN', defaultValue: true, description: 'Scan code w/ Black Duck')
            booleanParam(name: 'RUN_MVN_TESTS', defaultValue: true, description: 'Run Maven Tests')
            booleanParam(name: 'RUN_SYSDIG_SCAN', defaultValue: true, description: 'Scan image w/ Sysdig')
            booleanParam(name: 'RUN_DEPLOY', defaultValue: false, description: 'Deploy OCI Image')
            booleanParam(name: 'RELEASE_BUILD', defaultValue: false, description: 'Whether to add the POM version label to the image tag')
        }

        environment {

            APP_NAME = "${appName}"
            BUILD_VERSION = readMavenPom().getVersion()
            K8S_NAMESPACE = "${k8sNamespace}"
            XLD_APPLICATION_FOLDER = "${xldPackageIdSuffix}"
            RESOURCE_YAML_LOCATION = "${resourcesYamlPath}"
            IMAGE_TAG = buildImageTag(imageTag, env.BRANCH_NAME, env.BUILD_VERSION, env.BUILD_NUMBER, params.RELEASE_BUILD)
            XLD_APP_VERSION = buildXldVersion(env.BUILD_VERSION, env.BUILD_NUMBER, env.BRANCH_NAME, params.RELEASE_BUILD)
        }

        stages {

            stage('Validation and Setup') {

                steps {
                    catchError {
                        script {

                            validateTeamName(teamName)
                            xldEnvironmentId = determineXldEnvironmentId(teamName, params.DEPLOYMENT_ENVIRONMENT)
                            kpackNamespace = determineKpackNamespace(teamName)
                            
                            //set the github owner (Github Organizations )
                            ghOwner = sh(script: "echo ${env.GIT_URL} | sed -E 's|.*/([^/]+)/[^/]+\$|\\1|'", returnStdout: true).trim()

                            
                            // if the current branch is not the default branch do not deploy
                            if(env.BRANCH_NAME == "master" || env.BRANCH_NAME == "main"){
                                params.RUN_DEPLOY = true
                                echo "Is this the default integration branch? "
                            }

                            // initialize
                            initializeCommitStatuses(params, ghOwner, env.APP_NAME)

                        }
                    }
                }

                post {
                    success { echo "Validation stage - Successful"
                        addCommitStatus([ghOwner: ghOwner,
                            ghRepo: env.APP_NAME,
                            context: 'Stage/Validation & Setup',
                            state: 'success',
                            description: 'Validation stage is complete'])
                    }
                    unsuccessful { echo "Validation stage - Failure"
                        addCommitStatus([ghOwner: ghOwner,
                            ghRepo: env.APP_NAME,
                            context: 'Stage/Validation & Setup',
                            state: 'failure',
                            description: 'Validation stage failed'])
                    }
                }

            }

            stage('Build Source') {

                steps {
                    catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
                        echo 'Building source...'

                        script {
                            configFileProvider([configFile(fileId: 'docet-settings', variable: 'MAVEN_SETTINGS')]) {
                                if(!params.BUILD_RELEASE){
                                    println("Building snapshot")

                                    sh "mvn -B -q package -s ${MAVEN_SETTINGS} -Dmaven.test.skip"

                                    stash includes: '*', name: 'builtSources'
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
                            description: 'Build stage is complete'])

                    }
                    unsuccessful { echo "Build stage - Failure"
                        addCommitStatus([ghOwner: ghOwner,
                            ghRepo: env.APP_NAME,
                            context: 'Stage/Build',
                            state: 'failure',
                            description: 'Build stage failed'])
                    }
                }
            }


            stage('Run scans'){
                parallel{

                    stage('Run Maven Tests'){
                        when { expression { params.RUN_MVN_TESTS } }
                        steps {
                            catchError (buildResult: 'UNSTABLE', stageResult: 'FAILURE'){
                                configFileProvider([configFile(fileId: 'docet-settings', variable: 'MAVEN_SETTINGS')]) {
                                    sh 'mvn test -s ${MAVEN_SETTINGS}' 
                                }
                            }
                        }
                        post {
                            success { echo "Maven Test stage - Successful"
                                addCommitStatus([ghOwner: ghOwner,
                                ghRepo: env.APP_NAME,
                                context: 'Stage/Maven Test',
                                state: 'success',
                                description: 'Maven Test stage is complete'])
                            }
                            unsuccessful { echo "Maven Test stage - Failure"
                                addCommitStatus([ghOwner: ghOwner,
                                    ghRepo: env.APP_NAME,
                                    context: 'Stage/Maven Test',
                                    state: 'failure',
                                    description: 'Maven Test stage failed'])
                            }
                        }
                    }

                    stage('Scan with SonarQube') {

                        when { expression { params.RUN_SONARQUBE_SCAN } }

                        steps {
                            catchError (buildResult: 'UNSTABLE', stageResult: 'FAILURE'){
                                sonarQubeScan()
                            }
                        }

                        post {
                            success { echo "Sonar ScanQube stage - Successful"
                                addCommitStatus([ghOwner: ghOwner,
                                ghRepo: env.APP_NAME,
                                context: 'Stage/SonarQube Scan',
                                state: 'success',
                                description: 'SonarQube Scan stage is complete'])
                            }
                            unsuccessful { echo "Sonar Scan stage - Failure"
                                addCommitStatus([ghOwner: ghOwner,
                                    ghRepo: env.APP_NAME,
                                    context: 'Stage/SonarQube Scan',
                                    state: 'failure',
                                    description: 'SonarQube Scan stage failed'])
                            }
                        }
                    }

                    stage('Checkmarx scan') {

                        when { expression { params.RUN_CHECKMARX_SCAN } }

                        steps {
                            catchError (buildResult: 'UNSTABLE', stageResult: 'FAILURE'){

                                checkMarxScan(checkmarxProjectName: "${checkmarxProjectName}")

                                // emailext body: 'Please find attached the latest scan PDF report.',
                                //         attachmentsPattern: 'Checkmarx/Reports/**/*.pdf',
                                //         recipientProviders: [[$class: 'RequesterRecipientProvider']],
                                //         subject: "Checkmarx scan PDF report",
                                //         to: 'collin.stolpa@fisglobal.com'
                            }

                        }

                        post {
                            success { echo "Checkmarx Scan stage - Successful"
                                addCommitStatus([ghOwner: ghOwner,
                                ghRepo: env.APP_NAME,
                                context: 'Stage/Checkmarx Scan',
                                state: 'success',
                                description: 'Checkmarx Scan stage is complete'])
                            }
                            unsuccessful { echo "Checkmarx Scan stage - Failure"
                                addCommitStatus([ghOwner: ghOwner,
                                    ghRepo: env.APP_NAME,
                                    context: 'Stage/Checkmarx Scan',
                                    state: 'failure',
                                    description: 'Checkmarx Scan stage failed'])
                            }
                        }
                    }

                    //TODO: add blackduck scan
                }
            }

            stage('Build Image with kpack') {

                when { expression { params.RUN_DEPLOY || params.RUN_SYSDIG_SCAN } }

                steps {
                    catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                        script{
                            buildKpackImage(kpackNamespace, appName, imageTag)
                        }
                    }
                }

                post {
                    success { echo "Build Image with kpack stage - Successful"
                        addCommitStatus([ghOwner: ghOwner,
                            ghRepo: env.APP_NAME,
                            context: 'Stage/Build Image with kpack',
                            state: 'success',
                            description: 'Build Image with kpack stage is complete'])
                    }
                    unsuccessful { echo "Build Image with kpack stage - Failure"
                        addCommitStatus([ghOwner: ghOwner,
                            ghRepo: env.APP_NAME,
                            context: 'Stage/Build Image with kpack',
                            state: 'failure',
                            description: 'Build Image with kpack stage failed'])
                    }
                }
            }

            stage('Scan image w/ Sysdig') {

                when { expression { params.RUN_SYSDIG_SCAN } }

                steps {
                    catchError {

                        container('kpack') {

                            script {

                                withCredentials([aws(credentialsId: 'svc-ecr-repo-prod-user',
                                        accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                                        secretKeyVariable: 'AWS_SECRET_ACCESS_KEY')]) {

                                    env.AWS_ECR_PASSWORD = sh(
                                            script: "aws ecr get-login-password --region us-east-2",
                                            returnStdout: true
                                    ).trim()
                                }
                            }
                        }
                    
                        container('sysdig') {

                            withCredentials([string(credentialsId: 'sysdig-token', variable: 'SYSDIG_TOKEN')]) {

                                wrap([$class          : 'MaskPasswordsBuildWrapper',
                                    varPasswordPairs: [[password: "${env.AWS_ECR_PASSWORD}"]]]) {

                                    //TODO: -r will return the results pdf, we have to figure out what to do with it
                                    sh """/sysdig-inline-scan.sh \
                                        --sysdig-token ${SYSDIG_TOKEN} \
                                        --registry-auth-basic AWS:${env.AWS_ECR_PASSWORD} \
                                        ${env.IMAGE_TAG}
                                        """
                                }
                            }
                        }
                    
                    }

                }
                post {
                    success { echo "Scan Image with Sysdig - Successful"
                        addCommitStatus([ghOwner: ghOwner,
                        ghRepo: env.APP_NAME,
                        context: 'Stage/Sysdig Scan',
                        state: 'success',
                        description: 'Scan Image with Sysdig is complete'])
                    }
                    unsuccessful { echo "Scan Image with Sysdig - Failure"
                        addCommitStatus([ghOwner: ghOwner,
                            ghRepo: env.APP_NAME,
                            context: 'Stage/Sysdig Scan',
                            state: 'failure',
                            description: 'Scan Image with Sysdig failed'])
                    }
                }
            }


            stage('Publish to XL Deploy') {
                when { expression { params.RUN_DEPLOY } }

                steps {
                    catchError {

                        script {

                            sh "echo Publish to XL Deploy"
                            sh "echo BRANCH_NAME is: ${env.BRANCH_NAME}"

                            sh "envsubst < ${resourcesYamlPath} > temp"
                            sh "cat temp"
                            sh "rm ${resourcesYamlPath} && mv temp ${resourcesYamlPath}"

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
                        description: 'Publish with XL Deploy stage is complete'])
                    }
                    unsuccessful { echo "Publish with XL Deploy stage - Failure"
                        addCommitStatus([ghOwner: ghOwner,
                            ghRepo: env.APP_NAME,
                            context: 'Stage/Publish with XL Deploy',
                            state: 'failure',
                            description: 'Publish with XL Deploy stage failed'])
                    }
                }
            }

            stage('Deploy to Kubernetes') {

                when { expression { params.RUN_DEPLOY } }

                steps {
                    catchError {

                        script {

                            xldDeploy environmentId: "${xldEnvironmentId}",
                                    packageId: "Applications/${xldPackageIdSuffix}/${env.XLD_APP_VERSION}",
                                    serverCredentials: 'xld-uat'
                        }
                    }
                }

                post {
                    success { echo "Deploy to Kubernetes stage - Successful"
                        addCommitStatus([ghOwner: ghOwner,
                        ghRepo: env.APP_NAME,
                        context: 'Stage/Deploy to Kubernetes',
                        state: 'success',
                        description: 'Deploy to Kubernetes stage is complete'])
                    }
                    unsuccessful { echo "Deploy to Kubernetes stage - Failure"
                        addCommitStatus([ghOwner: ghOwner,
                            ghRepo: env.APP_NAME,
                            context: 'Stage/Deploy to Kubernetes',
                            state: 'failure',
                            description: 'Deploy to Kubernetes stage failed'])
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
    }
}


static String buildImageTag(String imageTag, String branchName, String buildVersion, String buildNumber, boolean isReleaseBuild) {

    if (!isReleaseBuild) {

        return imageTag + ":" + buildVersion + "-" + branchName + "-" + buildNumber + "-devbuild"

    } else {

        return imageTag + ":" + buildVersion + "-" + branchName + "-" + buildNumber + "-release"
    }
}

static String buildXldVersion(String buildVersion, String buildNumber, String branchName, boolean isReleaseBuild) {

    if (!isReleaseBuild) {

        return buildVersion + "-" + branchName + "-" + buildNumber + "-devbuild"

    } else {

        return buildVersion + "-" + branchName + "-" + buildNumber + "-release"
    }
}

static void validateTeamName(String teamName) {

    ArrayList<String> validTeamNames = ["enterprise-api", "frauddispute"]

    if (!validTeamNames.contains(teamName)) {

        error('Invalid teamName passed. Valid team names: ' + validTeamNames)
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
    }

    error('Unable to determine XLD environment ID')
}

static String determineKpackNamespace(String teamName) {

    if ('enterprise-api'.equals(teamName)) {

        return 'kpack-fraudsight'

    } else if ('frauddispute'.equals(teamName)) {

        return 'kpack-fraudsight'
    }

    error('Unable to determine kpack namespace')
}
