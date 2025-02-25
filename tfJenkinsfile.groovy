    pipeline {
        agent  any 

    parameters{
        string(defaultValue: '', description: 'RFC Extract File Name without csv extension', name: 'RFC')
        string(defaultValue: '', description: 'Email Address of the recipient list', name: 'RecipientList')
        string(defaultValue: '', description: 'Subscription Name', name: 'Subscription')
        string(defaultValue: '', description: 'Resource Group Name', name: 'ResourceGroup')
        string(defaultValue: 'Devops', description: 'Resource Group Name', name: 'Terraform_StorageAccount_ResourceGroup')
        string(defaultValue: 'devstore1', description: 'Resource Group Name', name: 'Terraform_StorageAccount_Name')
        string(defaultValue: '', description: 'application Name', name: 'appName1')
        choice(name: 'Environment', choices: ['Dev', 'Stage', 'Prod'], description: 'Select deployment environment')
        
	
    }

    environment {
        PS_FILE = 'terraform_vm.ps1'
        TF_VAR_client_id = credentials('terraform_managed_identity')
	    TF_VAR_tenant_id = credentials('Azure_TenantId')
	    TF_VAR_subscription_id = credentials('subscription_id')
        varFile = "'./deployment_configs/${env.RFC}.tfvars'"
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
	stage('validating checkout') {
            steps {
                bat """
                    @echo off
                    echo APPLICATION SPECIFIC VARIABLES:
                    echo *******************************************
                    echo PS_FILE..........'${env.PS_FILE}'
                    echo *******************************************
                    set
                """
                script {
                    if (! fileExists(env.PS_FILE)) {
                        error("The file does not exist: env.PS_FILE.")
                    } else
                    {
                        echo "${env.PS_FILE} exists"
                    }
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
        stage('DeployParametersScript') {
            steps{
                script {

                    def appName = powershell(script: """
                        . './${env.PS_FILE}' '${env.RFC}'
                    """, returnStdout: true).trim()
                    env.APP_NAME = appName
                    echo "appName captured: ${env.APP_NAME}"
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
                    terraform -chdir="./applications/terraform_poc/modules_windows" init -backend-config=\"key=./${env.Environment}/${env.APP_NAME}_terraform.tfstate\" -var-file=$varFile
                    """)
                }
            }
        }

        stage('Terraform Action in Dev') {
            when {
                expression { params.Environment == 'Dev' }
            }
            steps{
                script {
    
                    def commandOutput = powershell (script: """
                        terraform -chdir="./applications/terraform_poc/modules_windows" plan -var-file=$varFile
                    """, returnStdout: true).trim()
                    echo commandOutput

                    def proceed = input(
                        message: 'Do you want to proceed with deployment?',
                        parameters: [booleanParam(defaultValue: false, description: 'Proceed to DeployTerraform?', name: 'DeployTerraform')]
                    )

                    if (proceed) {
                        echo 'Proceeding with deployment...'
                    } else {
                        error('DeployTerraform was not approved. Aborting pipeline.')
                    }
                }
            }
        }
        stage('DeployTerraform in Dev') {
            when {
                expression { params.Environment == 'Dev' }
            }
            steps{
                script {
                    powershell (script: """
                        terraform -chdir="./applications/terraform_poc/modules_windows" apply -var-file=$varFile -auto-approve
                    """) 
                }
                script {
                    echo "Email address of RecipientList is: ${env.RecipientList}"
                    emailext attachLog: true, attachmentsPattern: '', body: 'see the results in the attachments', subject: 'Deploy Terraform in Dev', to: env.RecipientList
                }
            }
        }
        stage('Initiate Terraform in Stage') {
            when {
                expression { params.Environment == 'Stage' }
            }
            steps{
                script {

                    powershell (script: """
                    terraform -chdir="./applications/terraform_poc/modules_windows" init -backend-config=\"key=./${env.Environment}/${env.APP_NAME}_terraform.tfstate\" -var-file=$varFile
                    """)
                }
            }
        }

        stage('Terraform Action in Stage') {
            when {
                expression { params.Environment == 'Stage' }
            }
            steps{
                script {
    
                    def commandOutput = powershell (script: """
                        terraform -chdir="./applications/terraform_poc/modules_windows" plan -var-file=$varFile
                        
                    """, returnStdout: true).trim()
                    echo commandOutput

                    def proceed = input(
                        message: 'Do you want to proceed with deployment?',
                        parameters: [booleanParam(defaultValue: false, description: 'Proceed to DeployTerraform?', name: 'DeployTerraform')]
                    )

                    if (proceed) {
                        echo 'Proceeding with deployment...'
                    } else {
                        error('DeployTerraform was not approved. Aborting pipeline.')
                    }
                }
            }
        }
        stage('DeployTerraform in Stage') {
            when {
                expression { params.Environment == 'Stage' }
            }
            steps{
                script {
                    powershell (script: """
                        terraform -chdir="./applications/terraform_poc/modules_windows" apply -var-file=$varFile -auto-approve
                    """) 
                }
                script {
                    echo "Email address of RecipientList is: ${env.RecipientList}"
                    emailext attachLog: true, attachmentsPattern: '', body: 'see the results in the attachments', subject: 'Deploy Terraform in Stage', to: env.RecipientList
                }
            }
        }
        stage('Initiate Terraform in Prod') {
            when {
                expression { params.Environment == 'Prod' }
            }
            steps{
                script {

                    powershell (script: """
                    terraform -chdir="./applications/terraform_poc/modules_windows" init -backend-config=\"key=./${env.Environment}/${env.APP_NAME}_terraform.tfstate\" -var-file=$varFile
                    """)
                }
            }
        }

        stage('Terraform Action in Prod') {
            when {
                expression { params.Environment == 'Prod' }
            }
            steps{
                script {
    
                    def commandOutput = powershell (script: """ 
                        terraform -chdir="./applications/terraform_poc/modules_windows" plan -var-file=$varFile
                    """, returnStdout: true).trim()
                    echo commandOutput

                    def proceed = input(
                        message: 'Do you want to proceed with deployment?',
                        parameters: [booleanParam(defaultValue: false, description: 'Proceed to DeployTerraform?', name: 'DeployTerraform')]
                    )

                    if (proceed) {
                        echo 'Proceeding with deployment...'
                    } else {
                        error('DeployTerraform was not approved. Aborting pipeline.')
                    }
                }
            }
        }
        stage('DeployTerraform in Prod') {
            when {
                expression { params.Environment == 'Prod' }
            }
            steps{
                script {
                    powershell (script: """
                        terraform -chdir="./applications/terraform_poc/modules_windows" apply -var-file=$varFile -auto-approve
                    """) 
                }
                script {
                    echo "Email address of RecipientList is: ${env.RecipientList}"
                    emailext attachLog: true, attachmentsPattern: '', body: 'see the results in the attachments', subject: 'Deploy Terraform in Prod', to: env.RecipientList
                }
            }
        }
    }
} 
