The MGM can update the current group parameters using the MGM API. This update may only include changes to the minimum platform version and custom properties. To update the group parameters, the MGM must submit an updated version of the group parameters to the REST endpoint, which will overwrite any previous properties submitted using the endpoint.

To view current group parameters:

<details>
<summary>Bash</summary>

```bash
curl --insecure -u admin:admin -X GET $API_URL/members/$HOLDING_ID/group-parameters
```
</details>

<details>
<summary>PowerShell</summary>

```PowerShell
 Invoke-RestMethod -SkipCertificateCheck  -Headers @{Authorization=("Basic {0}" -f $AUTH_INFO)} -Uri "$API_URL/membership/$HOLDING_ID/group-parameters" | ConvertTo-Json -Depth 4
```
</details>

To submit a group parameters update, use the MGM API endpoint shown below. Keys of custom properties must have the prefix "ext.". For minimum platform version, use key "corda.minimum.platform.version". For example:

<details>
<summary>Bash</summary>

```bash
export GROUP_PARAMS_UPDATE='{"newGroupParameters":{"corda.minimum.platform.version": "50000", "ext.group.key.0": "value0", "ext.group.key.1": "value1"}}'
curl --insecure -u admin:admin -d "GROUP_PARAMS_UPDATE" $API_URL/mgm/$HOLDING_ID/group-parameters
```
</details>
<details>
<summary>PowerShell</summary>

```PowerShell
GROUP_PARAMS_UPDATE = @{
  'corda.minimum.platform.version' = "50000"
  'ext.group.key.0' = "value0"
  'ext.group.key.1' = "value1"
}
$GROUP_PARAMS_UPDATE_RESPONSE = Invoke-RestMethod -SkipCertificateCheck  -Headers @{Authorization=("Basic {0}" -f $AUTH_INFO)} -Method Post -Uri "$API_URL/mgm/$HOLDING_ID/group-parameters" -Body (ConvertTo-Json -Depth 4 @{
    newGroupParameters = $GROUP_PARAMS_UPDATE
})
$GROUP_PARAMS_UPDATE_RESPONSE.parameters
```
</details>

These submitted parameters are combined with notary information from the platform to construct the new group parameters, which are then signed by the MGM and distributed within the network.

> Note: Custom properties have a character limit of 128 for keys and 800 for values. A maximum of 100 such key-value pairs may be defined.
