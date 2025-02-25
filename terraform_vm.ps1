param (
    [Parameter(Mandatory=$true)]
    [ValidatePattern("^[M]?RFC-\d{4}-\d{4}")]
    [string]$RFC,
    
    [Parameter(Mandatory=$False)]
    [switch]$Interactive
)

$rfcfile = './<excelFilePath>/' + $RFC + '.csv'
if(!(Test-Path $rfcfile) )
{
    Write-Host "RFC extract file, $rfcfile does not exist, abort the process"
    exit 1
}

# Read values from .csv file
$values = Import-Csv -Path $rfcfile -verbose

# create array list
$vmNames = @()
$ipAddresses = @()
$vmSizes = @{}


$buildVarFile = ""
foreach ($value in $values) {

    if ($value.vmName) {

        $vmNames += $value.vmName
    }
    if ($value.ipAddress) {

        $ipAddresses += $value.ipAddress
    }
    if ($value.vmName -and $value.vm_size) {
        $vmSizes[$value.vmName] = $value.vm_size
    }

    $vmSizesString = ""
}

$buildVarFile += @"

    vm_names = ["$($vmNames -join '","')"]
    
    vm_ip_address = ["$($ipAddresses -join '","')"]
"@
foreach ($entry in $vmSizes.GetEnumerator()) {
    $vmSizesString +=@"
"$($entry.Key)" : "$($entry.Value)",`n`t`t`t`t
"@ 
}
$buildVarFile += @"

    applicationName = "$($value.applicationName)"  
        
    vm_sizes = {$($vmSizesString.TrimEnd(','))} 

    availabilityZone = "$($value.availabilityZone)"
        
    vnet_resource_group = "$($value.vnetResourceGroup)"

    vnet = "$($value.vnet)"

    subnet = "$($value.subnet)"

    subscription = "$($value.subscriptionId)"

    resourceGroup = "$($value.resourceGroup)"
        
    DeviceIdTagValue = "$($value.DeviceIdTagValue)"

    FinancialTagValue = "$($value.FinancialTagValue)"

    ImageTagValue = "$($value.ImageTagValue)"

    RoleTagValue = "$($value.RoleTagValue)"

    Schedule = "$($value.Schedule)"

    WindowsImage = "$($value.WindowsImage)"

    storageSku = "$($value.storageSku)"

    data_disk_sku = "$($value.data_disk_sku)"

    os_disk_size = "$($value.os_disk_size)"

    data_disk_size = "$($value.data_disk_size)"

    location = "$($value.location)"

    domain = "$($value.domain)"
    
    userAssignedManagedId_vm_name = "$($value.userAssignedManagedId_vm_name)"
    
    dataDiskCount = "$($value.dataDiskCount)"
    
    new_rsv_resourceGroup = "$($value.new_rsv_resourceGroup)"
    
    existing_rsv_resourceGroup = "$($value.existing_rsv_resourceGroup)"

    new_rsvName = "$($value.new_rsvName)"
    
    existing_rsvName = "$($value.existing_rsvName)"
    
    new_or_existing_rsv = "$($value.new_or_existing_rsv)" 
"@

$env:applicationName = $value.applicationName
$appName = $env:applicationName
Write-Output $appName

# Write the updated template to a file
$varfile = './<outfilePath>/' + $RFC + '.tfvars'
$buildVarFile | Set-Content -Path $varfile
