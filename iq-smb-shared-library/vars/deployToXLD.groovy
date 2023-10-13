def call(Map config = [:]) {
    echo 'Publishing to XL Deploy...'
    sh "echo '<tr><td>Publish to XL Deploy</td><td>' >> ${config.reportFile}"

    try {
        xldCreatePackage  artifactsPath: '.', darPath: "spring-${config.repo}-${config.tag}.dar", manifestPath: 'deployit-manifest.xml'
        xldPublishPackage darPath: "spring-${config.repo}-${config.tag}.dar", serverCredentials: 'test-xl-deploy'
        echo 'Publish to XL Deploy - Successful'
        sh """ echo '<font color="green">Successful</font></td></tr>' >> ${config.reportFile} """
    } catch (exception) {
        echo 'Publish to XL Deploy - Failure'
        sh """ echo '<font color="red">Failed</font></td></tr>' >> ${config.reportFile} """
        error exception.toString()
    }
}
