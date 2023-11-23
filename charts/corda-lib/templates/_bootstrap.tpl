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

              JDBC_URL="jdbc:{{ include "corda.clusterDbType" . }}://{{ required "A db host is required" .Values.db.cluster.host }}:{{ include "corda.clusterDbPort" . }}/{{ include "corda.clusterDbName" . }}"

              echo 'Generating Cluster DB specification'
              mkdir /tmp/db
              java -Dpf4j.pluginsDir=/opt/override/plugins -Dlog4j2.debug=false -jar /opt/override/cli.jar database spec \
                -s "config,rbac,crypto" \
                -g "config:${DB_CLUSTER_SCHEMA},rbac:${DB_RBAC_SCHEMA},crypto:${DB_CRYPTO_SCHEMA}" \
                -u "${CLUSTER_PGUSER}" -p "${CLUSTER_PGPASSWORD}" \
                --jdbc-url "${JDBC_URL}" \
                -c -l /tmp/db

              echo 'Generating RBAC initial DB configuration'
              mkdir /tmp/rbac
              java -Dpf4j.pluginsDir=/opt/override/plugins -Dlog4j2.debug=false -jar /opt/override/cli.jar initial-config create-db-config \
                -u "${RBAC_DB_USER_USERNAME}" -p "${RBAC_DB_USER_PASSWORD}" \
                --name "corda-rbac" \
                --jdbc-url "${JDBC_URL}?currentSchema=${DB_RBAC_SCHEMA}" \
                --jdbc-pool-max-size {{ .Values.bootstrap.db.rbac.dbConnectionPool.maxSize | quote }} \
              {{- if not ( kindIs "invalid" .Values.bootstrap.db.rbac.dbConnectionPool.minSize ) }}
                --jdbc-pool-min-size {{ .Values.bootstrap.db.rbac.dbConnectionPool.minSize | quote }}
              {{- end }}
                --idle-timeout {{ .Values.bootstrap.db.rbac.dbConnectionPool.idleTimeoutSeconds | quote }} \
                --max-lifetime {{ .Values.bootstrap.db.rbac.dbConnectionPool.maxLifetimeSeconds | quote }} \
                --keepalive-time {{ .Values.bootstrap.db.rbac.dbConnectionPool.keepaliveTimeSeconds | quote }} \
                --validation-timeout {{ .Values.bootstrap.db.rbac.dbConnectionPool.validationTimeoutSeconds | quote }} \
              {{- if (((.Values).config).vault).url }}
                -t "VAULT" --vault-path "dbsecrets" --key "rbac-db-password" \
              {{- else }}
                --salt "${SALT}" --passphrase "${PASSPHRASE}" \
              {{- end }}
                -l /tmp/rbac

              echo 'Generating virtual nodes initial DB configuration'
              mkdir /tmp/vnodes
              java -Dpf4j.pluginsDir=/opt/override/plugins -Dlog4j2.debug=false -jar /opt/override/cli.jar initial-config create-db-config \
                -a -u "${DB_CLUSTER_USERNAME}" -p "${DB_CLUSTER_PASSWORD}" \
                --name "corda-virtual-nodes" \
                --jdbc-url "${JDBC_URL}" \
                --jdbc-pool-max-size {{ .Values.bootstrap.db.rbac.dbConnectionPool.maxSize | quote }} \
              {{- if not ( kindIs "invalid" .Values.bootstrap.db.rbac.dbConnectionPool.minSize ) }}
                --jdbc-pool-min-size {{ .Values.bootstrap.db.rbac.dbConnectionPool.minSize | quote }}
              {{- end }}
                --idle-timeout {{ .Values.bootstrap.db.rbac.dbConnectionPool.idleTimeoutSeconds | quote }} \
                --max-lifetime {{ .Values.bootstrap.db.rbac.dbConnectionPool.maxLifetimeSeconds | quote }} \
                --keepalive-time {{ .Values.bootstrap.db.rbac.dbConnectionPool.keepaliveTimeSeconds | quote }} \
                --validation-timeout {{ .Values.bootstrap.db.rbac.dbConnectionPool.validationTimeoutSeconds | quote }} \
              {{- if (((.Values).config).vault).url }}
                -t "VAULT" --vault-path "dbsecrets" --key "vnodes-db-password" \
              {{- else }}
                --salt "${SALT}" --passphrase "${PASSPHRASE}" \
              {{- end }}
                -l /tmp/vnodes

              echo 'Generating crypto initial DB configuration'
              mkdir /tmp/crypto
              java -Dpf4j.pluginsDir=/opt/override/plugins -Dlog4j2.debug=false -jar /opt/override/cli.jar initial-config create-db-config \
                -u "${CRYPTO_DB_USER_USERNAME}" -p "${CRYPTO_DB_USER_PASSWORD}" \
                --name "corda-crypto" \
                --jdbc-url "${JDBC_URL}?currentSchema=${DB_CRYPTO_SCHEMA}" \
                --jdbc-pool-max-size {{ .Values.bootstrap.db.crypto.dbConnectionPool.maxSize | quote }} \
              {{- if not ( kindIs "invalid" .Values.bootstrap.db.crypto.dbConnectionPool.minSize ) }}
                --jdbc-pool-min-size {{ .Values.bootstrap.db.crypto.dbConnectionPool.minSize | quote }}
              {{- end }}
                --idle-timeout {{ .Values.bootstrap.db.crypto.dbConnectionPool.idleTimeoutSeconds | quote }} \
                --max-lifetime {{ .Values.bootstrap.db.crypto.dbConnectionPool.maxLifetimeSeconds | quote }} \
                --keepalive-time {{ .Values.bootstrap.db.crypto.dbConnectionPool.keepaliveTimeSeconds | quote }} \
                --validation-timeout {{ .Values.bootstrap.db.crypto.dbConnectionPool.validationTimeoutSeconds | quote }} \
              {{- if (((.Values).config).vault).url }}
                -t "VAULT" --vault-path "dbsecrets" --key "crypto-db-password" \
              {{- else }}
                --salt "${SALT}" --passphrase "${PASSPHRASE}" \
              {{- end }}
                -l /tmp/crypto

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
            - name: DB_CLUSTER_SCHEMA
              value: {{ .Values.db.cluster.schema | quote }}
            - name: DB_RBAC_SCHEMA
              value: {{ .Values.bootstrap.db.rbac.schema | quote }}
            - name: DB_CRYPTO_SCHEMA
              value: {{ .Values.bootstrap.db.crypto.schema | quote }}
            {{- include "corda.bootstrapClusterDbEnv" . | nindent 12 }}
            {{- include "corda.configSaltAndPassphraseEnv" . | nindent 12 }}
            {{- include "corda.bootstrapCliEnv" . | nindent 12 }}
            {{- include "corda.rbacDbUserEnv" . | nindent 12 }}
            {{- include "corda.clusterDbEnv" . | nindent 12 }}
            {{- include "corda.restApiAdminSecretEnv" . | nindent 12 }}
            {{- include "corda.cryptoDbUsernameEnv" . | nindent 12 }}
            {{- include "corda.cryptoDbPasswordEnv" . | nindent 12 }}
            {{- range $workerName, $authConfig := .Values.bootstrap.db.stateManager -}}
            {{-   $workerConfig := (index $.Values.workers $workerName) -}}
            {{/*  No point in trying to bootstrap the State Manager for the specific worker if the host has not been configured */}}
            {{-   if and (not $workerConfig.stateManager.db.host) (or ( $authConfig.username.value ) ( $authConfig.username.valueFrom.secretKeyRef.name ) ( $authConfig.password.value ) ( $authConfig.password.valueFrom.secretKeyRef.name ) ) -}}
            {{-     fail ( printf "Can only specify bootstrap.db.stateManager.%s when workers.%s.stateManager.host is configured" $workerName $workerName ) -}}
            {{-   else -}}
            {{-     include "corda.bootstrapStateManagerDb" ( list $ $workerName $authConfig ) }}
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

              echo 'Applying DB specification'
              export PGPASSWORD="${CLUSTER_PGPASSWORD}"
              find /tmp/db -iname "*.sql" | xargs printf -- ' -f %s' | xargs psql -v ON_ERROR_STOP=1 -h "${DB_CLUSTER_HOST}" -p "${DB_CLUSTER_PORT}" -U "${CLUSTER_PGUSER}" --dbname "${DB_CLUSTER_NAME}"

              echo 'Applying initial configurations'
              psql -v ON_ERROR_STOP=1 -h "${DB_CLUSTER_HOST}" -p "${DB_CLUSTER_PORT}" -U "${CLUSTER_PGUSER}" -f /tmp/rbac/db-config.sql -f /tmp/vnodes/db-config.sql -f /tmp/crypto/db-config.sql -f /tmp/crypto-config.sql --dbname "dbname=${DB_CLUSTER_NAME} options=--search_path=${DB_CLUSTER_SCHEMA}"

              echo 'Applying initial RBAC configuration'
              psql -v ON_ERROR_STOP=1 -h "${DB_CLUSTER_HOST}" -p "${DB_CLUSTER_PORT}" -U "${CLUSTER_PGUSER}" -f /tmp/rbac-config.sql --dbname "dbname=${DB_CLUSTER_NAME} options=--search_path=${DB_RBAC_SCHEMA}"

              echo 'Creating users and granting permissions'
              psql -v ON_ERROR_STOP=1 -h "${DB_CLUSTER_HOST}" -p "${DB_CLUSTER_PORT}" -U "${CLUSTER_PGUSER}" "${DB_CLUSTER_NAME}" << SQL
                GRANT USAGE ON SCHEMA ${DB_CLUSTER_SCHEMA} TO "${DB_CLUSTER_USERNAME}";
                GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA ${DB_CLUSTER_SCHEMA} TO "${DB_CLUSTER_USERNAME}";
                GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA ${DB_CLUSTER_SCHEMA} TO "${DB_CLUSTER_USERNAME}";
                DO \$\$ BEGIN IF EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '${RBAC_DB_USER_USERNAME}') THEN RAISE NOTICE 'Role "${RBAC_DB_USER_USERNAME}" already exists'; ELSE CREATE USER "${RBAC_DB_USER_USERNAME}" WITH ENCRYPTED PASSWORD '${RBAC_DB_USER_PASSWORD}'; END IF; END \$\$;
                GRANT USAGE ON SCHEMA ${DB_RBAC_SCHEMA} TO "$RBAC_DB_USER_USERNAME";
                GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA ${DB_RBAC_SCHEMA} TO "$RBAC_DB_USER_USERNAME";
                DO \$\$ BEGIN IF EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '${CRYPTO_DB_USER_USERNAME}') THEN RAISE NOTICE 'Role "${CRYPTO_DB_USER_USERNAME}" already exists'; ELSE CREATE USER "${CRYPTO_DB_USER_USERNAME}" WITH ENCRYPTED PASSWORD '$CRYPTO_DB_USER_PASSWORD'; END IF; END \$\$;
                GRANT USAGE ON SCHEMA ${DB_CRYPTO_SCHEMA} TO "${CRYPTO_DB_USER_USERNAME}";
                GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA ${DB_CRYPTO_SCHEMA} TO "${CRYPTO_DB_USER_USERNAME}";
              SQL

              echo 'DB Bootstrapped'
          volumeMounts:
            - mountPath: /tmp
              name: temp
          env:
            - name: DB_CLUSTER_HOST
              value: {{ required "A db host is required" .Values.db.cluster.host | quote }}
            - name: DB_CLUSTER_PORT
              value: {{ include "corda.clusterDbPort" . | quote }}
            - name: DB_CLUSTER_NAME
              value: {{ include "corda.clusterDbName" . | quote }}
            - name: DB_CLUSTER_SCHEMA
              value: {{ .Values.db.cluster.schema | quote }}
            - name: DB_RBAC_SCHEMA
              value: {{ .Values.bootstrap.db.rbac.schema | quote }}
            - name: DB_CRYPTO_SCHEMA
              value: {{ .Values.bootstrap.db.crypto.schema | quote }}
            {{- include "corda.bootstrapClusterDbEnv" . | nindent 12 }}
            {{- include "corda.rbacDbUserEnv" . | nindent 12 }}
            {{- include "corda.cryptoDbUsernameEnv" . | nindent 12 }}
            {{- include "corda.cryptoDbPasswordEnv" . | nindent 12 }}
            {{- include "corda.clusterDbEnv" . | nindent 12 }}
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
