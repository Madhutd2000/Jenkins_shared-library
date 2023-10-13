def call(Map config = [:]) {
    echo 'Git source code checkout...'
    sh "echo '<tr><td>Git checkout</td><td>' >> ${config.reportFile}"

    try {
        git branch: config.branch, credentialsId: config.credentials, url: config.url
        echo 'Git checkout - Successful'
        sh """ echo '<font color="green">Successful</font></td></tr>' >> ${config.reportFile} """
    } catch (exception) {
        echo 'Git checkout - Failure'
        sh """ echo '<font color="red">Failed</font></td></tr>' >> ${config.reportFile} """
        error exception.toString()
    }
}
