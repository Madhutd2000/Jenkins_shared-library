def call(Map config = [:]) {
    echo 'Deploying to Kubernetes...'
    sh "echo '<tr><td>Deploy to Kubernetes</td><td>' >> ${config.reportFile}"

    try {
        xldDeploy environmentId: "Environments/${config.environmentId}", packageId: "Applications/${config.packageId}", serverCredentials: "${config.serverCredentials}"
        echo 'Deploy to Kubernetes - Successful'
        sh """ echo '<font color="green">Successful</font></td></tr>' >> ${config.reportFile} """
    } catch (exception) {
        echo 'Deploy to Kubernetes - Failure'
        sh """ echo '<font color="red">Failed</font></td></tr>' >> ${config.reportFile} """
        error exception.toString()
    }
}
