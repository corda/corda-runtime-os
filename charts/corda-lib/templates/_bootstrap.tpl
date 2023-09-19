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
      {{- include "corda.imagePullSecrets" . | nindent 6 }}
      {{- include "corda.tolerations" . | nindent 6 }}
      serviceAccountName: {{ include "corda.bootstrapPreinstallServiceAccountName" . }}
      {{- with .Values.podSecurityContext }}
      securityContext:
        {{ . | toYaml | nindent 8 }}
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
      {{- include "corda.imagePullSecrets" . | nindent 6 }}
      {{- include "corda.tolerations" $ | nindent 6 }}
      {{- include "corda.bootstrapServiceAccount" . | nindent 6 }}
      {{- with .Values.podSecurityContext }}
      securityContext:
        {{ . | toYaml | nindent 8 }}
      {{- end }}
      containers:
        - name: fin
          image: {{ include "corda.bootstrapCliImage" . }}
          imagePullPolicy: {{ .Values.imagePullPolicy }}
          {{- include "corda.bootstrapResources" . | nindent 10 }}
          {{- include "corda.containerSecurityContext" . | nindent 10 }}
          command:
            - /bin/bash
            - -e
            - -c
          args: ["echo", "'DB Bootstrapped'"]
          workingDir: /tmp
          volumeMounts:
            - mountPath: /tmp
              name: temp
      initContainers:
        {{- include "corda.generateAndExecuteSql" ( dict "name" "db" "Values" .Values "Chart" .Chart "Release" .Release "schema" "RBAC" "namePostfix" "schemas" "sequenceNumber" 1) | nindent 8 }}
        {{- include "corda.generateAndExecuteSql" ( dict "name" "rbac" "Values" .Values "Chart" .Chart "Release" .Release "environmentVariablePrefix" "RBAC_DB_USER" "schema" "RBAC" "sequenceNumber" 3) | nindent 8 }}
        {{- include "corda.generateAndExecuteSql" ( dict "name" "vnodes" "longName" "virtual-nodes" "dbName" "rbac" "admin" "true" "Values" .Values "Chart" .Chart "Release" .Release "environmentVariablePrefix" "DB_CLUSTER" "sequenceNumber" 5) | nindent 8 }}
        {{- include "corda.generateAndExecuteSql" ( dict "name" "crypto" "Values" .Values "Chart" .Chart "Release" .Release "environmentVariablePrefix" "CRYPTO_DB_USER"  "schema" "CRYPTO" "sequenceNumber" 7) | nindent 8 }}
        {{- include "corda.generateAndExecuteSql" ( dict "name" "rest"  "Values" .Values "Chart" .Chart "Release" .Release "environmentVariablePrefix" "REST_API_ADMIN"  "schema" "RBAC"  "searchPath" "RBAC" "subCommand" "create-user-config" "namePostfix" "admin" "sqlFile" "rbac-config.sql" "sequenceNumber" 9) | nindent 8 }}
        - name: 11-create-db-users-and-grant
          image: {{ include "corda.bootstrapDbClientImage" . }}
          imagePullPolicy: {{ .Values.imagePullPolicy }}
          {{- include "corda.bootstrapResources" . | nindent 10 }}
          {{- include "corda.containerSecurityContext" . | nindent 10 }}
          command: [ '/bin/bash', '-e', '-c' ]
          args:
            - |
              psql -v ON_ERROR_STOP=1 -h {{ required "A db host is required" .Values.db.cluster.host }} -p {{ include "corda.clusterDbPort" . }} {{ include "corda.clusterDbName" . }} << SQL
                GRANT USAGE ON SCHEMA {{ .Values.db.cluster.schema }} TO "$DB_CLUSTER_USERNAME";
                GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA {{ .Values.db.cluster.schema }} TO "$DB_CLUSTER_USERNAME";
                GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA {{ .Values.db.cluster.schema }} TO "$DB_CLUSTER_USERNAME";
                DO \$\$ BEGIN IF EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '$RBAC_DB_USER_USERNAME') THEN RAISE NOTICE 'Role "$RBAC_DB_USER_USERNAME" already exists'; ELSE CREATE USER "$RBAC_DB_USER_USERNAME" WITH ENCRYPTED PASSWORD '$RBAC_DB_USER_PASSWORD'; END IF; END \$\$;
                GRANT USAGE ON SCHEMA {{ .Values.bootstrap.db.rbac.schema }} TO "$RBAC_DB_USER_USERNAME";
                GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA {{ .Values.bootstrap.db.rbac.schema }} TO "$RBAC_DB_USER_USERNAME";
                DO \$\$ BEGIN IF EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '$CRYPTO_DB_USER_USERNAME') THEN RAISE NOTICE 'Role "$CRYPTO_DB_USER_USERNAME" already exists'; ELSE CREATE USER "$CRYPTO_DB_USER_USERNAME" WITH ENCRYPTED PASSWORD '$CRYPTO_DB_USER_PASSWORD'; END IF; END \$\$;
                GRANT USAGE ON SCHEMA {{ .Values.bootstrap.db.crypto.schema }} TO "$CRYPTO_DB_USER_USERNAME";
                GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA {{ .Values.bootstrap.db.crypto.schema }} TO "$CRYPTO_DB_USER_USERNAME";
              SQL
          volumeMounts:
            - mountPath: /tmp
              name: temp
          env:
          {{- include "corda.bootstrapClusterDbEnv" . | nindent 12 }}
          {{ include "corda.rbacDbUserEnv" . | nindent 12 }}
          {{ include "corda.cryptoDbUserEnv" . | nindent 12 }}
          {{- include "corda.clusterDbEnv" . | nindent 12 }}
        {{- include "corda.generateAndExecuteSql" ( dict "name" "crypto-config" "subCommand" "create-crypto-config" "Values" .Values "Chart" .Chart "Release" .Release "schema" "CRYPTO" "namePostfix" "worker-config" "sqlFile" "crypto-config.sql" "sequenceNumber" 12) | nindent 8 }}
      volumes:
        - name: temp
          emptyDir: {}
        {{ include "corda.log4jVolume" . | nindent 8 }}

      {{- include "corda.bootstrapNodeSelector" . | nindent 6 }}

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
      {{- include "corda.imagePullSecrets" . | nindent 6 }}
      {{- include "corda.tolerations" . | nindent 6 }}
      {{- include "corda.bootstrapServiceAccount" . | nindent 6 }}
      {{- with .Values.podSecurityContext }}
      securityContext:
        {{ . | toYaml | nindent 8 }}
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
            'connect'{{- if .Values.bootstrap.kafka.cleanup }},
            '-d',
            '-w', '{{ .Values.bootstrap.kafka.timeoutSeconds }}'
            {{- end }}
          ]
          volumeMounts:
            - mountPath: /tmp
              name: temp
            {{- if and .Values.kafka.tls.enabled .Values.kafka.tls.truststore.valueFrom.secretKeyRef.name }}
            - mountPath: "/certs"
              name: certs
              readOnly: true
            {{- end }}
            {{ include "corda.log4jVolumeMount" . | nindent 12 }}
          env:
            {{ include "corda.bootstrapCliEnv" . | nindent 12 }}
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
        - name: create-trust-store
          image: {{ include "corda.bootstrapCliImage" . }}
          imagePullPolicy: {{ .Values.imagePullPolicy }}
          {{- include "corda.bootstrapResources" . | nindent 10 }}
          {{- include "corda.containerSecurityContext" . | nindent 10 }}
          env:
          {{- include "corda.bootstrapKafkaSaslUsernameAndPasswordEnv" . | nindent 12 }}
          {{- include "corda.kafkaTlsPassword" . | nindent 12 }}
          command:
            - /bin/bash
            - -c
          args:
            - |
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
        {{ include "corda.log4jVolume" . | nindent 8 }}
      restartPolicy: Never
      {{- include "corda.bootstrapNodeSelector" . | nindent 6 }}
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
      {{- include "corda.imagePullSecrets" . | nindent 6 }}
      {{- include "corda.tolerations" . | nindent 6 }}
      {{- include "corda.bootstrapServiceAccount" . | nindent 6 }}
      {{- with .Values.podSecurityContext }}
      securityContext:
        {{ . | toYaml | nindent 8 }}
      {{- end }}
      containers:
        - name: create-rbac-role-user-admin
          image: {{ include "corda.bootstrapCliImage" . }}
          imagePullPolicy: {{ .Values.imagePullPolicy }}
          {{- include "corda.bootstrapResources" . | nindent 10 }}
          {{- include "corda.containerSecurityContext" . | nindent 10 }}
          args: ['initial-rbac', 'user-admin', '--yield', '300', '--user', "$(REST_API_ADMIN_USERNAME)",
            '--password', "$(REST_API_ADMIN_PASSWORD)",
            '--target', "https://{{ include "corda.fullname" . }}-rest-worker:443", '--insecure']
          volumeMounts:
            - mountPath: /tmp
              name: temp
            {{ include "corda.log4jVolumeMount" . | nindent 12 }}
          env:
            {{ include "corda.restApiAdminSecretEnv" . | nindent 12 }}
            {{ include "corda.bootstrapCliEnv" . | nindent 12 }}
        - name: create-rbac-role-vnode-creator
          image: {{ include "corda.bootstrapCliImage" . }}
          imagePullPolicy: {{ .Values.imagePullPolicy }}
          {{- include "corda.bootstrapResources" . | nindent 10 }}
          {{- include "corda.containerSecurityContext" . | nindent 10 }}
          args: ['initial-rbac', 'vnode-creator', '--yield', '300', '--user', "$(REST_API_ADMIN_USERNAME)",
            '--password', "$(REST_API_ADMIN_PASSWORD)",
            '--target', "https://{{ include "corda.fullname" . }}-rest-worker:443", '--insecure']
          volumeMounts:
            - mountPath: /tmp
              name: temp
            {{ include "corda.log4jVolumeMount" . | nindent 12 }}
          env:
            {{ include "corda.restApiAdminSecretEnv" . | nindent 12 }}
            {{ include "corda.bootstrapCliEnv" . | nindent 12 }}
        - name: create-rbac-role-corda-dev
          image: {{ include "corda.bootstrapCliImage" . }}
          imagePullPolicy: {{ .Values.imagePullPolicy }}
          {{- include "corda.bootstrapResources" . | nindent 10 }}
          {{- include "corda.containerSecurityContext" . | nindent 10 }}
          args: ['initial-rbac', 'corda-developer', '--yield', '300', '--user', "$(REST_API_ADMIN_USERNAME)",
            '--password', "$(REST_API_ADMIN_PASSWORD)",
            '--target', "https://{{ include "corda.fullname" . }}-rest-worker:443", '--insecure']
          volumeMounts:
            - mountPath: /tmp
              name: temp
            {{ include "corda.log4jVolumeMount" . | nindent 12 }}
          env:
            {{ include "corda.restApiAdminSecretEnv" . | nindent 12 }}
            {{ include "corda.bootstrapCliEnv" . | nindent 12 }}
      {{- include "corda.bootstrapNodeSelector" . | nindent 6 }}
      volumes:
        - name: temp
          emptyDir: {}
        {{ include "corda.log4jVolume" . | nindent 8 }}
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
{{- define "corda.bootstrapResources" }}
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
{{- define "corda.bootstrapNodeSelector" }}
{{- with .Values.bootstrap.nodeSelector | default .Values.nodeSelector }}
nodeSelector:
  {{- toYaml . | nindent 2 }}
{{- end }}
{{- end }}

{{/*
Bootstrap service account
*/}}
{{- define "corda.bootstrapServiceAccount" }}
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


{{/*
Bootstrap declaration to declare an initial container for running corda-cli initial-config, then
a second init container to execute the output SQL to the relevant database
*/}}

{{- define "corda.generateAndExecuteSql" -}}
{{- /* define 2 init containers, which run in sequence. First run corda-cli initial-config to generate some SQL, storing in a persistent volume called working-volume. Second is a postgres image which mounts the same persistent volume and executes the SQL. */ -}}
- name: {{ printf "%02d-create-%s" .sequenceNumber .name }}
  image: {{ include "corda.bootstrapCliImage" . }}
  imagePullPolicy: {{ .Values.imagePullPolicy }}
  {{- include "corda.bootstrapResources" . | nindent 2 }}
  {{- include "corda.containerSecurityContext" . | nindent 2 }}
  {{- if eq .name "db" }}
  args: [ 'database', 'spec', '-g', 'config:{{ .Values.db.cluster.schema }},rbac:{{ .Values.bootstrap.db.rbac.schema }},crypto:{{ .Values.bootstrap.db.crypto.schema }}', '-c', '-l', '/tmp', '--jdbc-url', 'jdbc:{{ include "corda.clusterDbType" . }}://{{ required "A db host is required" .Values.db.cluster.host }}:{{ include "corda.clusterDbPort" . }}/{{ include "corda.clusterDbName" . }}', '-u', $(PGUSER), '-p', $(PGPASSWORD) ]
  {{- else }}
  args: [ 'initial-config', '{{ .subCommand | default "create-db-config" }}',{{ " " -}}

         {{- /* request admin access in some cases, only when the optional admin argument to this function (named template) is specified as true */ -}}
         {{- if eq .admin "true" -}} '-a',{{- end -}}

         {{- if and (not (eq .name "db")) (not (eq .name "crypto-config")) -}}
           {{- /* specify DB user */ -}}
           {{- "'-u'" -}}, '$({{ .environmentVariablePrefix -}}_USERNAME)',

           {{- /* specify DB password */ -}}
           {{- " '-p'" -}}, '$({{ .environmentVariablePrefix -}}_PASSWORD)',
         {{- end -}}

         {{- if and (not (eq .name "rest")) (not (eq .subCommand "create-crypto-config")) -}}
             {{- " '--name'" -}}, 'corda-{{ .longName | default .name }}',
             {{- " '--jdbc-url'" -}}, 'jdbc:{{ include "corda.clusterDbType" . }}://{{ required "A db host is required" .Values.db.cluster.host }}:{{ include "corda.clusterDbPort" . }}/{{ include "corda.clusterDbName" . }}{{- if .schema }}?currentSchema={{.schema }}{{- end -}}',
             {{- " '--jdbc-pool-max-size'" -}}, {{ (index .Values.bootstrap.db (.dbName | default .name)).dbConnectionPool.maxSize | quote }},
             {{- if not (kindIs "invalid" (index .Values.bootstrap.db (.dbName | default .name)).dbConnectionPool.minSize) -}}
                {{- " '--jdbc-pool-min-size'" -}}, {{ (index .Values.bootstrap.db (.dbName | default .name)).dbConnectionPool.minSize | quote }},
             {{- end -}}
             {{- " '--idle-timeout'" -}}, {{ (index .Values.bootstrap.db (.dbName | default .name)).dbConnectionPool.idleTimeoutSeconds | quote }},
             {{- " '--max-lifetime'" -}}, {{ (index .Values.bootstrap.db (.dbName | default .name)).dbConnectionPool.maxLifetimeSeconds | quote }},
             {{- " '--keepalive-time'" -}}, {{ (index .Values.bootstrap.db (.dbName | default .name)).dbConnectionPool.keepaliveTimeSeconds | quote }},
             {{- " '--validation-timeout'" -}}, {{ (index .Values.bootstrap.db (.dbName | default .name)).dbConnectionPool.validationTimeoutSeconds | quote }}, {{- " " -}}
         {{- end -}}

         {{- if not (eq .name "rest") -}}
           {{- if and (((.Values).config).vault).url  (not (eq .name "crypto-config")) -}}
             '-t', 'VAULT', '--vault-path', 'dbsecrets', '--key', {{ (printf "%s-db-password" .name)| quote }},
           {{- else -}}
             {{- /* using encryption secrets service, so provide its salt and passphrase */ -}}
             '--salt', "$(SALT)", '--passphrase', "$(PASSPHRASE)",
           {{- end -}}
         {{- end -}}

         {{- if and (eq .name "crypto-config") (((.Values).config).vault).url  -}}
            {{- /* when configuring the crypto service and using Vault then specify where to find the wrapping key salt and passphrase in Vault */ -}}
            '-t', 'VAULT', '--vault-path', 'cryptosecrets', '-ks', 'salt', '-kp', 'passphrase',
         {{- end -}}

         {{- " '-l'" -}}, '/tmp']
   {{- end }}
  workingDir: /tmp
  volumeMounts:
    - mountPath: /tmp
      name: temp
    {{ include "corda.log4jVolumeMount" . | nindent 4 }}
  env:
    {{- if eq .name "db" -}}
      {{- include "corda.bootstrapClusterDbEnv" . | nindent 4 }}
    {{- end -}}
    {{- if or (eq .name "rest") (eq .name "rbac") (eq .name "vnodes") (eq .name "crypto") -}}
       {{- "\n    " -}} {{- /* legacy whitespace compliance */ -}}
    {{- end -}}
    {{- if and (not (eq .name "rest")) (not (eq .name "db")) -}}
      {{ include "corda.configSaltAndPassphraseEnv" . | nindent 4 -}}
    {{- end -}}
    {{- if or (eq .name "rbac") (eq .name "crypto") (eq .name "vnodes") (eq .name "db") -}}
       {{- "\n    " -}} {{- /* legacy whitespace compliance */ -}}
    {{- end -}}

    {{- include "corda.bootstrapCliEnv" . | nindent 4 -}}{{- /* set JAVA_TOOL_OPTIONS, CONSOLE_LOG*, CORDA_CLI_HOME_DIR */ -}}

    {{- if or (eq .name "rbac") (eq .name "vnodes") }}
    {{ include "corda.rbacDbUserEnv" . | nindent 4 }}
    {{- end -}}

    {{- if eq .name "vnodes" -}}
      {{ include "corda.clusterDbEnv" . | nindent 4 -}}
    {{- end -}}
    {{- if eq .name "rest" -}}
      {{- include "corda.restApiAdminSecretEnv" . | nindent 4 }}
    {{- end -}}
    {{- if eq .environmentVariablePrefix "CRYPTO_DB_USER" -}}
      {{- include "corda.cryptoDbUserEnv" . | nindent 4 -}}
    {{- end }}
- name: {{ printf "%02d-apply-%s" (add .sequenceNumber 1) .name }}
  image: {{ include "corda.bootstrapDbClientImage" . }}
  imagePullPolicy: {{ .Values.imagePullPolicy }}
  {{- include "corda.bootstrapResources" . | nindent 2 }}
  {{- include "corda.containerSecurityContext" . | nindent 2 }}
  command: [ 'sh', '-c', '-e',{{- if eq .name "db" }} 'for f in /tmp/*.sql; do psql -v ON_ERROR_STOP=1 -h {{ required "A db host is required" .Values.db.cluster.host }} -p {{ include "corda.clusterDbPort" . }} -f "$f" --dbname {{ include "corda.clusterDbName" . }}; done'{{- else }} 'psql -v ON_ERROR_STOP=1 -h {{ required "A db host is required" .Values.db.cluster.host }} -p {{ include "corda.clusterDbPort" . }} -f /tmp/{{ .sqlFile | default "db-config.sql" }} --dbname "dbname={{ include "corda.clusterDbName" . }} options=--search_path={{ .searchPath | default .Values.db.cluster.schema }}"' {{- end }} ]
  volumeMounts:
    - mountPath: /tmp
      name: temp
  env:
  {{- include "corda.bootstrapClusterDbEnv" . | nindent 4 }}
{{- end }}
