def call(Map config = [:]) {
    def result = 'PASSED'

    if(currentBuild.result == 'FAILURE' || currentBuild.result == 'Failure' || currentBuild.result == 'failed' || currentBuild.result == 'Still Failing') {
        result = 'FAILED'
    }

    sh "echo '</table>    </body>    </html>' >> ${config.reportFile}"
    echo "BUILD ${result}"
    emailext attachLog: true, body: '${FILE, path="index.html"}',
             recipientProviders: [[$class: 'RequesterRecipientProvider']],
             subject: "${config.repo} - Branch ${config.branch} - Build # ${env.BUILD_NUMBER} has ${result}", to: config.recipients
}
