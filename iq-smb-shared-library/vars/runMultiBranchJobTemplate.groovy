def call(Map config = [:]) {
    def repoName = config.repoName
    def testWithDocker = config.testWithDocker
    def skipUnitTests = config.skipUnitTests
    def emailRecipientsList = config.emailRecipients
    def cx_project = config.cx_project
  
    def runCheckmarx = config.runCheckmarx
    echo 'runCheckmarx: ' + runCheckmarx
    echo 'config.runCheckmarx: ' + config.runCheckmarx
    if(runCheckmarx == null) {
    	runCheckmarx=false
    }
    
    def usesVoltage = config.usesVoltage
    echo 'usesVoltage: ' + usesVoltage
    echo 'config.usesVoltage: ' + config.usesVoltage
    if(usesVoltage == null) {
    	usesVoltage=false
    }
    
    def xldEnvironmentFolder = config.xldEnvironmentFolder
    if(xldEnvironmentFolder == null || xldEnvironmentFolder.trim().isEmpty()){
    	xldEnvironmentFolder='IQ-SMB/DEV/IQ-SMB_DEV'
    }
    
    def xldApplicationFolder = config.xldApplicationFolder
    if(xldApplicationFolder == null || xldApplicationFolder.trim().isEmpty()){
    	xldApplicationFolder='IQ-SMB/Dev/iq-smb-profile-ds-dev-master'
    }
    
    def appVersion = config.appVersion
    if(appVersion == null || appVersion.trim().isEmpty()){
    	appVersion=''
    }else {
    	appVersion = "-${appVersion}"
    	appVersionNoDash = config.appVersion
    }
        
    def runSonar = config.runSonar
    if(runSonar == null) {
    	runSonar=false
    }
    
    def publishToXld = config.publishToXld
    if(publishToXld == null) {
    	publishToXld=true
    }
    
    def deployToKubernetes = config.deployToKubernetes
    if(deployToKubernetes == null) {
    	deployToKubernetes=true
    }
    
    def enableDockerImage = config.enableDockerImage
    if(enableDockerImage == null) {
    	enableDockerImage=true
    }
    /* Change the gitSshCredentials, nexusRegistry, nexusRegistry */
    def gitSshCredentials = 'none'
    def cloneUrl = "git@github.worldpay.com:Worldpay/${repoName}.git"
    def nexusRegistry = "slflokydlnexs60.infoftps.com/iq-smb"
    def buildTag = 'slflokydlnexs60.infoftps.com/iq-smb' 
    def branchNamePlaceholder = ''
    def branchNameNoDashPlaceholder = ''
  
    if(!(env.BRANCH_NAME ==~ /^([0-9]+\.[0-9]+\.[0-9])/)) {        
    	branchNamePlaceholder = "-${env.BRANCH_NAME}"
    	branchNameNoDashPlaceholder = "${env.BRANCH_NAME}"  
     } 

    def xldPackageId = "${xldApplicationFolder}"
    		
    pipeline {
        agent {label 'puflopjenkap02'}

        parameters {
            string(description: 'List of Email Report Recipients', name: 'recipients', defaultValue: emailRecipientsList)
            string(description: 'Checkmarx Project name for this build', name: 'cx_project',  defaultValue: repoName)
            booleanParam(defaultValue: runCheckmarx, description: 'Run Checkmarx Scan?', name: 'runCheckmarxParam')
            booleanParam(defaultValue: runSonar, description: 'Run SonarQube Scan?', name: 'runSonar')
            booleanParam(defaultValue: publishToXld, description: 'Publish to XLD?', name: 'publishToXld')
            booleanParam(defaultValue: deployToKubernetes, description: 'Deploy to Kubernetes?', name: 'deployToKubernetes')
            booleanParam(defaultValue: enableDockerImage, description: 'Build Docker Image?', name: 'enableDockerImage')
        }

        tools {
            maven 'maven-3.5.2-pugrarjenkap01'
            jdk 'jdk-11.0.7'
        }

        environment {
            //MAVEN_OPTS = '--add-modules java.xml.bind -Xmx1024m -XX:MaxPermSize=512m -Djdk.tls.client.protocols=TLSv1.2 -Dhttps.protocols=TLSv1.2 -Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true'
            
            //REPORT_FILE= "${WORKSPACE}/index.html"
           MAVEN_OPTS = '-Xmx1024m -XX:MaxPermSize=512m -Djdk.tls.client.protocols=TLSv1.2 -Dhttps.protocols=TLSv1.2 -Dmaven.wagon.http.ssl.insecure=true -Dmaven.compiler.executable=/opt/jenkins/jdk-11.0.7 -Dmaven.wagon.http.ssl.allowall=true'
           report_file= "${WORKSPACE}/index.html"
           ts = new java.text.SimpleDateFormat('yyyyMMddHHmm').format(new Date())
            MDS_TEST_IMAGE_NAME="mds-microservice-test-${repoName}${branchNamePlaceholder}-image"
            MDS_TEST_CONTAINER_NAME="test-${repoName}${branchNamePlaceholder}"           
            BRANCH_NAME_PLACEHOLDER = "${branchNamePlaceholder}"
            BRANCH_NAME_NO_DASH_PLACEHOLDER = "${branchNameNoDashPlaceholder}"
            XLD_APPLICATION_FOLDER = "${xldApplicationFolder}"    
            APP_VERSION = "${appVersion}"
            APP_VERSION_NO_DASH = "${appVersionNoDash}"
            XLD_PACKAGE_ID = "${xldPackageId}"	
            REPO_NAME = "${repoName}"
        }

        stages {
            stage('Create report file') {
                steps {
                    createReportFile repo: repoName, branch: env.BRANCH_NAME                   
                }
            }
            
           stage('Checkmarx scan'){        	  
        	    when {
                    expression { params.runCheckmarxParam == true }                    
                }

                agent {
                    label "Checkmarx"
                }

                steps{
                	script{
                		  echo 'params.runCheckmarxParam=' + params.runCheckmarxParam
                	}
                    checkMarxScan()
                    emailext body: 'Please find attached the latest scan PDF report.', attachmentsPattern: 'Checkmarx/Reports/**/*.pdf',
             recipientProviders: [[$class: 'RequesterRecipientProvider']], subject: "CoreIQ Checkmarx scan PDF report", to: 'prashantika.k@fisglobal.com'
                 
                 //sendEmailNotification repo: repoName, recipients: recipients
                    deleteDir()
                }
            } 

           stage('Test with docker') {
                when {
                    expression { params.testWithDocker == true }
                }

                steps {
                    runTestsWithDocker image: MDS_TEST_IMAGE_NAME, container: MDS_TEST_CONTAINER_NAME, reportFile: REPORT_FILE
                }
            }

            stage ('Publish unit test results') {
            	when {
                    expression { params.testWithDocker == true }
                }
            	
                steps {
                    publishJUnitTestResults reportFile: REPORT_FILE
                }
            }
            
            stage('Build src') {
                steps {    
                	script{
                		 echo 'JAVA_HOME=' + env.JAVA_HOME 
                	}
                    runMavenBuild sonar: params.runSonar, skipTests: skipUnitTests, reportFile: REPORT_FILE
                }
            }

            stage('Build image') {
            	 when {
                     expression { params.enableDockerImage == true }
                 }
                steps {      
                	script {       
                 		 if(!(env.BRANCH_NAME ==~ /^([0-9]+\.[0-9]+\.[0-9])/)) {                   			
                 			 buildTag = "${env.BRANCH_NAME}-${env.BUILD_NUMBER}"                			
                          } else {
                         	 buildTag = "${env.BRANCH_NAME}-${GIT_COMMIT[0..7]}"                                           
                          }       		
                 		 echo 'Tagging ' + buildTag
                      }
                    buildDockerImage repo: repoName, registry: nexusRegistry, tag: buildTag, reportFile: REPORT_FILE, voltage: usesVoltage
                }
            }

            stage('Push image and update k8s resources yaml') {
            	 when {
                     expression { params.enableDockerImage == true }
                 }
            	 environment {
            		 BUILD_TAG = "${buildTag}"
                 }
                steps {
                    pushDockerImage repo: repoName, registry: nexusRegistry, tag: buildTag, reportFile: REPORT_FILE
                    resolveVariablesInFile filePath: 'resources.yaml', reportFile: REPORT_FILE
                    resolveVariablesInFile filePath: 'deployit-manifest.xml', reportFile: REPORT_FILE
                }
            }

            stage('Publish to XL Deploy') {
            	 when {
                     expression { params.publishToXld == true }
                 }
                steps {
                    deployToXLD repo: repoName, tag: buildTag, reportFile: REPORT_FILE
                }
            }

            stage('Deploy to Kubernetes') {
            	when {
                    expression { params.deployToKubernetes == true }
                }
                steps {
                    deployToKubernetesViaXLD environmentId: "${xldEnvironmentFolder}", packageId: "${xldPackageId}/${env.BUILD_NUMBER}", reportFile: REPORT_FILE
                }
            }


            /*stage('Qualys Security Scan') {
                steps {
                    qualysSecurityScan repo: repoName, tag: buildTag
                }
            }
            */
        }
        

        post {
            always {
                sendEmailNotification repo: repoName, branch: env.BRANCH_NAME, recipients: recipients, reportFile: REPORT_FILE
                deleteDir() /* Clean up workspace */
            }
        }
    }
}
