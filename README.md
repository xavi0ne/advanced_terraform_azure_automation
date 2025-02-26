# TERRAFORM ON AZURE: A Baseline for integrating Terraform using Jenkins CICD Pipelines. 

Terraform is an open-source IaC tool for deploying cloud infrastructure for multi-cloud organizations. It enables the management of any cloud infra using Terraform providers.
Enables efficient management for infrastructure and automation. Allows a clear and concise understanding for infra changes before they are applied. 

## DETAILS

### TERRAFORM TEMPLATES

The 'applications.zip' file contains the modularized templates that can serve to be used when integrating on an automation pipeline. 
The structure of the files contained in this repo assumes the following:

+ The templates are scoped to be deployed in an Azure US Government subscription.
+ The templates assume the following resources exist in the subscription prior to deployment:
  + Azure Key Vault and secrets for:
    + administrator user name.
    + administrator password.
    + domain join user credentials.
    + storage account access key.
    + DNS IPs for Domain Controller.
  + Azure Storage Account and Container.
    + a custom script file URL to be used with Custom Script Extension.

### JENKINSFILE

The Jenkinsfiles are to be used as a baseline for a CICD Jenkins Pipeline. It is assumed the following are met prior to use of files: 

+ A running Jenkins Pipeline Server.
+ A Managed Identity scoped to the Azure VM which hosts the Jenkins Server. 
+ Repository credentials added to Jenkins managed credentials for Repo Access.

More details on how to install and run Jenkins can be found here: https://www.jenkins.io/doc/book/installing/
