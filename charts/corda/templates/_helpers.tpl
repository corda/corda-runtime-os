{{/*
Expand the name of the chart.
*/}}
{{- define "corda.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "corda.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "corda.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "corda.labels" -}}
helm.sh/chart: {{ include "corda.chart" . }}
{{ include "corda.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "corda.selectorLabels" -}}
app.kubernetes.io/name: {{ include "corda.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Image pull secrets
*/}}
{{- define "corda.imagePullSecrets" -}}
{{- if ( not ( empty  .Values.imagePullSecrets ) ) }}
imagePullSecrets:
{{- range .Values.imagePullSecrets }}
  - name: {{ . }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Worker type in kebab case
*/}}
{{- define "corda.workerTypeKebabCase" -}}
{{ . | kebabcase | replace "p-2p" "p2p" }}
{{- end }}

{{/*
Worker type in upper snake case
*/}}
{{- define "corda.workerTypeUpperSnakeCase" -}}
{{ . | snakecase | replace "p_2p" "p2p" | upper }}
{{- end }}

{{/*
Worker pod name
*/}}
{{- define "corda.workerName" -}}
{{ include "corda.fullname" . }}-{{ include "corda.workerTypeKebabCase" .worker }}-worker
{{- end }}

{{/*
Worker annotations
*/}}
{{- define "corda.workerAnnotations" -}}
{{ if .Values.metrics.scrape -}}
prometheus.io/scrape: "true"
prometheus.io/path: /metrics
prometheus.io/port: "7000"
{{- end }}
{{ if .Values.annotations -}}
{{ .Values.annotations | toYaml }}
{{- end }}
{{ if ( get .Values.workers .worker ).annotations -}}
{{ ( get .Values.workers .worker ).annotations | toYaml }}
{{- end }}
{{- end }}

{{/*
Worker common labels
*/}}
{{- define "corda.workerLabels" -}}
{{ include "corda.labels" . }}
{{ include "corda.workerComponentLabel" . }}
{{- end }}

{{/*
Worker selector labels
*/}}
{{- define "corda.workerSelectorLabels" -}}
{{ include "corda.selectorLabels" . }}
{{ include "corda.workerComponentLabel" . }}
{{- end }}

{{/*
Worker component label
*/}}
{{- define "corda.workerComponentLabel" -}}
app.kubernetes.io/component: {{ .worker }}-worker
{{- end }}

{{/*
Worker image
*/}}
{{- define "corda.workerImage" -}}
"{{ ( get .Values.workers .worker ).image.registry | default .Values.image.registry }}/{{ ( get .Values.workers .worker ).image.repository }}:{{ ( get .Values.workers .worker ).image.tag | default .Values.image.tag | default .Chart.AppVersion }}"
{{- end }}

{{/*
Worker probes
*/}}
{{- define "corda.workerProbes" -}}
{{- if not ( get .Values.workers .worker ).debug.enabled }}
readinessProbe:
  httpGet:
    path: /status
    port: monitor
  periodSeconds: 10
  failureThreshold: 3
  timeoutSeconds: 5
livenessProbe:
  httpGet:
    path: /isHealthy
    port: monitor
  periodSeconds: 10
  failureThreshold: 3
  timeoutSeconds: 5
startupProbe:
  httpGet:
    path: /isHealthy
    port: monitor
  periodSeconds: 5
  failureThreshold: 20
  timeoutSeconds: 5
{{- end }}
{{- end }}

{{/*
Pod security context
*/}}
{{- define "corda.podSecurityContext" -}}
{{- if and ( not .Values.dumpHostPath ) ( not ( get .Values.workers .worker ).profiling.enabled ) }}
securityContext:
  runAsUser: 10001
  runAsGroup: 10002
  fsGroup: 1000
{{- end }}
{{- end }}

{{/*
Container security context - may be called for bootstrap or worker containers
*/}}
{{- define "corda.containerSecurityContext" -}}
{{- if .Values.dumpHostPath }}
{{-  $ignore := set . "addContainerSecurityContext" false }}
{{- else if .worker }}
{{-   if ( get .Values.workers .worker ).profiling.enabled }}
{{-     $ignore := set . "addContainerSecurityContext" false }}
{{-   else }}
{{-     $ignore := set . "addContainerSecurityContext" true }}
{{-   end }}
{{- else }}
{{-   $ignore := set . "addContainerSecurityContext" true }}
{{- end }}
{{- if .addContainerSecurityContext }}
securityContext:
  runAsUser: 10001
  runAsGroup: 10002
  allowPrivilegeEscalation: false
{{- end }}
{{- end }}

{{/*
Worker service account
*/}}
{{- define "corda.serviceAccount" }}
{{- if .Values.serviceAccount.name  }}
serviceAccountName: {{ .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{/*
CLI image
*/}}
{{- define "corda.bootstrapImage" -}}
"{{ .Values.bootstrap.image.registry | default .Values.image.registry }}/{{ .Values.bootstrap.image.repository }}:{{ .Values.bootstrap.image.tag | default .Values.image.tag | default .Chart.AppVersion }}"
{{- end }}

{{/*
DB client image
*/}}
{{- define "corda.dbClientImage" -}}
"{{- if .Values.bootstrap.db.clientImage.registry }}{{.Values.bootstrap.db.clientImage.registry}}/{{- end }}{{ .Values.bootstrap.db.clientImage.repository }}:{{ .Values.bootstrap.db.clientImage.tag }}"
{{- end }}

{{/*
Resources for the bootstrapper
*/}}
{{- define "corda.bootstrapResources" }}
resources:
  requests:
  {{- if or .Values.resources.requests.cpu .Values.bootstrap.resources.requests.cpu }}
    cpu: {{ default .Values.resources.requests.cpu .Values.bootstrap.resources.requests.cpu }}
  {{- end }}
  {{- if or .Values.resources.requests.memory .Values.bootstrap.resources.requests.memory }}
    memory: {{ default .Values.resources.requests.memory .Values.bootstrap.resources.requests.memory }}
  {{- end}}
  limits:
  {{- if or .Values.resources.limits.cpu .Values.bootstrap.resources.limits.cpu }}
    cpu: {{ default .Values.resources.limits.cpu .Values.bootstrap.resources.limits.cpu }}
  {{- end }}
  {{- if or .Values.resources.limits.memory .Values.bootstrap.resources.limits.memory }}
    memory: {{ default .Values.resources.limits.memory .Values.bootstrap.resources.limits.memory }}
  {{- end }}
{{- end }}

{{/*
Node selector for the bootstrapper
*/}}

{{- define "corda.bootstrapNodeSelector" }}
{{- with .Values.bootstrap.nodeSelector | default .Values.nodeSelector }}
nodeSelector:
  {{- toYaml . | nindent 2 }}
{{- end }}
{{- end }}

{{/*
Service account for the bootstrapper
*/}}
{{- define "corda.bootstrapServiceAccount" }}
{{- if or .Values.bootstrap.serviceAccount.name .Values.serviceAccount.name }}
serviceAccountName: {{ default .Values.serviceAccount.name .Values.bootstrap.serviceAccount.name }}
{{- end }}
{{- end }}

{{/*
Worker environment variables
*/}}
{{- define "corda.workerEnv" -}}
- name: K8S_NODE_NAME
  valueFrom:
    fieldRef:
      apiVersion: v1
      fieldPath: spec.nodeName
- name: K8S_POD_NAME
  valueFrom:
    fieldRef:
      apiVersion: v1
      fieldPath: metadata.name
- name: K8S_POD_UID
  valueFrom:
    fieldRef:
      apiVersion: v1
      fieldPath: metadata.uid
- name: K8S_NAMESPACE
  valueFrom:
    fieldRef:
      apiVersion: v1
      fieldPath: metadata.namespace
- name: JAVA_TOOL_OPTIONS
  value:
    {{ ( get .Values.workers .worker ).javaOptions }}
    {{- if ( get .Values.workers .worker ).debug.enabled }}
      -agentlib:jdwp=transport=dt_socket,server=y,address=5005,suspend={{ if ( get .Values.workers .worker ).debug.suspend }}y{{ else }}n{{ end }}
    {{- end -}}
    {{- if  ( get .Values.workers .worker ).profiling.enabled }}
      -agentpath:/opt/override/libyjpagent.so=exceptions=disable,port=10045,listen=all,dir=/dumps/profile/snapshots,logdir=/dumps/profile/logs
    {{- end -}}
    {{- if .Values.heapDumpOnOutOfMemoryError }}
      -XX:+HeapDumpOnOutOfMemoryError
      -XX:HeapDumpPath=/dumps/heap
    {{- end -}}
    {{- if ( get .Values.workers .worker ).verifyInstrumentation }}
      -Dco.paralleluniverse.fibers.verifyInstrumentation=true
    {{- end -}}
    {{- if .Values.kafka.sasl.enabled }}
      -Djava.security.auth.login.config=/etc/config/jaas.conf
    {{- end }}
- name: LOG4J_CONFIG_FILE
  {{- if  ( get .Values.workers .worker ).logging.override }}
  value: "/etc/log4j/log4j2.xml,/etc/log4j/log4j2-{{ .worker }}.xml"
  {{- else }}
  value: "/etc/log4j/log4j2.xml"
  {{- end }}
- name: CONSOLE_LOG_FORMAT
  value: {{ .Values.logging.format | quote }}
- name: CONSOLE_LOG_LEVEL
  value: {{ ( get .Values.workers .worker ).logging.level | default .Values.logging.level | quote }}
{{- end }}

{{/*
CLI log4j volume
*/}}
{{- define "corda.log4jVolume" -}}
- name: log4j
  configMap:
    name: {{ printf "%s-log4j" (include "corda.fullname" .) }}
{{- end }}

{{/*
Log4j volume mounts
*/}}
{{- define "corda.log4jVolumeMount" -}}
- name: log4j
  mountPath: /etc/log4j
{{- end }}

{{/*
Corda CLI environment variables
*/}}
{{- define "corda.cliEnv" -}}
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
Kafka bootstrap servers
*/}}
{{- define "corda.kafkaBootstrapServers" -}}
{{ required "Must specify kafka.bootstrapServers" .Values.kafka.bootstrapServers }}
{{- end }}

{{/*
Initial admin user secret name
*/}}
{{- define "corda.initialAdminUserSecretName" -}}
{{ default (printf "%s-initial-admin-user" (include "corda.fullname" .)) }}
{{- end }}

{{/*
Initial admin user username secret name
*/}}
{{- define "corda.initialAdminUserUsernameSecretName" -}}
{{ .Values.bootstrap.initialAdminUser.username.valueFrom.secretKeyRef.name | default ((include "corda.initialAdminUserSecretName" .)) }}
{{- end }}

{{/*
Initial admin user password secret name
*/}}
{{- define "corda.initialAdminUserPasswordSecretName" -}}
{{ .Values.bootstrap.initialAdminUser.password.valueFrom.secretKeyRef.name | default (include "corda.initialAdminUserSecretName" .) }}
{{- end }}

{{/*
Initial admin user secret username key
*/}}
{{- define "corda.initialAdminUserSecretUsernameKey" -}}
{{- if .Values.bootstrap.initialAdminUser.username.valueFrom.secretKeyRef.name -}}
{{ required "Must specify bootstrap.initialAdminUser.username.valueFrom.secretKeyRef.key" .Values.bootstrap.initialAdminUser.username.valueFrom.secretKeyRef.key }}
{{- else -}}
username
{{- end -}}
{{- end -}}

{{/*
Initial admin user secret password key
*/}}
{{- define "corda.initialAdminUserSecretPasswordKey" -}}
{{- if .Values.bootstrap.initialAdminUser.password.valueFrom.secretKeyRef.name -}}
{{ required "Must specify bootstrap.initialAdminUser.password.valueFrom.secretKeyRef.key" .Values.bootstrap.initialAdminUser.password.valueFrom.secretKeyRef.key }}
{{- else -}}
password
{{- end -}}
{{- end -}}

{{/*
Initial admin secret environment variable
*/}}
{{- define "corda.initialAdminUserSecretEnv" -}}
- name: INITIAL_ADMIN_USER_USERNAME
  valueFrom:
    secretKeyRef:
      name: {{ include "corda.initialAdminUserUsernameSecretName" . }}
      key: {{ include "corda.initialAdminUserSecretUsernameKey" . }}
- name: INITIAL_ADMIN_USER_PASSWORD
  valueFrom:
    secretKeyRef:
      name: {{ include "corda.initialAdminUserPasswordSecretName" . }}
      key: {{ include "corda.initialAdminUserSecretPasswordKey" . }}
{{- end -}}


{{/*
Worker Kafka arguments
*/}}
{{- define "corda.workerKafkaArgs" -}}
- "-mbootstrap.servers={{ include "corda.kafkaBootstrapServers" . }}"
- "--topic-prefix={{ .Values.kafka.topicPrefix }}"
{{- if .Values.kafka.tls.enabled }}
{{- if .Values.kafka.sasl.enabled }}
- "-msecurity.protocol=SASL_SSL"
- "-msasl.mechanism={{ .Values.kafka.sasl.mechanism }}"
{{- else }}
- "-msecurity.protocol=SSL"
{{- end }}
{{- if .Values.kafka.tls.truststore.valueFrom.secretKeyRef.name }}
- "-mssl.truststore.location=/certs/ca.crt"
- "-mssl.truststore.type={{ .Values.kafka.tls.truststore.type | upper }}"
{{- if or .Values.kafka.tls.truststore.password.value .Values.kafka.tls.truststore.password.valueFrom.secretKeyRef.name }}
- "-mssl.truststore.password=$TRUSTSTORE_PASSWORD"
{{- end }}
{{- end }}
{{- else }}
{{- if .Values.kafka.sasl.enabled }}
- "-msecurity.protocol=SASL_PLAINTEXT"
- "-msasl.mechanism={{ .Values.kafka.sasl.mechanism }}"
{{- end }}
{{- end }}
{{- end }}

{{/*
Resources for corda workers
*/}}
{{- define "corda.workerResources" }}
resources:
  requests:
  {{- if or .Values.resources.requests.cpu ( get .Values.workers .worker ).resources.requests.cpu }}
    cpu: {{ default .Values.resources.requests.cpu ( get .Values.workers .worker ).resources.requests.cpu }}
  {{- end }}
  {{- if or .Values.resources.requests.memory ( get .Values.workers .worker ).resources.requests.memory }}
    memory: {{ default .Values.resources.requests.memory ( get .Values.workers .worker ).resources.requests.memory }}
  {{- end}}
  limits:
  {{- if or .Values.resources.limits.cpu ( get .Values.workers .worker ).resources.limits.cpu }}
    cpu: {{ default .Values.resources.limits.cpu ( get .Values.workers .worker ).resources.limits.cpu }}
  {{- end }}
  {{- if or .Values.resources.limits.memory ( get .Values.workers .worker ).resources.limits.memory }}
    memory: {{ default .Values.resources.limits.memory ( get .Values.workers .worker ).resources.limits.memory }}
  {{- end }}
{{- end }}

{{/*
Volume mounts for corda workers
*/}}
{{- define "corda.workerVolumeMounts" -}}
{{- if and .Values.kafka.tls.enabled .Values.kafka.tls.truststore.valueFrom.secretKeyRef.name }}
- mountPath: "/certs"
  name: "certs"
  readOnly: true
{{- end }}
{{- if .Values.kafka.sasl.enabled  }}
- mountPath: "/etc/config"
  name: "jaas-conf"
  readOnly: true
{{- end }}
{{- if .Values.dumpHostPath }}
- mountPath: /dumps
  name: dumps
  subPathExpr: $(K8S_POD_NAME)
{{- end }}
{{ include "corda.log4jVolumeMount" . }}
{{- end }}

{{/*
Volumes for corda workers
*/}}
{{- define "corda.workerVolumes" }}
{{- if and .Values.kafka.tls.enabled .Values.kafka.tls.truststore.valueFrom.secretKeyRef.name }}
- name: certs
  secret:
    secretName: {{ .Values.kafka.tls.truststore.valueFrom.secretKeyRef.name | quote }}
    items:
      - key: {{ .Values.kafka.tls.truststore.valueFrom.secretKeyRef.key | quote }}
        path: "ca.crt"
{{- end -}}
{{- if .Values.kafka.sasl.enabled  }}
- name: jaas-conf
  emptyDir: {}
{{- end }}
{{- if .Values.dumpHostPath }}
- name: dumps
  hostPath:
    path: {{ .Values.dumpHostPath }}/{{ .Release.Namespace }}/
    type: DirectoryOrCreate
{{- end }}
{{ include "corda.log4jVolume" . }}
{{- end }}

{{/*
Cluster DB type
*/}}
{{- define "corda.clusterDbType" -}}
{{- .Values.db.cluster.type | default "postgresql" }}
{{- end -}}

{{/*
Cluster DB port
*/}}
{{- define "corda.clusterDbPort" -}}
{{- .Values.db.cluster.port | default "5432" }}
{{- end -}}

{{/*
Cluster DB name
*/}}
{{- define "corda.clusterDbName" -}}
{{- .Values.db.cluster.database | default "cordacluster" }}
{{- end -}}

{{/*
Default name for cluster DB secret
*/}}
{{- define "corda.clusterDbDefaultSecretName" -}}
{{ printf "%s-cluster-db" (include "corda.fullname" .) }}
{{- end -}}

{{/*
Cluster DB credentials environment variables
*/}}
{{- define "corda.clusterDbEnv" -}}
- name: PGUSER
  valueFrom:
    secretKeyRef:
      {{- if .Values.db.cluster.username.valueFrom.secretKeyRef.name }}
      name: {{ .Values.db.cluster.username.valueFrom.secretKeyRef.name | quote }}
      key: {{ required "Must specify db.cluster.username.valueFrom.secretKeyRef.key" .Values.db.cluster.username.valueFrom.secretKeyRef.key | quote }}
      {{- else }}
      name: {{ include "corda.clusterDbDefaultSecretName" . | quote }}
      key: "username"
      {{- end }}
- name: PGPASSWORD
  valueFrom:
    secretKeyRef:
      {{- if .Values.db.cluster.password.valueFrom.secretKeyRef.name }}
      name: {{ .Values.db.cluster.password.valueFrom.secretKeyRef.name | quote }}
      key: {{ required "Must specify db.cluster.password.valueFrom.secretKeyRef.key" .Values.db.cluster.password.valueFrom.secretKeyRef.key | quote }}
      {{- else }}
      name: {{ include "corda.clusterDbDefaultSecretName" . | quote }}
      key: "password"
      {{- end }}
{{- end -}}

{{/*
Kafka TLS truststore password
*/}}
{{- define "corda.kafkaTlsPassword" -}}
{{- if .Values.kafka.tls.enabled -}}
  {{- if .Values.kafka.tls.truststore.password.valueFrom.secretKeyRef.name -}}
- name: TRUSTSTORE_PASSWORD
  valueFrom:
    secretKeyRef:
      name: {{ .Values.kafka.tls.truststore.password.valueFrom.secretKeyRef.name | quote }}
      key: {{ required "Must specify kafka.tls.truststore.password.valueFrom.secretKeyRef.key" .Values.kafka.tls.truststore.password.valueFrom.secretKeyRef.key | quote }}
  {{- else if .Values.kafka.tls.truststore.password.value -}}
- name: TRUSTSTORE_PASSWORD
  value: {{ .Values.kafka.tls.truststore.password.value | quote }}
  {{- end -}}
{{- end -}}
{{- end -}}

{{/*
Bootstrap Kafka SASL username and password environment variables
*/}}
{{- define "corda.bootstrapKafkaSaslUsernameAndPasswordEnv" -}}
{{- if .Values.kafka.sasl.enabled }}
- name: SASL_USERNAME
  {{- if .Values.bootstrap.kafka.sasl.username.valueFrom.secretKeyRef.name -}}
  valueFrom:
    secretKeyRef:
      name: {{ .Values.bootstrap.kafka.sasl.username.valueFrom.secretKeyRef.name | quote }}
      key: {{ required "Must specify bootstrap.kafka.sasl.username.valueFrom.secretKeyRef.key" .Values.bootstrap.kafka.sasl.username.valueFrom.secretKeyRef.key | quote }}
  {{- else if .Values.bootstrap.kafka.sasl.username.value }}
  value: {{ .Values.bootstrap.kafka.sasl.username.value | quote }}
  {{- else if .Values.kafka.sasl.username.valueFrom.secretKeyRef.name }}
  valueFrom:
    secretKeyRef:
      name: {{ .Values.kafka.sasl.username.valueFrom.secretKeyRef.name | quote }}
      key: {{ required "Must specify kafka.sasl.username.valueFrom.secretKeyRef.key" .Values.kafka.sasl.username.valueFrom.secretKeyRef.key | quote }}
  {{- else }}
  value: {{ required "Must specify bootstrap.kafka.sasl.username.value, bootstrap.kafka.sasl.username.valueFrom.secretKeyRef.name, kafka.sasl.username.value, or kafka.sasl.username.valueFrom.secretKeyRef.name" .Values.kafka.sasl.username.value }}
  {{- end }}
- name: SASL_PASSWORD
  {{- if .Values.bootstrap.kafka.sasl.password.valueFrom.secretKeyRef.name }}
  valueFrom:
    secretKeyRef:
      name: {{ .Values.bootstrap.kafka.sasl.password.valueFrom.secretKeyRef.name | quote }}
      key: {{ required "Must specify kafka.sasl.password.valueFrom.secretKeyRef.key" .Values.bootstrap.kafka.sasl.password.valueFrom.secretKeyRef.key | quote }}
  {{- else if .Values.bootstrap.kafka.sasl.password.value }}
  value: {{ .Values.bootstrap.kafka.sasl.password.value | quote }}
  {{- else if .Values.kafka.sasl.password.valueFrom.secretKeyRef.name }}
  valueFrom:
    secretKeyRef:
      name: {{ .Values.kafka.sasl.password.valueFrom.secretKeyRef.name | quote }}
      key: {{ required "Must specify kafka.sasl.password.valueFrom.secretKeyRef.key" .Values.kafka.sasl.password.valueFrom.secretKeyRef.key | quote }}
  {{- else }}
  value: {{ required "Must specify bootstrap.kafka.sasl.password.value, bootstrap.kafka.sasl.password.valueFrom.secretKeyRef.name, kafka.sasl.password.value, or kafka.sasl.password.valueFrom.secretKeyRef.name" .Values.kafka.sasl.password.value }}
  {{- end }}
{{- end }}
{{- end -}}

{{/*
Kafka SASL username and password environment variables
*/}}
{{- define "corda.kafkaSaslUsernameAndPasswordEnv" -}}
{{- if .Values.kafka.sasl.enabled }}
  {{- $username := ( get .Values.workers .worker ).kafka.sasl.username }}
- name: SASL_USERNAME
  {{- if $username.valueFrom.secretKeyRef.name }}
  valueFrom:
    secretKeyRef:
      name: {{ $username.valueFrom.secretKeyRef.name | quote }}
      key: {{ required (printf "Must specify %s.kafka.sasl.username.valueFrom.secretKeyRef.key" .worker) $username.valueFrom.secretKeyRef.key | quote }}
  {{- else if $username.value }}
  value: {{ $username.value | quote }}
  {{- else if .Values.kafka.sasl.username.valueFrom.secretKeyRef.name }}
  valueFrom:
    secretKeyRef:
      name: {{ .Values.kafka.sasl.username.valueFrom.secretKeyRef.name | quote }}
      key: {{ required "Must specify kafka.sasl.username.valueFrom.secretKeyRef.key" .Values.kafka.sasl.username.valueFrom.secretKeyRef.key | quote }}
  {{- else }}
  value: {{ required (printf "Must specify workers.%s.kafka.sasl.username.value, workers.%s.kafka.sasl.username.valueFrom.secretKeyRef.name, kafka.sasl.username.value, or kafka.sasl.username.valueFrom.secretKeyRef.name" .worker .worker) .Values.kafka.sasl.username.value }}
  {{- end }}
  {{- $password := ( get .Values.workers .worker ).kafka.sasl.password }}
- name: SASL_PASSWORD
  {{- if $password.valueFrom.secretKeyRef.name }}
  valueFrom:
    secretKeyRef:
      name: {{ $password.valueFrom.secretKeyRef.name | quote }}
      key: {{ required (printf "Must specify %s.kafka.sasl.password.valueFrom.secretKeyRef.key" .worker) $password.valueFrom.secretKeyRef.key | quote }}
  {{- else if $password.value }}
  value: {{ $password.value | quote }}
  {{- else if .Values.kafka.sasl.password.valueFrom.secretKeyRef.name }}
  valueFrom:
    secretKeyRef:
      name: {{ .Values.kafka.sasl.password.valueFrom.secretKeyRef.name | quote }}
      key: {{ required "Must specify kafka.sasl.password.valueFrom.secretKeyRef.key" .Values.kafka.sasl.password.valueFrom.secretKeyRef.key | quote }}
  {{- else }}
  value: {{ required (printf "Must specify workers.%s.kafka.sasl.password.value, workers.%s.kafka.sasl.password.valueFrom.secretKeyRef.name, kafka.sasl.password.value, or kafka.sasl.password.valueFrom.secretKeyRef.name" .worker .worker) .Values.kafka.sasl.password.value }}
  {{- end }}
{{- end }}
{{- end -}}

{{/*
Kafka SASL init container
*/}}
{{- define "corda.kafkaSaslInitContainer" -}}
{{- if .Values.kafka.sasl.enabled }}
- name: create-sasl-jaas-conf
  image: {{ include "corda.workerImage" . }}
  imagePullPolicy:  {{ .Values.imagePullPolicy }}
  env:
  {{- include "corda.kafkaSaslUsernameAndPasswordEnv" . | nindent 2 }}
  {{- include "corda.containerSecurityContext" . | nindent 2 }}
  command:
  - /bin/bash
  - -c
  args:
    - |
        cat <<EOF > /etc/config/jaas.conf
        KafkaClient {
            {{- if eq .Values.kafka.sasl.mechanism "PLAIN" }}
            org.apache.kafka.common.security.plain.PlainLoginModule required
            {{- else }}
            org.apache.kafka.common.security.scram.ScramLoginModule required
            {{- end }}
            username="$SASL_USERNAME"
            password="$SASL_PASSWORD";
        };    
        EOF
  volumeMounts:
  - mountPath: "/etc/config"
    name: "jaas-conf"
    readOnly: false
  {{- include "corda.bootstrapResources" . | nindent 2 }}
{{- end }}    
{{- end }}

{{/*
SALT and PASSPHRASE environment variables for decrypting configuration.
*/}}
{{- define "corda.configSaltAndPassphraseEnv" -}}
- name: SALT
  valueFrom:
    secretKeyRef:
      {{- if .Values.config.encryption.salt.valueFrom.secretKeyRef.name }}
      name: {{ .Values.config.encryption.salt.valueFrom.secretKeyRef.name | quote }}
      key: {{ required "Must specify config.encryption.salt.valueFrom.secretKeyRef.key" .Values.config.encryption.salt.valueFrom.secretKeyRef.key | quote }}
      {{- else }}
      name: {{ (printf "%s-config" (include "corda.fullname" .)) | quote }}
      key: "salt"
      {{- end }}
- name: PASSPHRASE
  valueFrom:
    secretKeyRef:
      {{- if .Values.config.encryption.passphrase.valueFrom.secretKeyRef.name }}
      name: {{ .Values.config.encryption.passphrase.valueFrom.secretKeyRef.name | quote }}
      key: {{ required "Must specify config.encryption.passphrase.valueFrom.secretKeyRef.key" .Values.workers.db.passphrase.valueFrom.secretKeyRef.key | quote }}
      {{- else }}
      name: {{ (printf "%s-config" (include "corda.fullname" .)) | quote }}
      key: "passphrase" 
      {{- end }}
{{- end }}

{{/*
Default name for RBAC DB secret
*/}}
{{- define "corda.rbacDbDefaultSecretName" -}}
{{ printf "%s-rbac-db" (include "corda.fullname" .) }}
{{- end -}}

{{/*
RBAC User environment variable
*/}}
{{- define "corda.rbacDbUserEnv" -}}
- name: RBAC_DB_USER_USERNAME
  valueFrom:
    secretKeyRef:
      {{- if .Values.bootstrap.db.rbac.username.valueFrom.secretKeyRef.name }}
      name: {{ .Values.bootstrap.db.rbac.username.valueFrom.secretKeyRef.name | quote }}
      key: {{ required "Must specify bootstrap.db.rbac.username.valueFrom.secretKeyRef.key" .Values.bootstrap.db.rbac.username.valueFrom.secretKeyRef.key | quote }}
      {{- else }}
      name: {{ include "corda.rbacDbDefaultSecretName" . | quote }}
      key: "username" 
      {{- end }}
- name: RBAC_DB_USER_PASSWORD
  valueFrom:
    secretKeyRef:
      {{- if .Values.bootstrap.db.rbac.password.valueFrom.secretKeyRef.name }}
      name: {{ .Values.bootstrap.db.rbac.password.valueFrom.secretKeyRef.name | quote }}
      key: {{ required "Must specify bootstrap.db.rbac.password.valueFrom.secretKeyRef.key" .Values.bootstrap.db.rbac.password.valueFrom.secretKeyRef.key | quote }}
      {{- else }}
      name: {{ include "corda.rbacDbDefaultSecretName" . | quote }}
      key: "password" 
      {{- end }}
{{- end -}}

{{/*
Default name for crypto DB secret
*/}}
{{- define "corda.cryptoDbDefaultSecretName" -}}
{{ printf "%s-crypto-db" (include "corda.fullname" .) }}
{{- end -}}

{{/*
Crypto worker environment variable
*/}}
{{- define "corda.cryptoDbUserEnv" -}}
- name: CRYPTO_DB_USER_USERNAME
  valueFrom:
    secretKeyRef:
      {{- if .Values.bootstrap.db.crypto.username.valueFrom.secretKeyRef.name }}
      name: {{ .Values.bootstrap.db.crypto.username.valueFrom.secretKeyRef.name | quote }}
      key: {{ required "Must specify bootstrap.db.crypto.username.valueFrom.secretKeyRef.key" .Values.bootstrap.db.crypto.username.valueFrom.secretKeyRef.key | quote }}
      {{- else }}
      name: {{ include "corda.cryptoDbDefaultSecretName" . | quote }}
      key: "username" 
      {{- end }}
- name: CRYPTO_DB_USER_PASSWORD
  valueFrom:
    secretKeyRef:
      {{- if .Values.bootstrap.db.crypto.password.valueFrom.secretKeyRef.name }}
      name: {{ .Values.bootstrap.db.crypto.password.valueFrom.secretKeyRef.name | quote }}
      key: {{ required "Must specify bootstrap.db.crypto.password.valueFrom.secretKeyRef.key" .Values.bootstrap.db.crypto.password.valueFrom.secretKeyRef.key | quote }}
      {{- else }}
      name: {{ include "corda.cryptoDbDefaultSecretName" . | quote }}
      key: "password" 
      {{- end }}
{{- end -}}
