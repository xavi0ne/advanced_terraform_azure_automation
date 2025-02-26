pipeline {
    agent  any 

    parameters{
        string(defaultValue: '', description: 'RFC Extract File Name without csv extension', name: 'RFC')
        string(defaultValue: '', description: 'State File Name without tfstate extension', name: 'STATE_FILE_NAME')
        string(defaultValue: '', description: 'Email Address of the recipient list', name: 'RecipientList')
        string(defaultValue: '', description: 'Subscription Name', name: 'Subscription')
        string(defaultValue: '', description: 'Resource Group Name', name: 'ResourceGroup')
        string(defaultValue: 'Devops', description: 'Resource Group Name', name: 'Terraform_StorageAccount_ResourceGroup')
        string(defaultValue: 'devstore1', description: 'Storage Account Name', name: 'Terraform_StorageAccount_Name')
        choice(name: 'Environment', choices: ['Dev'], description: 'Select deployment environment')
    }

    environment {
      TF_VAR_client_id = credentials('terraform_managed_identity')
	    TF_VAR_tenant_id = credentials('Azure_TenantId')
	    TF_VAR_subscription_id = credentials('subscription_id')
      varFile = "'./deployment_configs/${params.STATE_FILE_NAME}.tfvars'"
    }
    stages {
        stage('Set Build Name'){
            steps {
                script {
                    currentBuild.displayName = env.name
                    currentBuild.description = env.description
                }
            }
        }
        stage('import extract file values') {
                steps {
                    script {
                        def vms = powershell (script: '''

                            $rfcfile = "./extracts/$env:RFC.csv"
                            if(!(Test-Path $rfcfile) ) {
                                Write-Host "RFC extract file, $rfcfile does not exist, abort the process"
                                exit 1
                            }

                            # Read values from .csv file
                            $values = Import-Csv -Path $rfcfile -verbose
                            
                            $targets = @()

                            $vms = $values | ForEach-Object {

                                '\\"' + $_.vmName + '\\"'
                            }

                            $targets = $vms | ForEach { 
                            
                                "-target `'module.windows_vms.azapi_resource.vm[$($_)]`'"
                                "-target `'module.windows_vms.azapi_resource.nic_vm[$($_)]`'"
                            }

                            $env:targets = $targets -join ' '
                            $terraform_targets = $env:targets
                            Write-Output $terraform_targets

                        ''', returnStdout: true).trim()

                        env.TARGETS = vms
                        echo "targets captured: ${env.TARGETS}"
                        
                    }
                }
        }
        stage('Login to TSA Azure'){
                steps {
                    script {

                powershell "Connect-AzAccount -Environment AzureUSGovernment -Identity -AccountId ${TF_VAR_client_id} -TenantId ${env.TF_VAR_tenant_id}"
                    def storageKey = powershell(script: """
                    (Get-AzStorageAccountKey -ResourceGroupName ${env.Terraform_StorageAccount_ResourceGroup} -Name ${env.Terraform_StorageAccount_Name}).Value[0]
                """, returnStdout: true).trim()
                env.ARM_ACCESS_KEY = storageKey

                    }
                }
        }
        stage('Initiate Terraform in Dev') {
            when {
                expression { params.Environment == 'Dev' }
            }
            steps{
                script {

                    powershell (script: """
                        terraform -chdir="./applications/terraform_poc/modules_windows" init -backend-config=\"key=./${env.Environment}/${env.STATE_FILE_NAME}.tfstate\" -var-file=$varFile
                    """)
                }
            }
        }
        stage('Terraform Plan Destroy in Dev') {
            when {
                expression { params.Environment == 'Dev' }
            }
            steps {
                script {
                    // Run Terraform plan -destroy and capture output.
                    def planOutput = powershell (script: """

                        terraform -chdir="./applications/terraform_poc/modules_windows" plan -var-file=$varFile ${env.TARGETS} -destroy
                    """, returnStdout: true)

                    // Print Terraform output
                    echo planOutput

                    // Manual approval step
                    def proceed = input(
                        message: 'Do you want to proceed with decommission?',
                        parameters: [booleanParam(defaultValue: false, description: 'Proceed to decommission resources?', name: 'DeployTerraform')]
                    )

                    if (proceed) {
                        echo 'Proceeding with decommissioning...'
                    } else {
                        error('Decommission was not approved. Aborting pipeline.')
                    }
                }
            }
        }
        stage('Decomm VM resources in Dev') {
            when {
                expression { params.Environment == 'Dev' }
            }
            steps{
                script {
                    echo "targets captured: ${env.TARGETS}"

                    def destroyOutput = powershell (script: """
                    
                        terraform -chdir="./applications/terraform_poc/modules_windows" destroy ${env.TARGETS} -var-file=$varFile -auto-approve
                    """, returnStdout: true)

                    // Print Terraform output
                    echo destroyOutput
                }
                script {
                    echo "Email address of RecipientList is: ${env.RecipientList}"
                    emailext attachLog: true, attachmentsPattern: '', body: 'see the results in the attachments', subject: 'Deploy Terraform in Dev', to: env.RecipientList
                }
            }
        }
    }
}
