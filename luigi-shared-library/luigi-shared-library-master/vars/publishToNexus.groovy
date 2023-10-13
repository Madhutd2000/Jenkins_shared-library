def call(Map config = [:]) {

        nexusArtifactUploader artifacts:
                [[artifactId: "${env.ARTIFACT_ID}",
                        classifier: '', 
                        file: "target/${env.ARTIFACT_ID}-${env.BUILD_VERSION}.${env.PACKAGING}",
                        type: "${env.PACKAGING}"]], 
                credentialsId: 'docat-us-migrator', 
                groupId: "${env.GROUP_ID}", 
                nexusUrl: 'nexus.luigi.worldpay.io/nexus', 
                nexusVersion: 'nexus3', 
                protocol: 'https', 
                repository: 'snapshots', 
                version: "${env.BUILD_VERSION}"

        // If its a maven project
        // if(pomfiel exists){
        //         nexusArtifactUploader{
        //                 artifacts{
        //                 artifactId( "${env.ARTIFACT_ID}")
        //                         classifier('')
        //                         file("target/${env.ARTIFACT_ID}-${env.BUILD_VERSION}.${env.PACKAGING}")
        //                         type("${env.PACKAGING}")
        //                 }
        //                 credentialsId('Nexus-Prod')
        //                 groupId("${env.GROUP_ID}")
        //                 nexusUrl("nexus.luigi.worldpay.io/nexus")
        //                 nexusVersion('nexus3')
        //                 protocol('https')
        //                 repository("snapshots")
        //                 version("${env.BUILD_VERSION}")
        //         }

        // }

        // If its a node project
        // if(paga.json exists){
        //         nexusArtifactUploader{
        //                 artifacts{
        //                 artifactId( "${env.ARTIFACT_ID}")
        //                         classifier('')
        //                         file("target/${env.ARTIFACT_ID}-${env.BUILD_VERSION}.${env.PACKAGING}")
        //                         type("${env.PACKAGING}")
        //                 }
        //                 credentialsId('Nexus-Prod')
        //                 groupId("${env.GROUP_ID}")
        //                 nexusUrl("nexus.luigi.worldpay.io/nexus")
        //                 nexusVersion('nexus3')
        //                 protocol('https')
        //                 repository("snapshots")
        //                 version("${env.BUILD_VERSION}")
        //         }
        // }

}