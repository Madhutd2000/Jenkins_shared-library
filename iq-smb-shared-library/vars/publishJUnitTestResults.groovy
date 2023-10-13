def call(Map config = [:]) {
    echo 'Publishing unit test results..'
    sh "echo '<tr><td>Publishing unit test results</td><td>' >> ${config.reportFile}"

    try {
        junit 'target/surefire-reports/*.xml'
        echo 'Publishing Unit Test Results - Successful'
        sh """ echo '<font color="green">Successful</font></td></tr>' >> ${config.reportFile} """
    } catch (ignored) {
        echo 'Publishing Unit Test Results - Skipped'
        sh """ echo '<font color="yellow">Skipped</font></td></tr>' >> ${config.reportFile} """
    }
}
