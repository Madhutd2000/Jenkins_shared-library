def call(Map config = [:]) {

    def appName = config.appName
    def teamName = config.teamName
    def deployXmlPath = config.deployXmlPath
    def checkmarxProjectName = config.checkmarxProjectName

    String xldEnvironmentId
    String buildNumb

    
    pipeline {

        agent {
            kubernetes{
                label 'launchpad-ui'
                defaultContainer 'ui'
                yaml libraryResource('agents/k8s/ui-nvm.yaml')

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
            // cannot invoke readJSON in the environment block only sh 
            BUILD_VERSION =  sh( script: """ node -p "require('./package.json').version"  """, returnStdout: true).trim()
            K8S_NAMESPACE = "${k8sNamespace}"
            XLD_APPLICATION_FOLDER = "${xldPackageIdSuffix}"
            RESOURCE_YAML_LOCATION = "${resourcesYamlPath}"
            //IMAGE_TAG = buildImageTag(imageTag, env.BRANCH_NAME, env.BUILD_VERSION, env.BUILD_NUMBER, params.RELEASE_BUILD)
            XLD_APP_VERSION = buildXldVersion(env.BUILD_VERSION, env.BUILD_NUMBER, env.BRANCH_NAME, params.RELEASE_BUILD)
            //TODO: if engine is not present fail build
            NODE_VERSION =  sh( script: """ node -p "require('./package.json').engines.node"  """, returnStdout: true).trim()
            //TODO: Once the mirror repository is created in nexus this can be removed
            NODE_DIR = '/home/node/nvm'
            NVM_NODEJS_ORG_MIRROR = 'https://nodejs.org/dist'
            FIS_ARTIFACTORY_TOKEN = ""

        }

        stages {

            stage('Validation and Setup') {
                steps {  
                    script {
                        println(env.BUILD_VERSION)
                        validateTeamName(teamName)
                        xldEnvironmentId = determineXldEnvironmentId(teamName, params.DEPLOYMENT_ENVIRONMENT)

                        sh '''
                            echo ". /home/node/nvm/nvm.sh" >> ~/.profile
                            . /home/node/nvm/nvm.sh
                            nvm install ${NODE_VERSION}
                            nvm use ${NODE_VERSION}
                            node -v
                        '''
                    }

                    // if the current branch is not the default branch do not deploy
                    if(env.BRANCH_NAME == "master" || env.BRANCH_NAME == "main"){
                        params.RUN_DEPLOY = true
                        echo "Is this the default integration branch? "
                    } 

                     // initialize
                    initializeCommitStatuses(params, ghOwner, env.APP_NAME)
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

            stage('Create report file') {
                steps {
                    echo "not creating report file"
                    //createReportFile repo: repoName, branch: env.BRANCH_NAME                   
                }
            }
            
            stage('Build Source') {
                steps{
                    script {
                       // if(!params.BUILD_RELEASE){
                        //TODO: handle None env
                        
                            withCredentials([string(credentialsId: 'SVC-docetartifactory-tkn', variable: 'token')]) {
                                env.FIS_ARTIFACTORY_TOKEN = "${token}"
                                println params.DEPLOYMENT_ENVIRONMENT
                                def deployEnv = params.DEPLOYMENT_ENVIRONMENT.toLowerCase().replace('\n', '')
                                println deployEnv
                                //TODO: handle npm ci with fail over, validate package-lock.json exists for npm ci
                                def packageLockExists = fileExists 'package-lock.json'
                                if(packageLockExists){
                                    sh """
                                        export FIS_ARTIFACTORY_TOKEN=${token}
                                        . /home/node/nvm/nvm.sh
                                        npm ci
                                    """
                                }else{
                                    sh """
                                        export FIS_ARTIFACTORY_TOKEN=${token}
                                        . /home/node/nvm/nvm.sh
                                        node -v
                                        npm i
                                        echo "is this deploying ${deployEnv}"
                                        npm run ng -- build --configuration=${deployEnv}
                                    """
                                }
                            }  

                            
                            // sh "npm ci"
                            // sh "npm run ng -- build --configuration=${params.DEPLOYMENT_ENVIRONMENT}"
                        //} 
                    }
                }
                post {
                    success { echo "Build Source stage - Successful"
                        addCommitStatus([ghOwner: ghOwner,
                        ghRepo: env.APP_NAME,
                        context: 'Stage/Build Source',
                        state: 'success',
                        description: 'Build stage is complete'])
                    }
                    unsuccessful { echo "Build Source stage - Failure"
                        addCommitStatus([ghOwner: ghOwner,
                            ghRepo: env.APP_NAME,
                            context: 'Stage/Build Source',
                            state: 'failure',
                            description: 'Build Source stage failed'])
                    }
                }
            }

            stage('Run scans'){
                parallel{

                    stage('Run npm audit'){
                        steps{
                            //TODO: set up connection to nexus for npm audit
                           // sh 'npm audit'
                        }
                        post {
                            success { echo "NPM audit stage - Successful"
                                addCommitStatus([ghOwner: ghOwner,
                                ghRepo: env.APP_NAME,
                                context: 'Stage/NPM audit',
                                state: 'success',
                                description: 'NPM audit stage is complete'])
                            }
                            unsuccessful { echo "NPM audit stage - Failure"
                                addCommitStatus([ghOwner: ghOwner,
                                    ghRepo: env.APP_NAME,
                                    context: 'Stage/NPM audit',
                                    state: 'failure',
                                    description: 'NPM audit stage failed'])
                            }
                        }
                    }

                    stage('Scan with SonarQube') {

                        when { expression { params.RUN_SONARQUBE_SCAN == true } }
                        
                        steps {
                            sonarQubeScan()
                        }

                        post {
                            success { echo "Sonar Scan stage - Successful"
                                addCommitStatus([ghOwner: ghOwner,
                                ghRepo: env.APP_NAME,
                                context: 'Stage/SonarQube Scan',
                                state: 'success',
                                description: 'SonarQube Scan stage is complete'])
                            }
                            unsuccessful { echo "SonarQube Scan stage - Failure"
                                addCommitStatus([ghOwner: ghOwner,
                                    ghRepo: env.APP_NAME,
                                    context: 'Stage/SonarQube Scan',
                                    state: 'failure',
                                    description: 'SonarQube Scan stage failed'])
                            }
                        }
                    }

                    stage('Checkmarx scan') {

                        when { expression { params.RUN_CHECKMARX_SCAN == true } }

                        steps {

                            checkMarxScan(checkmarxProjectName: "${checkmarxProjectName}")

                            emailext body: 'Please find attached the latest scan PDF report.',
                                    attachmentsPattern: 'Checkmarx/Reports/**/*.pdf',
                                    recipientProviders: [[$class: 'RequesterRecipientProvider']],
                                    subject: "Checkmarx scan PDF report",
                                    to: 'collin.stolpa@fisglobal.com'
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

                    stage("Black Duck Scan"){
                       
                        when{expression { params.RUN_BLACKDUCK_SCAN == true}}
                       
                        steps{
                            blackduckScan()
                        }
                    }

                }
            }


            //TODO: get to publish
            stage('Publish to Nexus') {
                when { expression { params.UPLOAD_NEXUS == true }}
                steps  {
                    script { 
                       // sh "npm publish ${appName}@-version-branch-buildNumber"
                    }
                }

                post {
                    success { echo "Publish to Nexus stage - Successful"
                        addCommitStatus([ghOwner: ghOwner,
                        ghRepo: env.APP_NAME,
                        context: 'Stage/Publish to Nexus',
                        state: 'success',
                        description: 'Publish to Nexus stage is complete'])
                    }
                    unsuccessful { echo "Publish to Nexus stage - Failure"
                        addCommitStatus([ghOwner: ghOwner,
                            ghRepo: env.APP_NAME,
                            context: 'Stage/Publish to Nexus',
                            state: 'failure',
                            description: 'Publish to Nexus stage failed'])
                    }
                }

            }

            stage('Publish with Xl Deploy'){
                when{ expression{ params.RUN_DEPLOY }}
                steps { 
                    script { 
                        publishToXLD(appName, deployXmlPath)
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

            stage('Deploy to environment'){
                when{ expression{ params.RUN_DEPLOY }}
                steps {
                    script { 
                        retry(3) {

                            xldDeploy environmentId: "${xldEnvironmentId}",
                                packageId: "Applications/${xldPackageIdSuffix}/${env.XLD_APP_VERSION}",
                                serverCredentials: 'xld-uat'                             
                        }                    
                    } 
                }

                post {
                    success { echo "Deploy to environment stage - Successful"
                        addCommitStatus([ghOwner: ghOwner,
                        ghRepo: env.APP_NAME,
                        context: 'Stage/Deploy to environment',
                        state: 'success',
                        description: 'Deploy to environment stage is complete'])
                    }
                    unsuccessful { echo "Deploy to environment stage - Failure"
                        addCommitStatus([ghOwner: ghOwner,
                            ghRepo: env.APP_NAME,
                            context: 'Stage/Deploy to environment',
                            state: 'failure',
                            description: 'Deploy to environment stage failed'])
                    }
                }

            }


            /*stage('Qualys Security Scan') {
                steps {
                    qualysSecurityScan repo: repoName, tag: buildTag
                }
            }*/
        }

        post {
            always {
                println("el fin")
                //sendEmailNotification repo: repoName, branch: env.BRANCH_NAME, recipients: recipients, reportFile: REPORT_FILE
            }
            //TODO: return results from stage(s) error(s)
            unsuccessful {
                println("Build Failed")
            }            
        }
    }
}

// Read package.json and return the version
static String getPackageVersion(){

        //return 

    // if(fileExists 'package.json'){
    // }
    // else{
    //     error("package.json doesn't exist.")
    // }

}

static String determineXldEnvironmentId(String teamName, String environment) {

 if ('launchpad'.equals(teamName)){
        //TODO: when we have configured the proper naming convention for XLD folder structure
        switch(environment){
            case 'None': 
                return ''
            case 'dev':
                return 'Environments/launchpad/ui/dev'
            case 'test':
                return 'Environments/launchpad/ui/test'
            case 'cert':
                return 'Environments/launchpad/ui/cert'
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

    ArrayList<String> validTeamNames = ["launchpad"]

    if (!validTeamNames.contains(teamName)) {

        error('Invalid teamName passed. Valid team names: ' + validTeamNames)
    }
}