#!/bin/bash

set -e
SCRIPT_DIR=$(dirname ${BASH_SOURCE[0]})
source "$SCRIPT_DIR/settings.sh"

create_mgm_group_policy() {
echo '{
  "fileFormatVersion" : 1,
  "groupId" : "CREATE_ID",
  "registrationProtocol" :"net.corda.membership.impl.registration.dynamic.mgm.MGMRegistrationService",
  "synchronisationProtocol": "net.corda.membership.impl.synchronisation.MgmSynchronisationServiceImpl"
}' > $1
}

config_gateway() {
   config_version=$(curl --fail-with-body -s -S --insecure -u admin:admin https://$1/api/v1/config/corda.p2p.gateway/ | jq -r '.version')
   if [[ $MTLS == "Y" ]]; then
     tls_type="MUTUAL"
   else
     tls_type="ONE_WAY"
   fi
   raw_config=$(jq -n --arg tls_type "$tls_type" '.sslConfig.revocationCheck.mode="OFF" | .sslConfig.tlsType=$tls_type')
   body=$(jq -n --arg raw_config "$raw_config" --arg version $config_version '.section="corda.p2p.gateway" | .config=$raw_config |.schemaVersion.major=1 | .schemaVersion.minor=0| .version=$version')
   curl --fail-with-body -s -S --insecure -u admin:admin -X PUT  -d "$body" https://$1/api/v1/config
}

build_cli_tool() {
   local WORKING_DIR=$PWD
   cd $CORDA_CLI_DIR
   ./gradlew build

   cd $REPO_TOP_LEVEL_DIR
   ./gradlew :tools:plugins:package:build :tools:plugins:mgm:build
   cd $WORKING_DIR
   cp $REPO_TOP_LEVEL_DIR/tools/plugins/package/build/libs/package-cli-plugin-*.jar $REPO_TOP_LEVEL_DIR/tools/plugins/mgm/build/libs/mgm-cli*.jar $CORDA_CLI_DIR/build/plugins/
}


build_cpi() {
   local WORKING_DIR=$PWD
   cd $REPO_TOP_LEVEL_DIR

   ./gradlew testing:cpbs:test-cordapp:build

   cd $WORKING_DIR

   cp $REPO_TOP_LEVEL_DIR/testing/cpbs/test-cordapp/build/libs/test-cordapp-5.0.0.0-SNAPSHOT-package.cpb ./

   rm -f test-cordapp-5.0.0.0-SNAPSHOT-package.cpi

   rm -f signingkeys.pfx
   keytool -genkeypair -alias "signing key 1" -keystore signingkeys.pfx -storepass "keystore password" -dname "cn=CPI Plugin Example - Signing Key 1, o=R3, L=London, c=GB" -keyalg RSA -storetype pkcs12 -validity 4000
   keytool -importcert -keystore signingkeys.pfx -storepass "keystore password" -noprompt -alias gradle-plugin-default-key -file "$SCRIPT_DIR/gradle-plugin-default-key.pem"

   $CORDA_CLI_DIR/build/generatedScripts/corda-cli.sh package create-cpi --cpb test-cordapp-5.0.0.0-SNAPSHOT-package.cpb --group-policy $1 --cpi-name "test cordapp" --cpi-version "1.0.0.0-SNAPSHOT" --file test-cordapp-5.0.0.0-SNAPSHOT-package.cpi --keystore signingkeys.pfx --storepass "keystore password" --key "signing key 1" 
}

trust_cpi_keys() {
   curl --insecure -u admin:admin -X PUT -F alias="gradle-plugin-default-key" -F certificate=@"$SCRIPT_DIR/gradle-plugin-default-key.pem" https://$1/api/v1/certificates/cluster/code-signer
   keytool -exportcert -rfc -alias "signing key 1" -keystore signingkeys.pfx -storepass "keystore password" -file signingkey1.pem
   curl --insecure -u admin:admin -X PUT -F alias="signingkey1-2022" -F certificate=@signingkey1.pem https://$1/api/v1/certificates/cluster/code-signer
}

allow_client_certificate() {
  curl --fail-with-body --insecure -u admin:admin -X PUT  https://$1/api/v1/mgm/$3/mutual-tls/allowed-client-certificate-subjects/"$2"
}
upload_cpi() {
   local CPI_ID=$(curl --fail-with-body -s -S --insecure -u admin:admin -F upload=@$2 https://$1/api/v1/cpi/ | jq -M '.["id"]' | tr -d '"')
   echo $CPI_ID
}

wait_for_cpi() {
   n=0
   until [ "$n" -ge 25 ];  do
     cpi_status=$(curl --fail-with-body -s -S --insecure -u admin:admin https://$1/api/v1/cpi/status/$2 | jq -r .status)
     if [[ "$cpi_status" == "OK" ]]; then
       break
     else
       echo "CPI status is $cpi_status, waiting a bit"
       n=$((n+1))
       sleep 5
     fi
   done
}

cpi_checksum() {
   local CPI_CHECKSUM=$(curl --fail-with-body -s -S --insecure -u admin:admin https://$1/api/v1/cpi/status/$2 | jq -M '.["cpiFileChecksum"]' | tr -d '"')
   echo $CPI_CHECKSUM
}

create_vnode() {
    local MGM_HOLDING_ID_SHORT_HASH=$(curl --fail-with-body -s -S --insecure -u admin:admin -d '{ "request": { "cpiFileChecksum": "'$2'", "x500Name": "'$3'"  } }' https://$1/api/v1/virtualnode | jq -M '.["holdingIdentity"]|.["shortHash"]' | tr -d '"')
    echo $MGM_HOLDING_ID_SHORT_HASH
}

assign_hsm_and_generate_session_key_pair() {
    curl --fail-with-body -s -S --insecure -u admin:admin -X POST https://$1/api/v1/hsm/soft/$2/SESSION_INIT &> /dev/null
    local MGM_SESSION_KEY_ID=$(curl --fail-with-body -s -S --insecure -u admin:admin -X POST https://$1/api/v1/keys/$2/alias/$2-session/category/SESSION_INIT/scheme/CORDA.ECDSA.SECP256R1 | jq -M '.["id"]' | tr -d '"')
    echo $MGM_SESSION_KEY_ID
}

assign_hsm_and_generate_tls_key_pair() {
    curl --fail-with-body -s -S -k -u admin:admin -X POST https://$1/api/v1/hsm/soft/p2p/TLS &> /dev/null
    MGM_TLS_KEY_ID=$(curl --fail-with-body -s -S -k -u admin:admin -X POST https://$1/api/v1/keys/p2p/alias/cluster-tls/category/TLS/scheme/CORDA.RSA | jq -M '.["id"]' | tr -d '"')
    echo $MGM_TLS_KEY_ID
}

assign_hsm_and_generate_edch_key_pair() {
    curl --fail-with-body -s -S -k -u admin:admin -X POST https://$1/api/v1/hsm/soft/$2/PRE_AUTH &> /dev/null
    MGM_EDCH_KEY_ID=$(curl --fail-with-body -s -S -k -u admin:admin -X POST https://$1/api/v1/keys/$2/alias/$2-auth/category/PRE_AUTH/scheme/CORDA.ECDSA.SECP256R1 | jq -M '.["id"]' | tr -d '"')
    echo $MGM_EDCH_KEY_ID
}

assign_hsm_and_generate_ledger_key_pair() {
    curl --fail-with-body -s -S -k -u admin:admin -X POST https://$1/api/v1/hsm/soft/$2/LEDGER &> /dev/null
    LEDGER_KEY_ID=$(curl --fail-with-body -s -S -k -u admin:admin -X POST https://$1/api/v1/keys/$2/alias/$2-ledger/category/LEDGER/scheme/CORDA.ECDSA.SECP256R1 | jq -M '.["id"]' | tr -d '"')
    echo $LEDGER_KEY_ID
}

get_csr() {
    curl --fail-with-body -s -S -k -u admin:admin  -X POST -H "Content-Type: application/json" -d '{"x500Name": "'$2'", "subjectAlternativeNames": [ "'$3'" ]}' "https://$1/api/v1/certificates/p2p/$4" > ./$5.csr
}

sign_certificate() {
    java -jar $CA_JAR --home=./ca csr ./$1.csr
}

upload_certificate() {
    curl --fail-with-body -s -S -k -u admin:admin -X PUT  -F certificate=@$2 -F alias=cluster-tls "https://$1/api/v1/certificates/cluster/p2p-tls"
}

register_node() {
    local TLS_TRUST_STORE=$(cat ./ca/ca/root-certificate.pem | awk 1 ORS='\\n')

    local REG_CONTEXT='{
      "corda.session.key.id": "'$3'",
      "corda.ledger.keys.0.id": "'$5'",
      "corda.ledger.keys.0.signature.spec": "SHA256withECDSA",
      "corda.endpoints.0.connectionURL": "'$4'",
      "corda.endpoints.0.protocolVersion": "1"
    }'
   
    register $1 $2 "$REG_CONTEXT"
}

register_mgm() {
    local TLS_TRUST_STORE=$(cat ./ca/ca/root-certificate.pem | awk 1 ORS='\\n')
    if [[ $MTLS == "Y" ]]; then
      tls_type="Mutual"
    else
      tls_type="OneWay"
    fi

    local REG_CONTEXT='{
      "corda.session.key.id": "'$3'",
      "corda.ecdh.key.id": "'$5'",
      "corda.group.protocol.registration": "net.corda.membership.impl.registration.dynamic.member.DynamicMemberRegistrationService",
      "corda.group.protocol.synchronisation": "net.corda.membership.impl.synchronisation.MemberSynchronisationServiceImpl",
      "corda.group.protocol.p2p.mode": "Authenticated_Encryption",
      "corda.group.key.session.policy": "Combined",
      "corda.group.pki.session": "NoPKI",
      "corda.group.pki.tls": "Standard",
      "corda.group.tls.version": "1.3",
      "corda.group.tls.type": "'$tls_type'",
      "corda.endpoints.0.connectionURL": "'$4'",
      "corda.endpoints.0.protocolVersion": "1",
      "corda.group.truststore.tls.0" : "'$TLS_TRUST_STORE'",
      "corda.group.truststore.session.0" : "'$TLS_TRUST_STORE'"
    }'
    echo $REG_CONTEXT | jq
    register $1 $2 "$REG_CONTEXT"
}

wait_for_approve() {
  n=0
  until [ "$n" -ge 25 ];  do
    registrationStatus=$(curl --fail-with-body -s -S --insecure -u admin:admin https://$1/api/v1/membership/$2/$3 | jq -r .registrationStatus)
    if [[ "$registrationStatus" == "APPROVED" ]]; then
      curl --fail-with-body -s -S --insecure -u admin:admin https://$1/api/v1/membership/$2/$3 | jq
      break
    elif [[ "$registrationStatus" == "DECLINED" ]]; then
      curl --fail-with-body -s -S --insecure -u admin:admin https://$1/api/v1/membership/$2/$3 | jq
      exit -1
    else
      echo "Registration status is $registrationStatus, waiting a bit"
      n=$((n+1))
      sleep 5
    fi
  done
}

register() {
    local COMMAND='{ "memberRegistrationRequest": { "action": "requestJoin", "context": '$3' }}'

    echo "Registering using:"
    echo $COMMAND | jq

    # Register
    registrationId=$(curl --fail-with-body -s -S --insecure -u admin:admin -d " $COMMAND " https://$1/api/v1/membership/$2 | jq -r .registrationId)

    # Wait for registration to be approve
    wait_for_approve $1 $2 $registrationId
}

complete_network_setup() {
    curl --fail-with-body -s -S -k -u admin:admin -X PUT -d '{"p2pTlsCertificateChainAlias": "cluster-tls", "sessionKeyId": "'$3'"}' "https://$1/api/v1/network/setup/$2"
}

extract_group_policy() {
   curl --fail-with-body -s -S --insecure -u admin:admin -X GET "https://$1/api/v1/mgm/$2/info" > ./GroupPolicy-out.json
}

on_board_mgm() {

   config_gateway $MGM_RPC

   create_mgm_group_policy ./GroupPolicy.json

   build_cpi ./GroupPolicy.json

   trust_cpi_keys $MGM_RPC

   CPI_ID=$(upload_cpi $MGM_RPC ./test-cordapp-5.0.0.0-SNAPSHOT-package.cpi)

   echo "MGM CPI ID $CPI_ID"

   wait_for_cpi $MGM_RPC $CPI_ID

   CPI_CHECKSUM=$(cpi_checksum $MGM_RPC $CPI_ID)

   echo "MGM CPI Checksum $CPI_CHECKSUM"

   # Create MGM VNode

   echo Create virtual node for MGM

   MGM_HOLDING_ID_SHORT_HASH=$(create_vnode $MGM_RPC $CPI_CHECKSUM  $MGM_X500_NAME)

   echo "MGM Holding Id Short Hash $MGM_HOLDING_ID_SHORT_HASH"

   MGM_SESSION_KEY_ID=$(assign_hsm_and_generate_session_key_pair $MGM_RPC $MGM_HOLDING_ID_SHORT_HASH)
   echo MGM Session Key Id: $MGM_SESSION_KEY_ID

   ECDH_KEY_ID=$(assign_hsm_and_generate_edch_key_pair $MGM_RPC $MGM_HOLDING_ID_SHORT_HASH)
   echo ECDH Key Id: $ECDH_KEY_ID

   # Generate Key Pair for TLS for MGM
   MGM_TLS_KEY_ID=$(assign_hsm_and_generate_tls_key_pair $MGM_RPC)
   echo MGM TLS Key Id: $MGM_TLS_KEY_ID

   echo Generate CSR for TLS Certificate

   MGM_TLS=mgm_tls
   get_csr $MGM_RPC $MGM_X500_NAME $MGM_GATEWAY_ADDRESS $MGM_TLS_KEY_ID $MGM_TLS
   sign_certificate $MGM_TLS

   echo Upload Signed TLS certificate
   upload_certificate $MGM_RPC ./ca/mgm_tls/certificate.pem

   # Prepare Registration Context
   register_mgm $MGM_RPC $MGM_HOLDING_ID_SHORT_HASH $MGM_SESSION_KEY_ID $MGM_GATEWAY_ENDPOINT $ECDH_KEY_ID

   echo Complete Network Setup
   complete_network_setup $MGM_RPC $MGM_HOLDING_ID_SHORT_HASH $MGM_SESSION_KEY_ID

   echo Extract the group policy from MGM
   extract_group_policy $MGM_RPC $MGM_HOLDING_ID_SHORT_HASH 

   echo $MGM_HOLDING_ID_SHORT_HASH > $MGM_HOLDING_ID_FILE
}

on_board_node() {
   config_gateway $1

   cp ./GroupPolicy-out.json ./GroupPolicy.json

   build_cpi ./GroupPolicy.json

   if [[ $MTLS == "Y" ]]; then
     allow_client_certificate $MGM_RPC $2 $MGM_HOLDING_ID_SHORT_HASH
   fi

   trust_cpi_keys $1

   CPI_ID=$(upload_cpi $1 ./test-cordapp-5.0.0.0-SNAPSHOT-package.cpi)

   echo "NODE $2 CPI ID $CPI_ID"

   wait_for_cpi $1 $CPI_ID

   CPI_CHECKSUM=$(cpi_checksum $1 $CPI_ID)

   NODE_HOLDING_ID_SHORT_HASH=$(create_vnode $1 $CPI_CHECKSUM $2)

   NODE_SESSION_KEY_ID=$(assign_hsm_and_generate_session_key_pair $1 $NODE_HOLDING_ID_SHORT_HASH)
   echo Node $2 Session Key Id: $NODE_SESSION_KEY_ID

   NODE_LEDGER_KEY_ID=$(assign_hsm_and_generate_ledger_key_pair $1 $NODE_HOLDING_ID_SHORT_HASH)
   echo Node $2 Ledger Key Id: $NODE_LEDGER_KEY_ID

   # Generate Key Pair for TLS for MGM
   NODE_TLS_KEY_ID=$(assign_hsm_and_generate_tls_key_pair $1)
   echo NODE $2 TLS Key Id: $NODE_TLS_KEY_ID

   get_csr $1 $2 $3 $NODE_TLS_KEY_ID $4
   sign_certificate $4

   echo Upload Signed TLS certificate
   upload_certificate $1 ./ca/$4/certificate.pem

   complete_network_setup $1 $NODE_HOLDING_ID_SHORT_HASH $NODE_SESSION_KEY_ID
   register_node $1 $NODE_HOLDING_ID_SHORT_HASH $NODE_SESSION_KEY_ID $5 $NODE_LEDGER_KEY_ID
}

ps -ef | grep port-forward | grep $A_RPC_PORT:8888 | awk '{print $2}' |  xargs kill || echo
ps -ef | grep port-forward | grep $B_RPC_PORT:8888 | awk '{print $2}' |  xargs kill || echo
ps -ef | grep port-forward | grep $MGM_RPC_PORT:8888 | awk '{print $2}' |  xargs kill || echo
kubectl port-forward --namespace $A_CLUSTER_NAMESPACE deployment/corda-rpc-worker $A_RPC_PORT:8888 &
kubectl port-forward --namespace $B_CLUSTER_NAMESPACE deployment/corda-rpc-worker $B_RPC_PORT:8888 &
kubectl port-forward --namespace $MGM_CLUSTER_NAMESPACE deployment/corda-rpc-worker $MGM_RPC_PORT:8888 &

sleep 15

build_cli_tool

# Create CA
java -jar $CA_JAR --home=./ca create-ca

# Onboard MGM

on_board_mgm

on_board_node $A_RPC $A_X500_NAME $A_GATEWAY_ADDRESS a_tls $A_GATEWAY_ENDPOINT

on_board_node $B_RPC $B_X500_NAME $B_GATEWAY_ADDRESS b_tls $B_GATEWAY_ENDPOINT
