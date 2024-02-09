{{/*
 Create the name of the service account to use for Preinstall Checks
 */}}
{{- define "corda.bootstrapPreinstallServiceAccountName" -}}
{{- if .Values.bootstrap.preinstallCheck.serviceAccount.create -}}
    {{ default (printf "%s-preinstall-service-account" (include "corda.fullname" .)) .Values.bootstrap.preinstallCheck.serviceAccount.name }}
{{- else -}}
    {{ default "default" .Values.bootstrap.preinstallCheck.serviceAccount.name }}
{{- end -}}
{{- end -}}

{{/*
Preinstall Checks
*/}}
{{- define "corda.bootstrapPreinstallJob" -}}
{{- if .Values.bootstrap.preinstallCheck.enabled }}
---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  annotations:
    "helm.sh/hook": pre-install
    "helm.sh/hook-weight": "-3"
  name: {{ include "corda.fullname" . }}-preinstall-role
rules:
- apiGroups: [""]
  resources: ["secrets"]
  verbs: ["get", "watch", "list"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  annotations:
    "helm.sh/hook": pre-install
    "helm.sh/hook-weight": "-2"
  name: {{ include "corda.fullname" . }}-preinstall-role-binding
subjects:
- kind: ServiceAccount
  name: {{ include "corda.bootstrapPreinstallServiceAccountName" . }}
roleRef:
  kind: Role
  name: {{ include "corda.fullname" . }}-preinstall-role
  apiGroup: rbac.authorization.k8s.io
{{- if .Values.bootstrap.preinstallCheck.serviceAccount.create }}
---
apiVersion: v1
kind: ServiceAccount
metadata:
  annotations:
    "helm.sh/hook": pre-install
    "helm.sh/hook-weight": "-4"
  name: {{ include "corda.bootstrapPreinstallServiceAccountName" . }}
{{- end }}
---
apiVersion: batch/v1
kind: Job
metadata:
  name: {{ include "corda.fullname" . }}-preinstall-checks
  labels:
    {{- include "corda.labels" . | nindent 4 }}
  annotations:
    "helm.sh/hook": pre-install
    "helm.sh/hook-weight": "-1"
spec:
  template:
    metadata:
      labels:
        {{- include "corda.selectorLabels" . | nindent 8 }}
        {{- include "corda.commonBootstrapPodLabels" . | nindent 8 }}
    spec:
      {{- include "corda.imagePullSecrets" . | indent 6 }}
      {{- include "corda.tolerations" . | nindent 6 }}
      serviceAccountName: {{ include "corda.bootstrapPreinstallServiceAccountName" . }}
      {{- with .Values.podSecurityContext }}
      securityContext:
        {{- . | toYaml | nindent 8 }}
      {{- end }}
      containers:
        - name: preinstall-checks
          image: {{ include "corda.bootstrapCliImage" . }}
          imagePullPolicy: {{ .Values.imagePullPolicy }}
          {{- include "corda.containerSecurityContext" . | nindent 10 }}
          {{- include "corda.bootstrapResources" . | nindent 10 }}
          args: ['preinstall', 'run-all', '/tmp/values.yaml']
          volumeMounts:
            - mountPath: /tmp
              name: temp
            {{ include "corda.log4jVolumeMount" . | nindent 12 }}
          env:
            {{ include "corda.bootstrapCliEnv" . | nindent 12 }}
      initContainers:
        - name: create-preinstall-values
          image: {{ include "corda.bootstrapCliImage" . }}
          imagePullPolicy: {{ .Values.imagePullPolicy }}
          {{- include "corda.bootstrapResources" . | nindent 10 }}
          {{- include "corda.containerSecurityContext" . | nindent 10 }}
          command:
            - /bin/bash
            - -c
          args:
            - |
                echo -e {{ toYaml .Values | quote }} > /tmp/values.yaml
          volumeMounts:
            - mountPath: /tmp
              name: temp
      volumes:
        - name: temp
          emptyDir: {}
        {{ include "corda.log4jVolume" . | nindent 8 }}
      restartPolicy: Never
      {{- include "corda.bootstrapNodeSelector" . | nindent 6 }}
  backoffLimit: 0
{{- end }}
{{- end }}

{{/*
DB bootstrap job
*/}}
{{- define "corda.bootstrapDbJob" -}}
{{- if .Values.bootstrap.db.enabled }}
---
apiVersion: batch/v1
kind: Job
metadata:
  name: {{ include "corda.fullname" . }}-setup-db
  labels:
    {{- include "corda.labels" . | nindent 4 }}
  annotations:
    "helm.sh/hook": pre-install
spec:
  template:
    metadata:
      labels:
        {{- include "corda.selectorLabels" . | nindent 8 }}
        {{- include "corda.commonBootstrapPodLabels" . | nindent 8 }}
    spec:
      {{- include "corda.imagePullSecrets" . | indent 6 }}
      {{- include "corda.tolerations" $ | indent 6 }}
      {{- include "corda.bootstrapServiceAccount" . | indent 6 }}
      {{- with .Values.podSecurityContext }}
      securityContext:
        {{- . | toYaml | nindent 8 }}
      {{- end }}
      initContainers:
        - name: generate
          image: {{ include "corda.bootstrapCliImage" . }}
          imagePullPolicy: {{ .Values.imagePullPolicy }}
          {{- include "corda.bootstrapResources" . | nindent 10 }}
          {{- include "corda.containerSecurityContext" . | nindent 10 }}
          command: [ 'sh', '-c', '-e' ]
          args:
            - |
              #!/bin/sh
              set -ev

              mkdir /tmp/db

              echo 'Generating config DB specification'
              {{- $configDbSettings := fromYaml ( include "corda.db.configuration" ( list $ $.Values.config.storageId "config.storageId" ) ) }}
              java -Dpf4j.pluginsDir=/opt/override/plugins -Dlog4j2.debug=false -jar /opt/override/cli.jar database spec \
                -s "config" \
                -g "config:${CONFIG_DB_SCHEMA}" \
                -u "${BOOTSTRAP_CONFIG_DB_USERNAME}" -p "${BOOTSTRAP_CONFIG_DB_PASSWORD}" \
                --jdbc-url {{ include "corda.db.connectionUrl" $configDbSettings | quote }} \
                -c -l /tmp/db

              echo 'Generating crypto DB specification'
              {{- $cryptoDbSettings := fromYaml ( include "corda.db.configuration" ( list $ $.Values.bootstrap.db.crypto.storageId "bootstrap.db.crypto.storageId" ) ) }}
              java -Dpf4j.pluginsDir=/opt/override/plugins -Dlog4j2.debug=false -jar /opt/override/cli.jar database spec \
                -s "crypto" \
                -g "crypto:${CRYPTO_DB_SCHEMA}" \
                -u "${BOOTSTRAP_CRYPTO_DB_USERNAME}" -p "${BOOTSTRAP_CRYPTO_DB_PASSWORD}" \
                --jdbc-url {{ include "corda.db.connectionUrl" $cryptoDbSettings | quote }} \
                -c -l /tmp/db

              echo 'Generating rbac DB specification'
              {{- $rbacDbSettings := fromYaml ( include "corda.db.configuration" ( list $ $.Values.bootstrap.db.rbac.storageId "bootstrap.db.rbac.storageId" ) ) }}
              java -Dpf4j.pluginsDir=/opt/override/plugins -Dlog4j2.debug=false -jar /opt/override/cli.jar database spec \
                -s "rbac" \
                -g "rbac:${RBAC_DB_SCHEMA}" \
                -u "${BOOTSTRAP_RBAC_DB_USERNAME}" -p "${BOOTSTRAP_RBAC_DB_PASSWORD}" \
                --jdbc-url {{ include "corda.db.connectionUrl" $rbacDbSettings | quote }} \
                -c -l /tmp/db

              echo 'Generating crypto initial DB configuration'
              mkdir /tmp/crypto
              java -Dpf4j.pluginsDir=/opt/override/plugins -Dlog4j2.debug=false -jar /opt/override/cli.jar initial-config create-db-config \
                -u "${CRYPTO_DB_USERNAME}" -p "${CRYPTO_DB_PASSWORD}" \
                --name "corda-crypto" \
                --jdbc-url {{ include "corda.db.connectionUrl" $cryptoDbSettings | quote }} \
                --jdbc-pool-max-size {{ .Values.bootstrap.db.crypto.connectionPool.maxSize | quote }} \
              {{- if not ( kindIs "invalid" .Values.bootstrap.db.crypto.connectionPool.minSize ) }}
                --jdbc-pool-min-size {{ .Values.bootstrap.db.crypto.connectionPool.minSize | quote }}
              {{- end }}
                --idle-timeout {{ .Values.bootstrap.db.crypto.connectionPool.idleTimeoutSeconds | quote }} \
                --max-lifetime {{ .Values.bootstrap.db.crypto.connectionPool.maxLifetimeSeconds | quote }} \
                --keepalive-time {{ .Values.bootstrap.db.crypto.connectionPool.keepAliveTimeSeconds | quote }} \
                --validation-timeout {{ .Values.bootstrap.db.crypto.connectionPool.validationTimeoutSeconds | quote }} \
              {{- if (((.Values).config).vault).url }}
                -t "VAULT" --vault-path "dbsecrets" --key "crypto-db-password" \
              {{- else }}
                --salt "${SALT}" --passphrase "${PASSPHRASE}" \
              {{- end }}
                -l /tmp/crypto

              echo 'Generating RBAC initial DB configuration'
              mkdir /tmp/rbac
              java -Dpf4j.pluginsDir=/opt/override/plugins -Dlog4j2.debug=false -jar /opt/override/cli.jar initial-config create-db-config \
                -u "${RBAC_DB_USERNAME}" -p "${RBAC_DB_PASSWORD}" \
                --name "corda-rbac" \
                --jdbc-url {{ include "corda.db.connectionUrl" $rbacDbSettings | quote }} \
                --jdbc-pool-max-size {{ .Values.bootstrap.db.rbac.connectionPool.maxSize | quote }} \
              {{- if not ( kindIs "invalid" .Values.bootstrap.db.rbac.connectionPool.minSize ) }}
                --jdbc-pool-min-size {{ .Values.bootstrap.db.rbac.connectionPool.minSize | quote }}
              {{- end }}
                --idle-timeout {{ .Values.bootstrap.db.rbac.connectionPool.idleTimeoutSeconds | quote }} \
                --max-lifetime {{ .Values.bootstrap.db.rbac.connectionPool.maxLifetimeSeconds | quote }} \
                --keepalive-time {{ .Values.bootstrap.db.rbac.connectionPool.keepAliveTimeSeconds | quote }} \
                --validation-timeout {{ .Values.bootstrap.db.rbac.connectionPool.validationTimeoutSeconds | quote }} \
              {{- if (((.Values).config).vault).url }}
                -t "VAULT" --vault-path "dbsecrets" --key "rbac-db-password" \
              {{- else }}
                --salt "${SALT}" --passphrase "${PASSPHRASE}" \
              {{- end }}
                -l /tmp/rbac

              echo 'Generating virtual nodes initial DB configuration'
              mkdir /tmp/vnodes
              {{- $virtualNodesDbSettings := fromYaml ( include "corda.db.configuration" ( list $ $.Values.bootstrap.db.virtualNodes.storageId "bootstrap.db.virtualNodes.storageId" ) ) }}
              java -Dpf4j.pluginsDir=/opt/override/plugins -Dlog4j2.debug=false -jar /opt/override/cli.jar initial-config create-db-config \
                -a -u "${VIRTUAL_NODES_DB_USERNAME}" -p "${VIRTUAL_NODES_DB_PASSWORD}" \
                --name "corda-virtual-nodes" \
                --jdbc-url {{ include "corda.db.connectionUrl" $virtualNodesDbSettings | quote }} \
                --jdbc-pool-max-size {{ .Values.bootstrap.db.virtualNodes.connectionPool.maxSize | quote }} \
              {{- if not ( kindIs "invalid" .Values.bootstrap.db.virtualNodes.connectionPool.minSize ) }}
                --jdbc-pool-min-size {{ .Values.bootstrap.db.virtualNodes.connectionPool.minSize | quote }}
              {{- end }}
                --idle-timeout {{ .Values.bootstrap.db.virtualNodes.connectionPool.idleTimeoutSeconds | quote }} \
                --max-lifetime {{ .Values.bootstrap.db.virtualNodes.connectionPool.maxLifetimeSeconds | quote }} \
                --keepalive-time {{ .Values.bootstrap.db.virtualNodes.connectionPool.keepAliveTimeSeconds | quote }} \
                --validation-timeout {{ .Values.bootstrap.db.virtualNodes.connectionPool.validationTimeoutSeconds | quote }} \
              {{- if (((.Values).config).vault).url }}
                -t "VAULT" --vault-path "dbsecrets" --key "vnodes-db-password" \
              {{- else }}
                --salt "${SALT}" --passphrase "${PASSPHRASE}" \
              {{- end }}
                -l /tmp/vnodes

              echo 'Generating REST API user initial configuration'
              java -Dpf4j.pluginsDir=/opt/override/plugins -Dlog4j2.debug=false -jar /opt/override/cli.jar initial-config create-user-config \
                -u "${REST_API_ADMIN_USERNAME}" -p "${REST_API_ADMIN_PASSWORD}" \
                -l /tmp

              echo 'Generating crypto initial configuration'
              java -Dpf4j.pluginsDir=/opt/override/plugins -Dlog4j2.debug=false -jar /opt/override/cli.jar initial-config create-crypto-config \
                --salt "${SALT}" --passphrase "${PASSPHRASE}" \
              {{- if (((.Values).config).vault).url }}
                -t "VAULT" --vault-path "cryptosecrets" -n 2 -ks "salt" -kp "passphrase" -ks "salt2" -kp "passphrase2" \
              {{- end }}
                -l /tmp
          workingDir: /tmp
          volumeMounts:
            - mountPath: /tmp
              name: temp
            {{- include "corda.log4jVolumeMount" . | nindent 12 }}
          env:
            - name: CONFIG_DB_SCHEMA
              value: {{ .Values.config.partition | quote }}
            - name: RBAC_DB_SCHEMA
              value: {{ .Values.bootstrap.db.rbac.partition | quote }}
            - name: CRYPTO_DB_SCHEMA
              value: {{ .Values.bootstrap.db.crypto.partition | quote }}
            {{- include "corda.configSaltAndPassphraseEnv" . | nindent 12 }}
            {{- include "corda.bootstrapCliEnv" . | nindent 12 }}
            {{- $configBootstrapSettings := fromYaml ( include "corda.db.bootstrapConfiguration" ( list $ $.Values.config.storageId "config.storageId" ) ) -}}
            {{- include "corda.db.bootstrapEnvironment" ( list $ "config" $.Values.config.storageId $configBootstrapSettings ) | nindent 12 }}
            {{- $cryptoBootstrapSettings := fromYaml ( include "corda.db.bootstrapConfiguration" ( list $ $.Values.bootstrap.db.crypto.storageId "bootstrap.db.crypto.storageId" ) ) -}}
            {{- include "corda.db.bootstrapEnvironment" ( list $ "crypto" $.Values.bootstrap.db.crypto.storageId $cryptoBootstrapSettings ) | nindent 12 }}
            {{- $rbacBootstrapSettings := fromYaml ( include "corda.db.bootstrapConfiguration" ( list $ $.Values.bootstrap.db.rbac.storageId "bootstrap.db.rbac.storageId" ) ) -}}
            {{- include "corda.db.bootstrapEnvironment" ( list $ "rbac" $.Values.bootstrap.db.rbac.storageId $rbacBootstrapSettings ) | nindent 12 }}
            {{- include "corda.db.runtimeEnvironment" ( list $ "crypto" $.Values.bootstrap.db.crypto ) | nindent 12 }}
            {{- include "corda.db.runtimeEnvironment" ( list $ "rbac" $.Values.bootstrap.db.rbac ) | nindent 12 }}
            {{- include "corda.db.runtimeEnvironment" ( list $ "virtualNodes" $.Values.bootstrap.db.virtualNodes ) | nindent 12 }}
            {{- include "corda.restApiAdminSecretEnv" . | nindent 12 }}
            {{/* Bootstrap State Manager Databases */}}
            {{- range $stateType, $stateTypeConfig  := .Values.stateManager -}}
            {{-   $storageId := $stateTypeConfig.storageId -}}
            {{-   $storagePartition := $stateTypeConfig.partition -}}
            {{-   $connectionSettings := fromYaml ( include "corda.db.configuration" ( list $ $storageId ( printf "stateManager.%s.storageId" $stateType ) ) ) -}}
            {{/*  -- Check whether bootstrap is enabled for the database storage associated to the state type */}}
            {{-   range $bootCredentials := $.Values.bootstrap.db.databases -}}
            {{-     if eq .id $storageId -}}
            {{/*        -- The database storage associated to the state type exists and is configured to be included within the bootstrap process */}}
            {{-         range $workerName, $workerConfig := $.Values.workers -}}
            {{-           $stateManagerSettings := ( index $workerConfig "stateManager" ) -}}
            {{/*          -- State Manager configured for the worker, generate the required database boostrap template */}}
            {{-           if and $stateManagerSettings ( index $stateManagerSettings $stateType ) -}}
            {{-             include "corda.sm.db.bootstrapContainers" ( list $ $stateType $workerName $storagePartition $connectionSettings ( index $stateManagerSettings $stateType ) $bootCredentials ) -}}
            {{-           end -}}
            {{-         end -}}
            {{-     end -}}
            {{-   end -}}
            {{- end }}
      containers:
        - name: apply
          image: {{ include "corda.bootstrapDbClientImage" . }}
          imagePullPolicy: {{ .Values.imagePullPolicy }}
          {{- include "corda.bootstrapResources" . | nindent 10 }}
          {{- include "corda.containerSecurityContext" . | nindent 10 }}
          command: [ 'sh', '-c', '-e' ]
          args:
            - |
              #!/bin/sh
              set -ev

              echo 'Applying config DB specification'
              export PGPASSWORD="${BOOTSTRAP_CONFIG_DB_PASSWORD}"
              export PGUSER="${BOOTSTRAP_CONFIG_DB_USERNAME}"
              psql -v ON_ERROR_STOP=1 -h "${CONFIG_DB_HOST}" -p "${CONFIG_DB_PORT}" --dbname "${CONFIG_DB_NAME}" -f /tmp/db/config.sql

              echo 'Applying initial configurations'
              (echo "SET search_path TO ${CONFIG_DB_SCHEMA};";
              cat /tmp/rbac/db-config.sql;
              cat /tmp/vnodes/db-config.sql;
              cat /tmp/crypto/db-config.sql;
              cat /tmp/crypto-config.sql) | psql -v ON_ERROR_STOP=1 \
              -h "${CONFIG_DB_HOST}" -p "${CONFIG_DB_PORT}" --dbname "${CONFIG_DB_NAME}"

              echo 'Creating config users and granting permissions'
              psql -v ON_ERROR_STOP=1 -h "${CONFIG_DB_HOST}" -p "${CONFIG_DB_PORT}" --dbname "${CONFIG_DB_NAME}" << SQL
{{- range $workerName, $workerValues := $.Values.workers }}
{{-   if $workerValues.config }}
{{-     $configValues := $workerValues.config }}
{{-     $usernameEnv := printf "CONFIG_%s_DB_USERNAME" ( upper ( snakecase $workerName ) ) }}
{{-     $passwordEnv := printf "CONFIG_%s_DB_PASSWORD" ( upper ( snakecase $workerName ) ) }}
                DO \$\$ BEGIN IF EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '${{ $usernameEnv }}') THEN RAISE NOTICE 'Role "${{ $usernameEnv }}" already exists'; ELSE CREATE USER "${{ $usernameEnv }}" WITH ENCRYPTED PASSWORD '${{ $passwordEnv }}'; END IF; END \$\$;
                GRANT USAGE ON SCHEMA ${CONFIG_DB_SCHEMA} TO "${{ $usernameEnv }}";
                GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA ${CONFIG_DB_SCHEMA} TO "${{ $usernameEnv }}";
                GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA ${CONFIG_DB_SCHEMA} TO "${{ $usernameEnv }}";
                ALTER ROLE "${{ $usernameEnv }}" SET search_path TO ${CONFIG_DB_SCHEMA};
{{-   end }}
{{- end }}
              SQL

              echo 'Applying crypto DB specification'
              export PGPASSWORD="${BOOTSTRAP_CRYPTO_DB_PASSWORD}"
              export PGUSER="${BOOTSTRAP_CRYPTO_DB_USERNAME}"
              psql -v ON_ERROR_STOP=1 -h "${CRYPTO_DB_HOST}" -p "${CRYPTO_DB_PORT}" --dbname "${CRYPTO_DB_NAME}" -f /tmp/db/crypto.sql

              echo 'Creating crypto user and granting permissions'
              psql -v ON_ERROR_STOP=1 -h "${CRYPTO_DB_HOST}" -p "${CRYPTO_DB_PORT}" --dbname "${CRYPTO_DB_NAME}" << SQL
                DO \$\$ BEGIN IF EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '${CRYPTO_DB_USERNAME}') THEN RAISE NOTICE 'Role "${CRYPTO_DB_USERNAME}" already exists'; ELSE CREATE USER "${CRYPTO_DB_USERNAME}" WITH ENCRYPTED PASSWORD '$CRYPTO_DB_PASSWORD'; END IF; END \$\$;
                GRANT USAGE ON SCHEMA ${CRYPTO_DB_SCHEMA} TO "${CRYPTO_DB_USERNAME}";
                GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA ${CRYPTO_DB_SCHEMA} TO "${CRYPTO_DB_USERNAME}";
                ALTER ROLE "${CRYPTO_DB_USERNAME}" SET search_path TO ${CRYPTO_DB_SCHEMA};
              SQL

              echo 'Applying RBAC DB specification'
              export PGPASSWORD="${BOOTSTRAP_RBAC_DB_PASSWORD}"
              export PGUSER="${BOOTSTRAP_RBAC_DB_USERNAME}"
              psql -v ON_ERROR_STOP=1 -h "${RBAC_DB_HOST}" -p "${RBAC_DB_PORT}" --dbname "${RBAC_DB_NAME}" -f /tmp/db/rbac.sql

              echo 'Applying initial RBAC configuration'
              (echo "SET search_path TO ${RBAC_DB_SCHEMA};";
              cat  /tmp/rbac-config.sql) | psql -v ON_ERROR_STOP=1 \
                -h "${RBAC_DB_HOST}" -p "${RBAC_DB_PORT}" --dbname "${RBAC_DB_NAME}"

              echo 'Creating RBAC user and granting permissions'
              psql -v ON_ERROR_STOP=1 -h "${RBAC_DB_HOST}" -p "${RBAC_DB_PORT}" --dbname "${RBAC_DB_NAME}" << SQL
                DO \$\$ BEGIN IF EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '${RBAC_DB_USERNAME}') THEN RAISE NOTICE 'Role "${RBAC_DB_USERNAME}" already exists'; ELSE CREATE USER "${RBAC_DB_USERNAME}" WITH ENCRYPTED PASSWORD '${RBAC_DB_PASSWORD}'; END IF; END \$\$;
                GRANT USAGE ON SCHEMA ${RBAC_DB_SCHEMA} TO "$RBAC_DB_USERNAME";
                GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA ${RBAC_DB_SCHEMA} TO "$RBAC_DB_USERNAME";
                ALTER ROLE "${RBAC_DB_USERNAME}" SET search_path TO ${RBAC_DB_SCHEMA};
              SQL

              echo 'Creating virtual nodes user and granting permissions'
              psql -v ON_ERROR_STOP=1 -h "${VIRTUAL_NODES_DB_HOST}" -p "${VIRTUAL_NODES_DB_PORT}" --dbname "${VIRTUAL_NODES_DB_NAME}" << SQL
                DO \$\$ BEGIN IF EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '${VIRTUAL_NODES_DB_USERNAME}') THEN RAISE NOTICE 'Role "${VIRTUAL_NODES_DB_USERNAME}" already exists'; ELSE CREATE USER "${VIRTUAL_NODES_DB_USERNAME}" WITH NOSUPERUSER CREATEROLE ENCRYPTED PASSWORD '${VIRTUAL_NODES_DB_PASSWORD}'; END IF; END \$\$;
                GRANT CREATE ON DATABASE ${VIRTUAL_NODES_DB_NAME} TO "${VIRTUAL_NODES_DB_USERNAME}"
              SQL

              echo 'DBs Bootstrapped'
          volumeMounts:
            - mountPath: /tmp
              name: temp
          env:
            - name: CONFIG_DB_HOST
              value: {{ required "A db host is required" $configDbSettings.host | quote }}
            - name: CONFIG_DB_PORT
              value: {{ $configDbSettings.port | quote }}
            - name: CONFIG_DB_NAME
              value: {{ $configDbSettings.name | quote }}
            - name: CONFIG_DB_SCHEMA
              value: {{ .Values.config.partition | quote }}
            - name: CRYPTO_DB_HOST
              value: {{ required "A crypto DB host is required" $cryptoDbSettings.host | quote }}
            - name: CRYPTO_DB_PORT
              value: {{ $cryptoDbSettings.port | quote }}
            - name: CRYPTO_DB_NAME
              value: {{ $cryptoDbSettings.name | quote }}
            - name: CRYPTO_DB_SCHEMA
              value: {{ .Values.bootstrap.db.crypto.partition | quote }}
            - name: RBAC_DB_HOST
              value: {{ required "An RBAC DB host is required" $rbacDbSettings.host | quote }}
            - name: RBAC_DB_PORT
              value: {{ $rbacDbSettings.port | quote }}
            - name: RBAC_DB_NAME
              value: {{ $rbacDbSettings.name | quote }}
            - name: RBAC_DB_SCHEMA
              value: {{ .Values.bootstrap.db.rbac.partition | quote }}
            - name: VIRTUAL_NODES_DB_HOST
              value: {{ required "A virtual nodes DB host is required" $virtualNodesDbSettings.host | quote }}
            - name: VIRTUAL_NODES_DB_PORT
              value: {{ $virtualNodesDbSettings.port | quote }}
            - name: VIRTUAL_NODES_DB_NAME
              value: {{ $virtualNodesDbSettings.name | quote }}
            {{- include "corda.db.bootstrapEnvironment" ( list $ "config" $.Values.config.storageId $configBootstrapSettings ) | nindent 12 }}
            {{- include "corda.db.bootstrapEnvironment" ( list $ "crypto" $.Values.bootstrap.db.crypto.storageId $cryptoBootstrapSettings ) | nindent 12 }}
            {{- include "corda.db.bootstrapEnvironment" ( list $ "rbac" $.Values.bootstrap.db.rbac.storageId $rbacBootstrapSettings ) | nindent 12 }}
            {{- include "corda.db.runtimeConfigEnvironment" ( list $ ) | nindent 12 }}
            {{- include "corda.db.runtimeEnvironment" ( list $ "crypto" $.Values.bootstrap.db.crypto ) | nindent 12 }}
            {{- include "corda.db.runtimeEnvironment" ( list $ "rbac" $.Values.bootstrap.db.rbac ) | nindent 12 }}
            {{- include "corda.db.runtimeEnvironment" ( list $ "virtualNodes" $.Values.bootstrap.db.virtualNodes ) | nindent 12 }}
      volumes:
        - name: temp
          emptyDir: {}
        {{- include "corda.log4jVolume" . | nindent 8 }}
      {{- include "corda.bootstrapNodeSelector" . | indent 6 }}
      restartPolicy: Never
  backoffLimit: 0
{{- end }}
{{- end }}

{{/*
Kafka bootstrap job
*/}}
{{- define "corda.bootstrapKafkaJob" -}}
{{- if .Values.bootstrap.kafka.enabled }}
---
apiVersion: batch/v1
kind: Job
metadata:
  name: {{ include "corda.fullname" . }}-create-topics
  labels:
    {{- include "corda.labels" . | nindent 4 }}
  annotations:
    "helm.sh/hook": pre-install
spec:
  template:
    metadata:
      labels:
        {{- include "corda.selectorLabels" . | nindent 8 }}
        {{- include "corda.commonBootstrapPodLabels" . | nindent 8 }}
    spec:
      {{- include "corda.imagePullSecrets" . | indent 6 }}
      {{- include "corda.tolerations" . | indent 6 }}
      {{- include "corda.bootstrapServiceAccount" . | indent 6 }}
      {{- with .Values.podSecurityContext }}
      securityContext:
        {{- . | toYaml | nindent 8 }}
      {{- end }}
      containers:
        - name: create-topics
          image: {{ include "corda.bootstrapCliImage" . }}
          imagePullPolicy: {{ .Values.imagePullPolicy }}
          {{- include "corda.containerSecurityContext" . | nindent 10 }}
          {{- include "corda.bootstrapResources" . | nindent 10 }}
          args: [
            'topic',
            '-b', '{{ include "corda.kafkaBootstrapServers" . }}',
            '-k', '/tmp/config.properties',
            {{- if .Values.kafka.topicPrefix }}
            '-n', '{{ .Values.kafka.topicPrefix }}',
            {{- end }}
            'create',
            {{- if .Values.kafka.sasl.enabled }}
              {{- range $k, $v := .Values.workers }}
            '-u', '{{ printf "%s=$(KAFKA_SASL_USERNAME_%s)" $k ( include "corda.workerTypeUpperSnakeCase" $k ) }}',
              {{- end }}
            {{- end }}
            '-r', '{{ .Values.bootstrap.kafka.replicas }}',
            '-p', '{{ .Values.bootstrap.kafka.partitions }}',
            {{- if .Values.bootstrap.kafka.overrides }}
            '-o', '/tmp/overrides.yaml',
            {{- end }}
            'connect',
            '-w', '{{ .Values.bootstrap.kafka.timeoutSeconds }}'
          ]
          volumeMounts:
            - mountPath: /tmp
              name: temp
            {{- if and .Values.kafka.tls.enabled .Values.kafka.tls.truststore.valueFrom.secretKeyRef.name }}
            - mountPath: "/certs"
              name: certs
              readOnly: true
            {{- end }}
            {{- include "corda.log4jVolumeMount" . | nindent 12 }}
          env:
            {{- include "corda.bootstrapCliEnv" . | nindent 12 }}
            {{- if .Values.kafka.sasl.enabled }}
              {{- range $k, $v := .Values.workers }}
            - name: {{ ( printf "KAFKA_SASL_USERNAME_%s" ( include "corda.workerTypeUpperSnakeCase" $k )) | quote }}
                {{- if $v.kafka.sasl.username.valueFrom.secretKeyRef.name }}
              valueFrom:
                secretKeyRef:
                  name: {{ $v.kafka.sasl.username.valueFrom.secretKeyRef.name | quote }}
                  key: {{ required (printf "Must specify workers.%s.kafka.sasl.username.valueFrom.secretKeyRef.key" $k) $v.kafka.sasl.username.valueFrom.secretKeyRef.key | quote }}
                {{- else if $v.kafka.sasl.username.value }}
              value: {{ $v.kafka.sasl.username.value | quote }}
                {{- else if $.Values.kafka.sasl.username.valueFrom.secretKeyRef.name }}
              valueFrom:
                secretKeyRef:
                  name: {{ $.Values.kafka.sasl.username.valueFrom.secretKeyRef.name | quote }}
                  key: {{ required "Must specify kafka.sasl.username.valueFrom.secretKeyRef.key" $.Values.kafka.sasl.username.valueFrom.secretKeyRef.key | quote }}
                {{- else }}
              value: {{ required (printf "Must specify workers.%s.kafka.sasl.username.value, workers.%s.kafka.sasl.username.valueFrom.secretKeyRef.name, kafka.sasl.username.value, or kafka.sasl.username.valueFrom.secretKeyRef.name" $k $k) $.Values.kafka.sasl.username.value | quote }}
                {{- end }}
              {{- end }}
            {{- end }}
      initContainers:
        - name: setup
          image: {{ include "corda.bootstrapCliImage" . }}
          imagePullPolicy: {{ .Values.imagePullPolicy }}
          {{- include "corda.bootstrapResources" . | nindent 10 }}
          {{- include "corda.containerSecurityContext" . | nindent 10 }}
          env:
          {{- include "corda.bootstrapKafkaSaslUsernameAndPasswordEnv" . | indent 12 }}
          {{- include "corda.kafkaTlsPassword" . | indent 12 }}
          command:
            - /bin/bash
            - -c
          args:
            - |
                {{- with .Values.bootstrap.kafka.overrides }}
                echo -e {{ toYaml . | quote }} > /tmp/overrides.yaml
                {{- end }}
                touch /tmp/config.properties
                {{- if .Values.kafka.tls.enabled }}
                {{- if .Values.kafka.sasl.enabled }}
                echo "security.protocol=SASL_SSL\n" >> /tmp/config.properties
                echo "sasl.mechanism={{ .Values.kafka.sasl.mechanism }}\n" >> /tmp/config.properties
                {{- if eq .Values.kafka.sasl.mechanism "PLAIN" }}
                echo "sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required username=\"$SASL_USERNAME\" password=\"$SASL_PASSWORD\" ;\n">> /tmp/config.properties
                {{- else }}
                echo "sasl.jaas.config=org.apache.kafka.common.security.scram.ScramLoginModule required username=\"$SASL_USERNAME\" password=\"$SASL_PASSWORD\" ;\n">> /tmp/config.properties
                {{- end }}
                {{- else }}
                echo "security.protocol=SSL\n" >> /tmp/config.properties
                {{- end }}
                {{- if .Values.kafka.tls.truststore.valueFrom.secretKeyRef.name }}
                echo "ssl.truststore.location=/certs/ca.crt\n" >> /tmp/config.properties
                echo "ssl.truststore.type={{ .Values.kafka.tls.truststore.type | upper }}\n" >> /tmp/config.properties
                {{- if or .Values.kafka.tls.truststore.password.value .Values.kafka.tls.truststore.password.valueFrom.secretKeyRef.name }}
                echo "ssl.truststore.password=\"$TRUSTSTORE_PASSWORD\"\n" >> /tmp/config.properties
                {{- end }}
                {{- end }}
                {{- else }}
                {{- if .Values.kafka.sasl.enabled }}
                echo "security.protocol=SASL_PLAINTEXT\n" >> /tmp/config.properties
                echo "sasl.mechanism={{ .Values.kafka.sasl.mechanism }}\n" >> /tmp/config.properties
                echo "sasl.jaas.config=org.apache.kafka.common.security.scram.ScramLoginModule required username=\"$SASL_USERNAME\" password=\"$SASL_PASSWORD\" ;\n">> /tmp/config.properties
                {{- end }}
                {{- end }}
          volumeMounts:
            - mountPath: /tmp
              name: temp
      volumes:
        - name: temp
          emptyDir: {}
        {{- if and .Values.kafka.tls.enabled .Values.kafka.tls.truststore.valueFrom.secretKeyRef.name }}
        - name: certs
          secret:
            secretName: {{ .Values.kafka.tls.truststore.valueFrom.secretKeyRef.name | quote }}
            items:
              - key: {{ .Values.kafka.tls.truststore.valueFrom.secretKeyRef.key | quote }}
                path: ca.crt
        {{- end }}
        {{- include "corda.log4jVolume" . | nindent 8 }}
      restartPolicy: Never
      {{- include "corda.bootstrapNodeSelector" . | indent 6 }}
  backoffLimit: 0
{{- end }}
{{- end }}

{{/*
RBAC bootstrap job
*/}}
{{- define "corda.bootstrapRbacJob" -}}
{{- if .Values.bootstrap.rbac.enabled }}
---
apiVersion: batch/v1
kind: Job
metadata:
  name: {{ include "corda.fullname" . }}-setup-rbac
  labels:
    {{- include "corda.labels" . | nindent 4 }}
  annotations:
    "helm.sh/hook": post-install
spec:
  template:
    metadata:
      labels:
        {{- include "corda.selectorLabels" . | nindent 8 }}
        {{- include "corda.commonBootstrapPodLabels" . | nindent 8 }}
    spec:
      {{- include "corda.imagePullSecrets" . | indent 6 }}
      {{- include "corda.tolerations" . | indent 6 }}
      {{- include "corda.bootstrapServiceAccount" . | indent 6 }}
      {{- with .Values.podSecurityContext }}
      securityContext:
        {{- . | toYaml | nindent 8 }}
      {{- end }}
      containers:
        - name: create-rbac-role-user-admin
          image: {{ include "corda.bootstrapCliImage" . }}
          imagePullPolicy: {{ .Values.imagePullPolicy }}
          {{- include "corda.bootstrapResources" . | nindent 10 }}
          {{- include "corda.containerSecurityContext" . | nindent 10 }}
          args: ['initial-rbac', 'all-cluster-roles', '--yield', '300', '--user', "$(REST_API_ADMIN_USERNAME)",
            '--password', "$(REST_API_ADMIN_PASSWORD)",
            '--target', "https://{{ include "corda.fullname" . }}-rest-worker:443", '--insecure']
          volumeMounts:
            - mountPath: /tmp
              name: temp
            {{- include "corda.log4jVolumeMount" . | nindent 12 }}
          env:
            {{- include "corda.restApiAdminSecretEnv" . | nindent 12 }}
            {{- include "corda.bootstrapCliEnv" . | nindent 12 }}
      {{- include "corda.bootstrapNodeSelector" . | indent 6 }}
      volumes:
        - name: temp
          emptyDir: {}
        {{- include "corda.log4jVolume" . | nindent 8 }}
      restartPolicy: Never
  backoffLimit: 0
{{- end }}
{{- end }}

{{/*
Bootstrap CLI image
*/}}
{{- define "corda.bootstrapCliImage" -}}
{{- printf "%s/%s:%s" ( .Values.bootstrap.image.registry | default .Values.image.registry ) ( .Values.bootstrap.image.repository ) ( .Values.bootstrap.image.tag | default .Values.image.tag | default .Chart.AppVersion ) | quote }}
{{- end }}

{{/*
Bootstrap DB client image
*/}}
{{- define "corda.bootstrapDbClientImage" -}}
"{{- if .Values.bootstrap.db.clientImage.registry }}{{.Values.bootstrap.db.clientImage.registry}}/{{- end }}{{ .Values.bootstrap.db.clientImage.repository }}:{{ .Values.bootstrap.db.clientImage.tag }}"
{{- end }}

{{/*
Bootstrap resources
*/}}
{{- define "corda.bootstrapResources" -}}
resources:
  requests:
  {{- with .Values.bootstrap.resources.requests.cpu }}
    cpu: {{ . }}
  {{- end }}
  {{- with .Values.bootstrap.resources.requests.memory }}
    memory: {{ . }}
  {{- end}}
  limits:
  {{- with .Values.bootstrap.resources.limits.cpu }}
    cpu: {{ . }}
  {{- end }}
  {{- with .Values.bootstrap.resources.limits.memory }}
    memory: {{ . }}
  {{- end }}
{{- end }}

{{/*
Bootstrap node selector
*/}}
{{- define "corda.bootstrapNodeSelector" -}}
{{- with .Values.bootstrap.nodeSelector | default .Values.nodeSelector }}
nodeSelector:
  {{- toYaml . | nindent 2 }}
{{- end }}
{{- end }}

{{/*
Bootstrap service account
*/}}
{{- define "corda.bootstrapServiceAccount" -}}
{{- with .Values.bootstrap.serviceAccount.name | default .Values.serviceAccount.name }}
serviceAccountName: {{ . }}
{{- end }}
{{- end }}

{{/*
Bootstrap Corda CLI environment variables
*/}}
{{- define "corda.bootstrapCliEnv" -}}
- name: JAVA_TOOL_OPTIONS
  value: "-Dlog4j2.configurationFile=/etc/log4j/log4j2.xml"
- name: CONSOLE_LOG_FORMAT
  value: {{ .Values.logging.format }}
- name: CONSOLE_LOG_LEVEL
  value: {{ .Values.logging.level }}
- name: CORDA_CLI_HOME_DIR
  value: "/tmp"
{{- end }}
