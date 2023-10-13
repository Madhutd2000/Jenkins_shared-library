def call(Map config = [:]) {
    echo 'Test with docker...'
    sh "echo '<tr><td>Test with docker</td><td>' >> ${config.reportFile}"

    try {
        /* Removing untagged images, as test image is always ${config.image}:latest */
        sh 'docker images -q --filter dangling=true | xargs docker rmi >/dev/null 2>&1 &'

        /* Listing if any containers are still running, as if this job is terminated manually
         * container keeps on running and it should be terminated before next build is run
         * by following commands
         */
        sh "docker stop ${config.container} || true"
        sh 'docker ps'

        /* Removing old log files, logs are already preserved in Jenkins */
        sh 'rm *.log* || true'
        sh 'ls -al'

        /* Building docker image with the user and uid as arguments. These arguments are required
         * as we're mounting .m2 and project directory in 'docker run' command which should have the
         * same permissions as in host file system after in containers after they've been mounted.
         */
        sh "docker build -f Dockerfile_test -t ${config.image} ./ --memory 2048m --build-arg ARG_USER_NAME=\"\$(whoami)\" --build-arg ARG_USER_UID=\"\$(id -u)\""
        sh "docker run --mount src=\"\$(pwd)\",target=/MDS/app_dir,type=bind --mount src=/opt/jenkins/.m2,target=/root/.m2,type=bind --rm --name ${config.container} ${config.image}"
        sh "docker stop ${config.container} || true"
        echo 'Test with docker - Successful'
        sh """ echo '<font color="green">Successful</font></td></tr>' >> ${config.reportFile} """
    } catch(exception) {
        sh "docker stop ${config.container} || true"
        echo 'Test with docker - Failure'
        sh """ echo '<font color="red">Failed</font></td></tr>' >> ${config.reportFile} """
        error exception.toString()
    }
}
