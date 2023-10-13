def call(Map config = [:]) {
    def content = """\
                  <html><head><title>${config.repo} Report</title><style>
                  #TestCategory {
                      font-family: "Trebuchet MS", Arial, Helvetica, sans-serif;
                      width: 100%;
                      border-collapse: collapse;
                  }
                  #TestCategory td, #TestCategory th {
                      font-size: 1em;
                      border: 1px solid #000000;
                      padding: 3px 7px 2px 7px;
                  }
                  #TestCategory th {
                      font-size: 1.1em;
                      text-align: left;
                      padding-top: 5px;
                      padding-bottom: 4px;
                      background-color: #1E90FF;
                      color: #ffffff;
                  }
                  </style></head>
                  <body>
                  <font face="Trebuchet MS, Arial, Helvetica, sans-serif">
                  <p><b>Build Number: </b>${env.BUILD_NUMBER}</p>
                  <p><b>Logs: </b>
                  <a href="${BUILD_URL}Logs">All Logs</a></p>
                  <p><b>Workflow View: </b>
                  <a href="${JOB_URL}workflow-stage/">Workflow</a></p>
                  <p><b>Branch: ${config.branch}</b></p>
                  <table border="1" id="TestCategory">
                  <tr><th>Step</th><th>Status</th></tr>""".stripIndent()

    fileOperations([fileCreateOperation(fileContent: content, fileName: 'index.html')])
}
