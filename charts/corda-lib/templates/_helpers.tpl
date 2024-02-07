{{/*
Expand the name of the chart.
*/}}
{{- define "corda.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
    Transform the given string input into kebab case
*/}}
{{- define "corda.kebabCase" -}}
{{ . | kebabcase | replace "p-2p" "p2p" }}
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
{{- with .Chart.AppVersion }}
app.kubernetes.io/version: {{ . | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{-  if .Values.commonLabels }}
{{- range $k, $v := .Values.commonLabels }}
{{ $k }}: {{ $v | quote }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "corda.selectorLabels" -}}
app.kubernetes.io/name: {{ include "corda.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Custom labels for bootstrap pods
*/}}
{{- define  "corda.commonBootstrapPodLabels" -}}
{{- with .Values.bootstrap.commonPodLabels }}
{{- . | toYaml }}
{{- end }}
{{- end }}

{{/*
Custom labels for deployments pods
*/}}
{{- define  "corda.workerCommonPodLabels" -}}
{{- with .Values.commonPodLabels }}
{{- . | toYaml }}
{{- end }}
{{- end }}

{{/*
Image pull secrets
*/}}
{{- define  "corda.imagePullSecrets" -}}
{{- with .Values.imagePullSecrets }}
imagePullSecrets:
{{- range . }}
  - name: {{ . }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Container security context
*/}}
{{- define "corda.containerSecurityContext" -}}
{{- if not .Values.dumpHostPath -}}
{{- with .Values.containerSecurityContext -}}
securityContext:
  {{- . | toYaml | nindent 2}}
{{- end }}
{{- end }}
{{- end }}

{{/*
topologySpreadConstraints to achieve high availability
*/}}
{{- define "corda.topologySpreadConstraints" -}}
{{- with .Values.topologySpreadConstraints }}
topologySpreadConstraints:
  {{- toYaml . | nindent 2 }}
{{- end }}
{{- end }}

{{/*
tolerations for node taints
*/}}
{{- define "corda.tolerations" -}}
{{- with .Values.tolerations }}
tolerations:
{{- range . }}
- key: {{ required "Must specify key for toleration" .key }}
  {{- with .operator }}
  operator: {{ . }}
  {{- end }}
  effect: {{ required ( printf "Must specify effect for toleration with key %s" .key ) .effect }}
  {{- if not (eq .operator "Exist") }}
  value: {{ required ( printf "Must specify value for toleration with key %s and operator not equal to 'Exist'" .key ) .value }}
  {{- end }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Log4j volume
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
Kafka bootstrap servers
*/}}
{{- define "corda.kafkaBootstrapServers" -}}
{{ required "Must specify kafka.bootstrapServers" .Values.kafka.bootstrapServers }}
{{- end }}

{{/*
Initial REST API admin secret name
*/}}
{{- define "corda.restApiAdminSecretName" -}}
{{ default (printf "%s-rest-api-admin" (include "corda.fullname" .)) }}
{{- end }}

{{/*
Initial REST API admin username secret name
*/}}
{{- define "corda.restApiAdminUsernameSecretName" -}}
{{ .Values.bootstrap.restApiAdmin.username.valueFrom.secretKeyRef.name | default ((include "corda.restApiAdminSecretName" .)) }}
{{- end }}

{{/*
Initial REST API admin password secret name
*/}}
{{- define "corda.restApiAdminPasswordSecretName" -}}
{{ .Values.bootstrap.restApiAdmin.password.valueFrom.secretKeyRef.name | default (include "corda.restApiAdminSecretName" .) }}
{{- end }}

{{/*
REST TLS keystore secret name
*/}}
{{- define "corda.restTlsSecretName" -}}
{{ .Values.workers.rest.tls.secretName | default (printf "%s-rest-tls" (include "corda.fullname" .)) }}
{{- end }}

{{/*
Initial REST API admin secret username key
*/}}
{{- define "corda.restApiAdminSecretUsernameKey" -}}
{{- if .Values.bootstrap.restApiAdmin.username.valueFrom.secretKeyRef.name -}}
{{ required "Must specify bootstrap.restApiAdmin.username.valueFrom.secretKeyRef.key" .Values.bootstrap.restApiAdmin.username.valueFrom.secretKeyRef.key }}
{{- else -}}
username
{{- end -}}
{{- end -}}

{{/*
Initial REST API admin secret password key
*/}}
{{- define "corda.restApiAdminSecretPasswordKey" -}}
{{- if .Values.bootstrap.restApiAdmin.password.valueFrom.secretKeyRef.name -}}
{{ required "Must specify bootstrap.restApiAdmin.password.valueFrom.secretKeyRef.key" .Values.bootstrap.restApiAdmin.password.valueFrom.secretKeyRef.key }}
{{- else -}}
password
{{- end -}}
{{- end -}}

{{/*
Initial REST API admin secret environment variable
*/}}
{{- define "corda.restApiAdminSecretEnv" -}}
- name: REST_API_ADMIN_USERNAME
  valueFrom:
    secretKeyRef:
      name: {{ include "corda.restApiAdminUsernameSecretName" . }}
      key: {{ include "corda.restApiAdminSecretUsernameKey" . }}
- name: REST_API_ADMIN_PASSWORD
  valueFrom:
    secretKeyRef:
      name: {{ include "corda.restApiAdminPasswordSecretName" . }}
      key: {{ include "corda.restApiAdminSecretPasswordKey" . }}
{{- end -}}

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
- name: DB_CLUSTER_USERNAME
  valueFrom:
    secretKeyRef:
      {{- include "corda.clusterDbUsername" . | nindent 6 }}
- name: DB_CLUSTER_PASSWORD
  valueFrom:
    secretKeyRef:
      {{- include "corda.clusterDbPassword" . | nindent 6 }}
{{- end -}}

{{- define "corda.clusterDbUsername" -}}
{{- if .Values.db.cluster.username.valueFrom.secretKeyRef.name -}}
name: {{ .Values.db.cluster.username.valueFrom.secretKeyRef.name | quote }}
key: {{ required "Must specify db.cluster.username.valueFrom.secretKeyRef.key" .Values.db.cluster.username.valueFrom.secretKeyRef.key | quote }}
{{- else -}}
name: {{ include "corda.clusterDbDefaultSecretName" . | quote }}
key: "username"
{{- end }}
{{- end }}

{{- define "corda.clusterDbPassword" -}}
{{- if .Values.db.cluster.password.valueFrom.secretKeyRef.name -}}
name: {{ .Values.db.cluster.password.valueFrom.secretKeyRef.name | quote }}
key: {{ required "Must specify db.cluster.password.valueFrom.secretKeyRef.key" .Values.db.cluster.password.valueFrom.secretKeyRef.key | quote }}
{{- else -}}
name: {{ include "corda.clusterDbDefaultSecretName" . | quote }}
key: "password"
{{- end -}}
{{- end -}}

{{/*
Default name for bootstrap cluster DB secret
*/}}
{{- define "corda.bootstrapClusterDbDefaultSecretName" -}}
{{ printf "%s-bootstrap-cluster-db" (include "corda.fullname" .) }}
{{- end -}}

{{- define "corda.bootstrapClusterPgUser" -}}
{{- if .Values.bootstrap.db.cluster.username.valueFrom.secretKeyRef.name -}}
name: {{ .Values.bootstrap.db.cluster.username.valueFrom.secretKeyRef.name | quote }}
key: {{ required "Must specify bootstrap.db.cluster.username.valueFrom.secretKeyRef.key" .Values.bootstrap.db.cluster.username.valueFrom.secretKeyRef.key | quote }}
{{- else if .Values.bootstrap.db.cluster.username.value -}}
name: {{ include "corda.bootstrapClusterDbDefaultSecretName" . | quote }}
key: "username"
{{- else -}}
{{ include "corda.clusterDbUsername" . }}
{{- end -}}
{{- end }}

{{- define "corda.bootstrapClusterPgPassword" -}}
{{- if .Values.bootstrap.db.cluster.password.valueFrom.secretKeyRef.name -}}
name: {{ .Values.bootstrap.db.cluster.password.valueFrom.secretKeyRef.name | quote }}
key: {{ required "Must specify bootstrap.db.cluster.password.valueFrom.secretKeyRef.key" .Values.bootstrap.db.cluster.password.valueFrom.secretKeyRef.key | quote }}
{{- else if .Values.bootstrap.db.cluster.password.value -}}
name: {{ include "corda.bootstrapClusterDbDefaultSecretName" . | quote }}
key: "password"
{{- else -}}
{{ include "corda.clusterDbPassword" . }}
{{- end -}}
{{- end }}

{{/*
Bootstrap cluster DB credentials environment variables
*/}}
{{- define "corda.bootstrapClusterDbEnv" -}}
- name: CLUSTER_PGUSER
  valueFrom:
    secretKeyRef:
      {{- include "corda.bootstrapClusterPgUser" . | nindent 6 }}
- name: CLUSTER_PGPASSWORD
  valueFrom:
    secretKeyRef:
      {{- include "corda.bootstrapClusterPgPassword" . | nindent 6 }}
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
      key: {{ required "Must specify config.encryption.passphrase.valueFrom.secretKeyRef.key" .Values.config.encryption.passphrase.valueFrom.secretKeyRef.key | quote }}
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
RBAC user environment variable
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
{{- define "corda.cryptoDbUsernameEnv" -}}
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
{{- end }}
{{- define "corda.cryptoDbPasswordEnv" -}}
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

{{/*
Config map for Log4J configuration
*/}}
{{- define "corda.log4jConfigMap" -}}
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: {{ printf "%s-log4j" (include "corda.fullname" . ) }}
  annotations:
    "helm.sh/hook-weight": "-1"
    "helm.sh/hook": pre-install
  labels:
    {{- include "corda.labels" . | nindent 4 }}
data:
  log4j2.xml: | {{ $.Files.Get "log4j2.xml" | nindent 4 }}
  {{- range $k, $v := .Values.workers }}
  {{- if $v.logging.override }}
  log4j2-{{ $k }}.xml: |
    {{- $v.logging.override | nindent 4 }}
  {{- end }}
  {{- end }}
{{- end }}

{{/*
Secret creation
*/}}
{{- define "corda.secret" -}}
{{- $ := index . 0 }}
{{- $context := index . 1 }}
{{- $path := index . 2 }}
{{- $secretName := index . 3 }}
{{- $fields := index . 4 }}
{{- $options := dict }}
{{- if gt (len .) 5 }}{{ $options = (index . 5) | default (dict) }}{{ end }}
{{- with $context }}
{{- $create := false }}
{{- range $k, $v := $fields }}
{{-   $field := ( get $context $k ) }}
{{-   if not $field.valueFrom.secretKeyRef.name }}
{{-     if and $v.required ( not $field.value ) }}
{{-       fail ( printf "Must specify %s.%s.valueFrom.secretKeyRef.name or %s.%s.value" $path $k $path $k ) }}
{{-     end }}
{{-     if or $field.value $v.generate }}
{{-       $create = true }}
{{-     end }}
{{-   end }}
{{- end }}
{{- if $create }}
---
apiVersion: v1
kind: Secret
metadata:
  name: {{ $secretName }}
  annotations:
    "helm.sh/hook-weight": "-1"
    "helm.sh/hook": pre-install
{{- if $options.cleanup }}
    "helm.sh/hook-delete-policy": hook-succeeded
{{- end }}
  labels:
    {{- include "corda.labels" $ | nindent 4 }}
type: Opaque
data:
{{- range $k, $v := $fields }}
{{-   $field := ( get $context $k ) }}
{{-   if not $field.valueFrom.secretKeyRef.name }}
{{-     if $field.value }}
  {{ $k }}: {{ $field.value | b64enc | quote }}
{{-     else if $v.generate }}
{{-       $existingSecret := lookup "v1" "Secret" $.Release.Namespace $secretName }}
{{-       $existingValue := "" }}
{{-       if $existingSecret }}
{{-         $existingValue = index $existingSecret.data $k }}
{{-       end }}
{{-       if $existingValue }}
  {{ $k }}: {{ $existingValue }}
{{-       else }}
  {{ $k }}: {{ randAlphaNum $v.generate | b64enc | quote }}
{{-       end }}
{{-     end }}
{{-   end }}
{{- end }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Pod Monitor creation
*/}}
{{- define "corda.podMonitor" -}}
{{- if .Values.metrics.podMonitor.enabled }}
---
apiVersion: monitoring.coreos.com/v1
kind: PodMonitor
metadata:
  name: {{ $.Release.Name }}-{{ include "corda.name" . }}
  labels:
  {{- range $k, $v := .Values.metrics.podMonitor.labels }}
    {{ $k }}: {{ $v | quote }}
  {{- end }}
spec:
  podMetricsEndpoints:
  - port: monitor
    metricRelabelings:
    {{- with .Values.metrics.keepNames }}
    - sourceLabels:
      - "__name__"
      regex: {{ join "|" . | quote }}
      action: "keep"
    {{- end }}
    {{- with .Values.metrics.dropLabels }}
    - regex: {{ join "|" . | quote }}
      action: "labeldrop"
    {{- end }}
  jobLabel: {{ $.Release.Name }}-{{ include "corda.name" . }}
  selector:
    matchLabels:
      app.kubernetes.io/name: {{ include "corda.name" . }}
{{- end }}
{{- end }}

{{/*
TLS Secret creation
*/}}
{{- define "corda.tlsSecret" -}}
{{- $ := index . 0 }}
{{- $purpose := index . 1 }}
{{- $serviceName := index . 2 }}
{{- $altNames := index . 3 }}
{{- $secretName := index . 4 }}
{{- $crtSecretKey := index . 5 }}
{{- $keySecretKey := index . 6 }}
{{- $caSecretKey := index . 7 }}
{{- $altNameAnnotationKey := "certificate/altNames" }}
{{- if not $altNames }}
{{-   $altNames = list }}
{{- end }}
{{- $altNames = ( concat $altNames (list ( printf "%s.%s" $serviceName $.Release.Namespace ) ( printf "%s.%s.svc" $serviceName $.Release.Namespace ) ) ) }}
{{- $altNamesAsString := ( join "," $altNames ) }}
{{- $create := true }}
{{- $existingSecret := lookup "v1" "Secret" $.Release.Namespace $secretName }}
{{- if $existingSecret }}
{{- $annotationValue := get $existingSecret.metadata.annotations $altNameAnnotationKey }}
{{- $create = not ( eq $annotationValue $altNamesAsString ) }}
{{- end }}
{{- $crtSecretValue := "to be defined" }}
{{- $keySecretValue := "to be defined" }}
{{- $caSecretValue := "to be defined" }}
{{- if $create }}
{{-   $caName := printf "%s Self-Signed Certification Authority" $purpose }}
{{-   $ca := genCA $caName 1000 }}
{{-   $cert := genSignedCert $serviceName nil $altNames 365 $ca }}
{{-   $crtSecretValue = $cert.Cert | b64enc | quote }}
{{-   $keySecretValue = $cert.Key | b64enc | quote }}
{{-   $caSecretValue = $ca.Cert | b64enc | quote }}
{{- else }}
{{-   $crtSecretValue = get $existingSecret.data $crtSecretKey }}
{{-   $keySecretValue = get $existingSecret.data $keySecretKey }}
{{-   $caSecretValue = get $existingSecret.data $caSecretKey }}
{{- end }}
---
apiVersion: v1
kind: Secret
metadata:
  name: {{ $secretName }}
  annotations:
    {{ $altNameAnnotationKey }}: {{ $altNamesAsString | quote }}
  labels:
    {{- include "corda.labels" $ | nindent 4 }}
type: Opaque
data:
  {{ $crtSecretKey }}: {{ $crtSecretValue }}
  {{ $keySecretKey }}: {{ $keySecretValue }}
  {{ $caSecretKey }}: {{ $caSecretValue }}
{{- end }}

{{/*
The port which should be used to connect to Corda worker instances
*/}}
{{- define "corda.workerServicePort" -}}
7000
{{- end -}}

{{/*
Cluster IP service name
*/}}
{{- define "corda.workerInternalServiceName" -}}
{{- printf "%s-internal-service" . -}}
{{- end -}}

{{/*
Get the endpoint argument for a given worker
*/}}
{{- define "corda.getWorkerEndpoint" }}
{{- $context := .context }}
{{- $worker := .worker }}
{{- $workerValues := ( index $context.Values.workers $worker ) }}
{{- $workerName := printf "%s-%s-worker" ( include "corda.fullname" $context ) ( include "corda.workerTypeKebabCase" $worker ) }}
{{- $workerServiceName := "" }}
{{- if ( ( $workerValues.sharding ).enabled ) }}
{{- $workerServiceName = include "corda.nginxName" $workerName }}
{{- else }}
{{- $workerServiceName = include "corda.workerInternalServiceName" $workerName }}
{{- end }}
{{- printf "endpoints.%s=%s:%s" $worker $workerServiceName ( include "corda.workerServicePort" $context ) }}
{{- end }}

{{/*
Default pod affinity
*/}}
{{- define "corda.defaultAffinity" -}}
{{- $weight := index . 0 }}
{{- $component := index . 1 }}
weight: {{ $weight}}
podAffinityTerm:
  labelSelector:
    matchExpressions:
      - key: "app.kubernetes.io/component"
        operator: In
        values:
          - {{ $component | quote }}
  topologyKey: "kubernetes.io/hostname"
{{- end }}

{{/*
Pod affinity
*/}}
{{- define "corda.affinity" -}}
{{- $ := index . 0 }}
{{- $component := index . 1 }}
{{- $affinity := default ( deepCopy $.Values.affinity ) dict }}
{{- if not ($affinity.podAntiAffinity) }}
{{- $_ := set $affinity "podAntiAffinity" dict }}
{{- end }}
{{- if not ($affinity.podAntiAffinity.preferredDuringSchedulingIgnoredDuringExecution) }}
{{- $_ := set $affinity.podAntiAffinity "preferredDuringSchedulingIgnoredDuringExecution" list }}
{{- end }}
{{- $_ := set $affinity.podAntiAffinity "preferredDuringSchedulingIgnoredDuringExecution" ( append $affinity.podAntiAffinity.preferredDuringSchedulingIgnoredDuringExecution ( fromYaml ( include "corda.defaultAffinity" ( list ( add ( len $affinity.podAntiAffinity.preferredDuringSchedulingIgnoredDuringExecution ) 1 ) $component ) ) ) ) }}
affinity:
{{- toYaml $affinity | nindent 2 }}
{{- end }}
