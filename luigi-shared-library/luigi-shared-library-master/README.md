# luigi-jenkins-shared-library

## addCommitStatus 

The `addCommitStatus` function is used to add a commit status to a GitHub repository. It takes a configuration map as a parameter, which contains the following properties:

- `ghOwner`: The GitHub owner/organization name.
- `ghRepo`: The GitHub repository name.
- `context`: The context of the commit status.
- `state`: The state of the commit status (e.g., 'success', 'failure', 'pending').
- `description`: The description of the commit status.

Inside the function, the `TOKEN` credential is retrieved using the `withCredentials` block. The commit hash is obtained using `git rev-parse HEAD`. Then, a `curl` command is executed to send a POST request to the GitHub API, creating the commit status. The request includes the necessary headers and payload with the provided configuration parameters.

Finally, the function returns without any explicit value.