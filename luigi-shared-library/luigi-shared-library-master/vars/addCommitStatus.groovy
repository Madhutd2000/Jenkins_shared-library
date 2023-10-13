
/**
 * Adds a commit status to a GitHub repository.
 *
 * @param config A map containing the configuration parameters:
 *   - ghOwner: The GitHub owner/organization name.
 *   - ghRepo: The GitHub repository name.
 *   - context: The context of the commit status.
 *   - state: The state of the commit status (e.g., 'success', 'failure', 'pending').
 *   - description: The description of the commit status.
 */
 def call (Map config = [:]) {
  String ghOwner = config.ghOwner
  String ghRepo = config.ghRepo
  String context = config.context
  String state = config.state
  String description = config.description

  withCredentials([string(credentialsId: 'ghChecks-tkn', variable: 'TOKEN')]) {
    sh """
      COMMIT_HASH=`git rev-parse HEAD`
      curl -L \
        -X POST \
        -H "Accept: application/vnd.github+json" \
        -H "Authorization: Bearer \$TOKEN" \
        -H "X-GitHub-Api-Version: 2022-11-28" \
        https://github.worldpay.com/api/v3/repos/${ghOwner}/${ghRepo}/statuses/\$COMMIT_HASH \
        -d '{"state":"${state}","target_url":"${env.RUN_DISPLAY_URL}","description":"${description}","context":"${context}"}'
    """
  }

  return
}