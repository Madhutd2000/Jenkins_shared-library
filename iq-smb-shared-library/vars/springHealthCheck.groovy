def call(Map config = [:]) {
    echo 'Checking micorservice health...'
    sh "echo '<tr><td>Health Check</td><td>' >> ${config.reportFile}"

    try {
        sleep 30
        def maxTries = 2;

        for(def count = 0; count <= maxTries; count++) {
            try {
                timeout(time: 5, unit: 'SECONDS') {
                    def response = sh(returnStdout: true, script: "curl -k ${config.url}").toString().trim()
                    echo "Response: ${response}"
                    if ("${response}" == '{"status":"UP"}') {
                        echo 'Health check has passed'
                    }
                }

                return
            } catch (ignored) {
                echo "Health check URL is not up yet..." 
                if (count == maxTries) {
                    error 'Failing build because health check has failed three times...'
                }
            }
        }

        echo 'Health Check - Successful'
        sh """ echo '<font color="green">Successful</font></td></tr>' >> ${config.reportFile} """
    } catch (exception) {
        echo 'Health Check - Failure'
        sh """ echo '<font color="red">Failed</font></td></tr>' >> ${config.reportFile} """
        error exception.toString()
    }
}
