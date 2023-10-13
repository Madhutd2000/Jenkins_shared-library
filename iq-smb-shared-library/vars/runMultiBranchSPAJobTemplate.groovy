def call(Map config = [:]) {
    def repoName = config.repoName
    def testWithDocker = config.testWithDocker
    def skipUnitTests = config.skipUnitTests
    def runSonar = config.runSonar
    def emailRecipientsList = config.emailRecipients
    def xldEnvironmentFolder = config.xldEnvironmentFolder
    def cx_project = config.cx_project
    
    def runCheckmarx = config.runCheckmarx
    echo 'runCheckmarx: ' + runCheckmarx
    echo 'config.runCheckmarx: ' + config.runCheckmarx
    if(runCheckmarx == null) {
    	runCheckmarx=false
    }
    
    if(xldEnvironmentFolder == null || xldEnvironmentFolder.trim().isEmpty()){
    	xldEnvironmentFolder='MDS/MDS_DEV/MDS-DEV'
    }
    
    def xldApplicationFolder = config.xldApplicationFolder
    if(xldApplicationFolder == null || xldApplicationFolder.trim().isEmpty()){
    	xldApplicationFolder='MDS_POC'
    }

    def gitSshCredentials = 'jenkadm-github-test'
    def cloneUrl = "git@github.worldpay.com:Worldpay/${repoName}.git"
    def nexusRegistry = "slflokydlnexs60.infoftps.com:9876/mdsui"
    def buildTag = ''
    def branchNamePlaceholder = ''
    def branchNameNoDashPlaceholder = ''
    def branchNameWithForwardSlashPlaceholder = ''
    
    if(!(env.BRANCH_NAME ==~ /^([0-9]+\.[0-9]+\.[0-9])/)) {        
        branchNamePlaceholder = "-${env.BRANCH_NAME}"
        branchNameWithForwardSlashPlaceholder = "/${env.BRANCH_NAME}"	
        branchNameNoDashPlaceholder = "${env.BRANCH_NAME}"
    }
    
    pipeline {
    agent {label 'puflopjenkap02'}

    parameters {
        string(description: 'List of Email Report Recipients', name: 'recipients', defaultValue: emailRecipientsList)
        //booleanParam(defaultValue: false, description: 'Run Checkmarx Scan?', name: 'runCheckmarx')
        booleanParam(defaultValue: true, description: 'Publish to XLD?', name: 'publishToXld')
        booleanParam(defaultValue: true, description: 'Deploy to Kubernetes?', name: 'deployToKubernetes')
       }

    tools
    {
        jdk 'jdk-9.0.4'
    }

    environment {
  	   ts = new java.text.SimpleDateFormat('yyyyMMddHHmm').format(new Date())
          REPORT_FILE= "${WORKSPACE}/index.html"
          MDS_TEST_IMAGE_NAME="mds-microservice-test-${repoName}${branchNamePlaceholder}-image"
          MDS_TEST_CONTAINER_NAME="test-${repoName}${branchNamePlaceholder}"
          BRANCH_NAME_PLACEHOLDER = "${branchNamePlaceholder}"
          BRANCH_NAME_WITH_FORWARD_SLASH_PLACEHOLDER = "${branchNameWithForwardSlashPlaceholder}"
          BRANCH_NAME_NO_DASH_PLACEHOLDER = "${branchNameNoDashPlaceholder}"
          XLD_APPLICATION_FOLDER = "${xldApplicationFolder}"    
    }
    stages {
            stage('Create report file') {
                steps {
                    createReportFile repo: repoName, branch: env.BRANCH_NAME
                }
            }
      /*      stage('Checkmarx scan'){
                when {
                    expression { params.runCheckmarx == true }
                }

                agent {
                    label "STFLOKYDLQCTR01"
                }

                steps{
                 checkMarxScan()
                }
            } 
*/
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
             recipientProviders: [[$class: 'RequesterRecipientProvider']], subject: "Hubinators Checkmarx scan PDF report", to: 'shashank.sirvastava@fisglobal.com, shobana.nambiar@fisglobal.com, wendell.payne@fisglobal.com, james.poeppelman@fisglobal.com, vaibhav.deshpande@fisglobal.com'
                 //sendEmailNotification repo: repoName, recipients: recipients
                    deleteDir()
                }
            }
        
            stage('Test with docker') {
                when {
                    expression { testWithDocker == true }
                }

                steps {
                    runTestsWithDocker image: MDS_TEST_IMAGE_NAME, container: MDS_TEST_CONTAINER_NAME, reportFile: REPORT_FILE
                }
            }

//            stage ('Publish unit test results') {
//                steps {
//                    publishJUnitTestResults reportFile: REPORT_FILE
//                }
//            }

            stage('Build src') {
                steps {
                	resolveVariablesInFile filePath: 'build.sh', reportFile: REPORT_FILE
                    runBuild reportFile: REPORT_FILE
                }
            }

            stage('Build image') {
                steps {
                	script {       
                		 if(!(env.BRANCH_NAME ==~ /^([0-9]+\.[0-9]+\.[0-9])/)) {                   			
                			 buildTag = "${env.BRANCH_NAME}-${env.BUILD_NUMBER}"                			
                         } else {
                        	 buildTag = "${env.BRANCH_NAME}-${GIT_COMMIT[0..7]}"                                           
                         }       		
                		 echo 'Tagging ' + buildTag
                     }
                    buildDockerImage repo: repoName, registry: nexusRegistry, tag: buildTag, reportFile: REPORT_FILE
                }
            }

            stage('Push image and update k8s resources yaml') {
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
                   deployToKubernetesViaXLD environmentId: "${xldEnvironmentFolder}", packageId: "${xldApplicationFolder}/${repoName}${branchNamePlaceholder}/${buildTag}"
               }
           }


//            stage('Health Check') {
//                steps {
//                    springHealthCheck url: params.healthCheckUrl, reportFile: REPORT_FILE
//                }
//            }

            /*stage('Qualys Security Scan') {
                steps {
                    qualysSecurityScan repo: repoName, tag: buildTag
                }
            }*/
        }

        post {
            always {
                sendEmailNotification repo: repoName, branch: env.BRANCH_NAME, recipients: params.recipients, reportFile: REPORT_FILE
                deleteDir() /* Clean up workspace */
            }
        }
    }
}
