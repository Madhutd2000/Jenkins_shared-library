def call(Map config = [:]) {
    echo 'Updating ${config.filePath}...'
    sh "echo '<tr><td>Update ${config.filePath}</td><td>' >> ${config.reportFile}"

    try {
        sh "envsubst < ${config.filePath} > temp"
        sh "rm ${config.filePath} && mv temp ${config.filePath}"
        echo "Update ${config.filePath} - Successful"
        sh """ echo '<font color="green">Successful</font></td></tr>' >> ${config.reportFile} """
    } catch (exception) {
        echo "Update ${config.filePath} - Failure"
        sh """ echo '<font color="red">Failed</font></td></tr>' >> ${config.reportFile} """
        error exception.toString()
    }
}
