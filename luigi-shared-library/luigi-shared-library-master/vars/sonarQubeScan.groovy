def call(Map config = [:]) {
    echo 'Building source...'
    //sh "echo '<tr><td>Build src</td><td>' >> ${config.reportFile}"

  //  try {

        withSonarQubeEnv('sonarqube') {
            configFileProvider([configFile(fileId: 'docet-settings', variable: 'MAVEN_SETTINGS')]) {
                sh "mvn -B -q -U -s ${MAVEN_SETTINGS} sonar:sonar -DskipTests -Dsonar.qualitygate.wait=true -Dsonar.qualitygate.timeout=30"
            }
        }

        //stash includes: 'target/*', name: 'builtSources'
        //sh """ echo '<font color="green">Successful</font></td></tr>' >> ${config.reportFile} """

    // } catch (exception) {

    //     echo 'Failure scanning'
    //     //sh """ echo '<font color="red">Failed</font></td></tr>' >> ${config.reportFile} """
    //     error(exception.toString())
    // }
}
