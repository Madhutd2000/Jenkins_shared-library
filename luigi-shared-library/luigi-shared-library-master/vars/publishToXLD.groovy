def call(String appName, String deployXmlPath) {

    xldCreatePackage artifactsPath: '.', 
                        darPath: "${appName}-${env.XLD_APP_VERSION}.dar", 
                        manifestPath: "/${deployXmlPath}"
    xldPublishPackage darPath: "${appName}-${env.XLD_APP_VERSION}.dar", 
                        serverCredentials: "xld-uat"    
    
}