def call(Map config = [:]) {
    echo 'Building source...'
    sh "echo '<tr><td>Build src</td><td>' >> ${config.reportFile}"

    try {
    	
    	sh "chmod +x build.sh"
        sh "./build.sh"

        echo 'Build src - Successful'
        sh """ echo '<font color="green">Successful</font></td></tr>' >> ${config.reportFile} """
    } catch (exception) {
        echo 'Build src - Failure'
        sh """ echo '<font color="red">Failed</font></td></tr>' >> ${config.reportFile} """
        error exception.toString()
    }
}
