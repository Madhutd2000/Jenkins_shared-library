def call(Map config = [:]) {

    echo "Running checkmarx scan, inside of call"
    step([$class: 'CxScanBuilder',
        avoidDuplicateProjectScans: true,
        comment: 'scan created by jenkins',

        credentialsId: 'launchpad-api-checkmarx-creds',
        excludeFolders: '!**/*modules/**, modules',
//          excludeOpenSourceFolders: '',
        exclusionsSetting: 'global',
        failBuildOnNewResults: false,
        failBuildOnNewSeverity: 'HIGH',
        fullScanCycle: 10,
        generatePdfReport: true,
        teamPath: "\\CxServer\\SP\\WorldPay\\DisputesValueStream\\MerchantDisputes",
//          includeOpenSourceFolders: '',
        osaArchiveIncludePatterns: '*.zip, *.war, *.ear, *.tgz',
        osaInstallBeforeScan: false, preset: '100000',
        projectName: config.checkmarxProjectName + '_dev',
        sastEnabled: true,
        sourceEncoding: '1',
//          username: '',
        vulnerabilityThresholdResult: 'FAILURE',
//        jobStatusOnError: 'FAILURE',
        waitForResultsEnabled: true])
        // echo "exit 1"
}
