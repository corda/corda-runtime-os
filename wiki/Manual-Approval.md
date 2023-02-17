# Manual approval of registration requests

Membership groups in Corda 5 may be configured to approve (or decline) member registration requests manually. The MGM operator can configure the approval method for joining the group, so that requests satisfying pre-defined criteria would require manual approval, while others would be auto-approved. Manual approval presents the request to the MGM operator, and allows the operator to review the request before approving/declining it via the REST API. This applies to registration and re-registration requests alike. The configuration may be added at any point in time, and only affects future registration requests - previously approved members will not be required to re-register.


# How to configure a group for manual approval

Registration requests are evaluated according to regular expression-based rules submitted by the MGM operator. The proposed `MemberInfo` is compared with the previous (if any) `MemberInfo` to calculate the difference in their member contexts. This difference will be 100% in case of a first-time registration, since there will be no previous `MemberInfo` for that member known to the MGM. If any of the keys present in this `MemberInfo` difference match the regular expressions set by the MGM operator, the request will require manual approval. If there are no matches, the request is auto-approved.

## Add a group approval rule

<details>
<summary>Bash</summary>

```bash
RULE_PARAMS='{"ruleParams":{"ruleRegex": "corda.*", "ruleLabel": "Review all changes to keys in the Corda namespace"}}'
curl --insecure -u admin:admin -d "$RULE_PARAMS" $API_URL/mgm/$MGM_HOLDING_ID/approval/rules
```

</details>

## View current group approval rules

<details>
<summary>Bash</summary>

```bash
curl --insecure -u admin:admin $API_URL/mgm/$MGM_HOLDING_ID/approval/rules
```

</details>

## Delete a group approval rule

Replace `<RULE ID>` with the ID of the rule to be deleted. The rule ID can be retrieved from the response of creating a rule, or from the response of the GET endpoint described previously.

<details>
<summary>Bash</summary>

```bash
RULE_ID=<RULE ID>
curl --insecure -u admin:admin -X DELETE $API_URL/mgm/$MGM_HOLDING_ID/approval/rules/$RULE_ID
```

</details>

# How to manually approve/decline requests

Requests which are pending manual approval are assigned `PENDING_MANUAL_APPROVAL` status.

## View requests pending manual approval

<details>
<summary>Bash</summary>

```bash
curl --insecure -u admin:admin $API_URL/mgm/$MGM_HOLDING_ID/registrations
```

</details>

To optionally view requests from a specific member (e.g. C=GB, L=London, O=Alice) and/or include historic requests, use:

<details>
<summary>Bash</summary>

```bash
curl --insecure -u admin:admin $API_URL'/mgm/'$MGM_HOLDING_ID'/registrations?requestsubjectx500name=C%3DGB%2C%20L%3DLondon%2C%20O%3DAlice&viewhistoric=true'
```

</details>

## Approve a request
> Note: This only works with requests that are in `PENDING_MANUAL_APPROVAL` status.

Replace `<REQUEST ID>` with the ID of the registration request.

<details>
<summary>Bash</summary>

```bash
REQUEST_ID=<REQUEST ID>
curl --insecure -u admin:admin -X POST $API_URL/mgm/$MGM_HOLDING_ID/approve/$REQUEST_ID
```

</details>

## Decline a request
> Note: This only works with requests that are in `PENDING_MANUAL_APPROVAL` status.

Replace `<REQUEST ID>` with the ID of the registration request.

<details>
<summary>Bash</summary>

```bash
REQUEST_ID=<REQUEST ID>
REASON='{"reason":{"reason": "test"}}'
curl --insecure -u admin:admin -d "$REASON" $API_URL/mgm/$MGM_HOLDING_ID/decline/$REQUEST_ID
```

</details>

# Pre-authentication of registration requests

The network operator can decide to pre-authenticate registering members, which will allow the registering member to by-pass any approval rules that have been defined for the group as described previously. Authentication is done outside of Corda using any criteria the network operator chooses. Once the network operator has finished their authentication process, they can generate a one-time-use pre-authentication token, also known as a pre-auth token, specific to the member that has been authenticated. 

Corda has a set of REST APIs available for managing these pre-auth tokens. Through these APIs, tokens can be created, revoked, and viewed. When viewing a token, it is possible to see the token ID, the X.500 name of the member the token is assigned to, optionally a time and date when the token expires, the token status, and additional information provided by the MGM when creating or revoking the token.

## Creating a pre-auth token

Token creation is done through a REST API using the POST method. At a minimum, the X.500 name of the member the token is for must be provided. This token will be tied to that X.500 name and only a registering member with the same X.500 name will be able to consume that token.

<details>
<summary>Bash</summary>
```bash
curl --insecure -u admin:admin -X POST -d '{"ownerX500Name": "O=Alice, L=London, C=GB"}' $API_URL/mgm/$MGM_HOLDING_ID/preauthtoken
```
</details>

This REST API allows for additional optional properties to be submitted also when creating a token. The first is a time-to-live, which allows a duration to be submitted, after which the token will no longer be valid for use. This duration is submitted in the `ISO-8601` duration format (i.e. `PnDTnHnMn.nS`). For example, PT15M (15 minutes), P4D (4 days), P1DT2H2M (1 day 2 hours and 2 minutes). The duration submitted is added to the current time when the request to create the token is submitted to calculate the time after which the token is no longer valid. If no time-to-live value is submitted, the token will only expire after it is consumed or revoked. The second optional property that can be submitted is a remark. This is simply a user defined string which will be stored along with the token as additional information which can provide information about the token creation when the token is viewed. 

<details>
<summary>Bash</summary>
```bash
curl --insecure -u admin:admin -X POST -d '{"ownerX500Name": "O=Alice, L=London, C=GB", "ttl": "P7D", "remarks": "Member was verified offline on 01/02/2023 and does not need additional verification when joining the network."}' $API_URL/mgm/$MGM_HOLDING_ID/preauthtoken
```
</details>

## Viewing tokens

To view tokens that have been created, you need to use the pre-auth token GET API. This returns a list of all tokens that the MGM has created which have not been consumed, revoked, or automatically invalidated by Corda (for example, due to an expired TTL).

If you wish to view tokens which are inactive (i.e. consumed, revoked, or auto-invalidated), you can set the query parameter `viewInactive` equal to true and pre-auth tokens which are available will be returned along with tokens with are consumed, revoked, or auto-invalidated. If this is set to false, only tokens which are active and ready to use are returned.

<details>
<summary>Bash</summary>
```bash
curl --insecure -u admin:admin $API_URL'/mgm/'$MGM_HOLDING_ID'/preauthtoken?viewinactive=false'
```
</details>

This endpoint accepts optional parameters to filter or expand the search results. The first filter is the X.500 name of the member who the token was issued for. This is passed in as a URL query parameter called `ownerX500Name`. The full URL encoded X.500 name should be passed in here to filter correctly. The second filter is token ID. If you know the ID of a specific token you want to look up then you can provide that to the API as the query parameter `preAuthTokenId`.  

These optional parameters can be used in any combination. Here is a sample of all used together:

<details>
<summary>Bash</summary>
```bash
TOKEN_ID=<token id>
OWNER_X500=<URL encoded X.500 name>
curl --insecure -u admin:admin $API_URL'/mgm/'$MGM_HOLDING_ID'/preauthtoken?viewInactive=true&preAuthTokenId='$TOKEN_ID'&ownerX500Name='$OWNER_X500
```
</details>

## Revoking tokens

After issuing a token, and before it has been consumed, the network operator might decide that the authentication they previously performed is no longer valid. If this happens, the network operator can revoke a token which was previously issued. This will prevent the token from being used. Any registrations submitted with a revoked token will be automatically declined.

To revoke a token, the network operator needs to submit the token ID to a `PUT` endpoint.

<details>
<summary>Bash</summary>
``` bash 
TOKEN_ID=<token id>
curl --insecure -u admin:admin -X PUT $API_URL/mgm/$MGM_HOLDING_ID/preauthtoken/revoke/$TOKEN_ID
```
</details>

Optionally, the network operator can submit a remark with the action to revoke the token which will be stored with the token and visible when viewing tokens for future reference. To do this, a body should be included in the request.

<details>
<summary>Bash</summary>
``` bash 
TOKEN_ID=<token id>
curl --insecure -u admin:admin -X PUT -d '{"remarks":"Additional authentication required."}' $API_URL/mgm/$MGM_HOLDING_ID/preauthtoken/revoke/$TOKEN_ID
```
</details>

# Submitting a pre-auth token in a registration request

Once the network operator has generated a pre-auth token, they can distribute this to a registering member through offline channels (i.e. outside of Corda). The registering member must then include this pre-auth token in the registration request they submit when registering. To do this, an additional key must be set in the registration context. This key is `corda.auth.token`, and the value of this key should be the pre-auth token that the MGM provided.

For example, if we take this sample registration request context as a base:

``` json
 {
 "corda.session.key.id": "CD432EA37B69",
 "corda.session.key.signature.spec": "SHA256withECDSA",
 "corda.ledger.keys.0.id": "4A37E41B63A7",
 "corda.ledger.keys.0.signature.spec": "SHA256withECDSA",
 "corda.endpoints.0.connectionURL": "https://alice.corda.com:8080",
 "corda.endpoints.0.protocolVersion": "1"
}
```

If the MGM generated and distributed the token `8d738966-07f0-456b-bc0e-19e61d7b90a3`, the member would submit the registration context:

``` json
 {
 "corda.session.key.id": "CD432EA37B69",
 "corda.session.key.signature.spec": "SHA256withECDSA",
 "corda.ledger.keys.0.id": "4A37E41B63A7",
 "corda.ledger.keys.0.signature.spec": "SHA256withECDSA",
 "corda.endpoints.0.connectionURL": "https://alice.corda.com:8080",
 "corda.endpoints.0.protocolVersion": "1",
 "corda.auth.token": "8d738966-07f0-456b-bc0e-19e61d7b90a3"
}
```

There are a number of scenarios in which a registration with a pre-auth token could fail.

1. If the token is not a valid UUID.
2. If the token was not issued for the X.500 name of the registering member.
3. If the registration was submitted after the token's time-to-live has expired.
4. If the token was revoked by the MGM.
5. If the token was successfully consumed previously.

If any of these conditions are met, the registration will be declined. 

A token is successfully consumed if a registration containing the token was approved automatically, approved manually, or declined manually. The next section goes through manual approve/decline of registrations with valid pre-auth tokens.


# Configure manual approval of pre-authenticated registrations

Pre-auth tokens allow registrations to skip the standard set of registration rules configured, though the network operator might still want to review certain changes to the member's context even if a pre-auth token was submitted. This is possible through a set of APIs very similar to the previously described APIs for defining registration approval rules. 

These pre-auth approval rules are applied to the registration requests containing a valid pre-auth token in the same way as done for standard approval rules applied to registrations without a valid pre-auth token.

## Add a pre-auth group approval rule

This API will add a group approval rule used specifically for the registrations containing a valid pre-auth token. The body of the request is the same as for standard approval rules. It contains the regular expression which is applied to all changed keys in the member context, and a label to describe the rule for informational purposes.

<details>
<summary>Bash</summary>

```bash
RULE_PARAMS='{"ruleParams":{"ruleRegex": "^corda.endpoints.*$", "ruleLabel": "Any change to P2P endpoints requires manual review."}}'
curl --insecure -u admin:admin -d $RULE_PARAMS $API_URL/mgm/$MGM_HOLDING_ID/approval/rules/preauth
```
</details>

## View current pre-auth group approval rules

Viewing the current pre-auth approval rules is also similar to the method of viewing standard group approval rules. This is a GET endpoint which shows how the group is currently configured in terms of automatically approving registrations requests with a valid pre-auth token.

<details>
<summary>Bash</summary>

```bash
curl --insecure -u admin:admin $API_URL/mgm/$MGM_HOLDING_ID/approval/rules/preauth
```

</details>

## Delete a pre-auth group approval rule

To remove a pre-auth group approval rule, we must use the `DELETE` API. This API permanently removes a pre-auth approval rule so it will immediately stop being applied to registrations. To use the below sample, replace `<RULE ID>` with the ID of the rule to be deleted.

<details>
<summary>Bash</summary>

```bash
RULE_ID=<RULE ID>
curl --insecure -u admin:admin -X DELETE $API_URL/mgm/$MGM_HOLDING_ID/approval/rules/preauth/$RULE_ID
```

</details>


# Manual approval of pre-authenticated registrations

Viewing, approving or declining paused registration requests with pre-auth tokens included is done using the same API as registrations without pre-auth tokens. Refer to the section "How to manually approve/decline requests" above to see how this is done.

