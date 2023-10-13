def call(Map config = [:]) {
    echo 'Building image...'
    sh "echo '<tr><td>Build image</td><td>' >> ${config.reportFile}"

    try {
    	if(config.voltage == true) {    		
    		sh "chmod +x mds-common-scripts/build_download_jar.sh && mds-common-scripts/build_download_jar.sh"
    		echo 'Downloaded Voltage JARs Successfully'
    	}
    	
        sh "docker build -t ${config.registry}/${config.repo}:${config.tag} ."
        echo 'Build image - Successful'
        sh """ echo '<font color="green">Successful</font></td></tr>' >> ${config.reportFile} """
    } catch (exception) {
        echo 'Build image - Failure'
        sh """ echo '<font color="red">Failed</font></td></tr>' >> ${config.reportFile} """
        error exception.toString()
    }
}
