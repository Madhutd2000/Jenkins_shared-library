def call(Map config = [:]) {
    echo 'Qualys Security Scan...'
    sh "echo '<tr><td>Qualys Security Scan</td><td>' >> ${config.reportFile}"

    try {
        qualysWASScan apiPass: 'WAS$api123', apiServer: 'https://qualysapi.qualys.com',
                      apiUser: 'vantv_wa', authRecord: 'useDefault', authRecordId: '',
                      cancelHours: '1', cancelOptions: 'none', optionProfile: 'other',
                      optionProfileId: '54591', proxyPassword: '', proxyPort: 8080,
                      proxyServer: 'unetproxy.infoftps.com', proxyUsername: '',
                      scanName: '[test-${config.repo}-scan]_jenkins_build_[${config.tag}]',
                      scanType: 'VULNERABILITY', useProxy: true, webAppId: '223532647'
        echo 'Qualys Security Scan - Successful'
        sh """ echo '<font color="green">Successful</font></td></tr>' >> ${config.reportFile} """
    } catch (exception) {
        echo 'Qualys Security Scan - Failure'
        sh """ echo '<font color="red">Failed</font></td></tr>' >> ${config.reportFile} """
        error exception.toString()
    }
}
