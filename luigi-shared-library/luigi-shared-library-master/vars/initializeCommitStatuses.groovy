def call(def params, String ghOwner, String appName) {
// clear checks for stages that are not set to true
    params.each{ runStage ->

        // initilize the build stage 
        addCommitStatus([ghOwner: ghOwner,
            ghRepo: env.APP_NAME,
            context: 'Stage/Build',
            state: 'pending',
            description: ' is pending.'])

         if(runStage.key  == "RUN_DEPLOY" && runStage.value == false){
            addCommitStatus([ghOwner: ghOwner,
                ghRepo: appName,
                context: 'Stage/Publish with XL Deploy',
                state: 'error',
                description: ' was skipped by user.'])
            addCommitStatus([ghOwner: ghOwner,
                ghRepo: appName,
                context: 'Stage/Deploy to Kubernetes',
                state: 'pending',
                description: ' was skipped by user.'])

         }
        else if(runStage.key  == "RUN_DEPLOY" && runStage.value == true){
            addCommitStatus([ghOwner: ghOwner,
                ghRepo: appName,
                context: 'Stage/Publish with XL Deploy',
                state: 'pending',
                description: ' is queued.'])
            addCommitStatus([ghOwner: ghOwner,
                ghRepo: appName,
                context: 'Stage/Deploy to Kubernetes',
                state: 'pending',
                description: ' is queued.'])
        }

        if(runStage.key  == "RUN_SONARQUBE_SCAN" && runStage.value == false){
            addCommitStatus([ghOwner: ghOwner,
                ghRepo: appName,
                context: 'Stage/SonarQube Scan',
                state: 'error',
                description: ' was skipped by user.'])
        } else if(runStage.key  == "RUN_SONARQUBE_SCAN" && runStage.value == true){
            addCommitStatus([ghOwner: ghOwner,
                ghRepo: appName,
                context: 'Stage/SonarQube Scan',
                state: 'pending',
                description: ' was queued.'])
        }

        if(runStage.key  == 'RUN_CHECKMARX_SCAN' && runStage.value == false){
            addCommitStatus([ghOwner: ghOwner,
                ghRepo: appName,
                context: 'Stage/Checkmarx Scan',
                state: 'error',
                description: ' was skipped by user.'])
        }else if(runStage.key  == 'RUN_CHECKMARX_SCAN' && runStage.value == true){
            addCommitStatus([ghOwner: ghOwner,
                ghRepo: appName,
                context: 'Stage/Checkmarx Scan',
                state: 'pending',
                description: ' was queued.'])
        }

        if(runStage.key  == 'RUN_BLACKDUCK_SCAN' && runStage.value == false){
            addCommitStatus([ghOwner: ghOwner,
                ghRepo: appName,
                context: 'Stage/Blackduck Scan',
                state: 'error',
                description: ' was skipped by user.'])
        }else if(runStage.key  == 'RUN_BLACKDUCK_SCAN' && runStage.value == true){
            addCommitStatus([ghOwner: ghOwner,
                ghRepo: appName,
                context: 'Stage/Blackduck Scan',
                state: 'pending',
                description: ' was queued.'])
        }

        if(runStage.key  == 'RUN_MVN_TESTS' && runStage.value == false){
            addCommitStatus([ghOwner: ghOwner,
                ghRepo: appName,
                context: 'Stage/Maven Test',
                state: 'error',
                description: ' was skipped by user.'])
        }else if(runStage.key  == 'RUN_MVN_TESTS' && runStage.value == true){
            addCommitStatus([ghOwner: ghOwner,
                ghRepo: appName,
                context: 'Stage/Maven Test',
                state: 'pending',
                description: ' was queued.'])
        }

        if(runStage.key  == 'RUN_SYSDIG_SCAN' && runStage.value == false){
            addCommitStatus([ghOwner: ghOwner,
                ghRepo: appName,
                context: 'Stage/Sysdig Scan',
                state: 'error',
                description: ' was skipped by user.'])
        }else if(runStage.key  == 'RUN_SYSDIG_SCAN' && runStage.value == true){
            addCommitStatus([ghOwner: ghOwner,
                ghRepo: appName,
                context: 'Stage/Sysdig Scan',
                state: 'pending',
                description: ' was queued.'])
        }

        if(runStage.key  == 'RUN_DEPLOY' && runStage.value == false){
            addCommitStatus([ghOwner: ghOwner,
                ghRepo: appName,
                context: 'Stage/Deploy to Kubernetes',
                state: 'error',
                description: ' stage was skipped by user.'])
        }else if(runStage.key  == 'RUN_DEPLOY' && runStage.value == false){
            addCommitStatus([ghOwner: ghOwner,
                ghRepo: appName,
                context: 'Stage/Deploy to Kubernetes',
                state: 'error',
                description: ' was skipped by user.'])
        }
        
        if(runStage.key  == 'UPLOAD_NEXUS' && runStage.value == false){
            addCommitStatus([ghOwner: ghOwner,
                ghRepo: appName,
                context: 'Stage/Publish to Nexus',
                state: 'error',
                description: ' was skipped by user.'])
        }else if(runStage.key  == 'UPLOAD_NEXUS' && runStage.value == false){
            addCommitStatus([ghOwner: ghOwner,
                ghRepo: appName,
                context: 'Stage/Publish to Nexus',
                state: 'error',
                description: ' was skipped by user.'])
        }
        

    }

}