def call(Map config = [:]) {
    echo 'Building source...'
    sh "echo '<tr><td>Build src</td><td>' >> ${config.reportFile}"

    try {
        if(config.sonar) {
            withSonarQubeEnv('SonarQube') {
                sh "mvn -B -U clean deploy sonar:sonar -DskipTests=${config.skipTests}"
            }
        } else {
        	echo 'JAVA_HOME=' + env.JAVA_HOME 
            sh "mvn -B -U clean deploy -DskipTests=${config.skipTests}"
        }

        echo 'Build src - Successful'
        sh """ echo '<font color="green">Successful</font></td></tr>' >> ${config.reportFile} """
    } catch (exception) {
        echo 'Build src - Failure'
        sh """ echo '<font color="red">Failed</font></td></tr>' >> ${config.reportFile} """
        error exception.toString()
    }
}
