def call(Map config = [:]) {
    echo 'Pushing image...'
    sh "echo '<tr><td>Push image</td><td>' >> ${config.reportFile}"

    try {
        sh "docker push ${config.registry}/${config.repo}:${config.tag}"
        sh "docker rmi -f ${config.registry}/${config.repo}:${config.tag}"
        echo 'Push image - Successful'
        sh """ echo '<font color="green">Successful</font></td></tr>' >> ${config.reportFile} """
    } catch (exception) {
        echo 'Push image - Failure'
        sh """ echo '<font color="red">Failed</font></td></tr>' >> ${config.reportFile} """
        error exception.toString()
    }
}
