def call (String kpackNamespace, String appName, String imageTag){

    withKubeConfig(credentialsId: 'kpack-kubeconfig') {
        script {
            String name = sh(
                    script: "kubectl get images -o json -n ${kpackNamespace} " +
                            "| jq --raw-output '.items[] " +
                            "| select(.metadata.name == \"${appName}\").metadata.name'",
                    returnStdout: true
            ).trim()

            boolean kpackImageResourceDoesntExistYet = name == null || name.equals('')

            if (kpackImageResourceDoesntExistYet) {
                sh "echo 'Creating Image Resource'"
                String scmUrl = scm.getUserRemoteConfigs()[0].getUrl()
                sh """
                    kubectl apply -f - <<EOF
apiVersion: kpack.io/v1alpha2
kind: Image
metadata:
  name: ${appName}
  namespace: ${kpackNamespace}
spec:
  additionalTags:
    - ${env.IMAGE_TAG}
  build:
    env:
      - name: BP_JVM_VERSION
        value: "17"
    services:
      - kind: Secret
        name: maven
      - kind: Secret
        name: certs
  builder:
    kind: ClusterBuilder
    name: jammy-tiny-custom
  serviceAccountName: kpack
  source:
    git:
      revision: ${env.GIT_COMMIT}
      url: ${scmUrl}
  tag: ${imageTag}
EOF
                    """

                sh 'sleep 10'

            } else {
                // Patch an existing image resource
                sh "kp image patch ${appName} -n ${kpackNamespace} --additional-tag ${env.IMAGE_TAG} && sleep 3"

                String existingImageRevision = sh(
                        script: "kubectl get image -o jsonpath={.spec.source.git.revision} -n ${kpackNamespace} ${appName}",
                        returnStdout: true
                ).trim()
                // Check if git commits are the same
                def imageAlreadyBuiltForCurrentCommit = existingImageRevision.equals("${env.GIT_COMMIT}".toString())

                if (imageAlreadyBuiltForCurrentCommit) {
                    // Trigger an image resource build
                    sh "kp image trigger ${appName} -n ${kpackNamespace} && sleep 3"
                } else {
                    // Save the image resource build
                    sh "kp image save ${appName} --git-revision ${env.GIT_COMMIT} -n ${kpackNamespace} && sleep 3"
                }
            }
            sh "kp build logs ${appName} -n ${kpackNamespace}"
            sh "kp image patch ${appName} -n ${kpackNamespace} --delete-additional-tag ${env.IMAGE_TAG}"
        }
    }
                
}