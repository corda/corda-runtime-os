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
      {{- include "corda.bootstrapServiceAccount" . | nindent 6 }}
      securityContext:
        runAsUser: 10001
        runAsGroup: 10002
        fsGroup: 1000
      containers:
        - name: fin
          image: {{ include "corda.bootstrapCliImage" . }}
          imagePullPolicy: {{ .Values.imagePullPolicy }}
          {{- include "corda.bootstrapResources" . | nindent 10 }}
          {{- include "corda.containerSecurityContext" . | nindent 10 }}
          command:
            - /bin/sh
            - -e
            - -c
          args: ["echo", "'DB Bootstrapped'"]
          workingDir: /tmp/working_dir
          volumeMounts:
            - mountPath: /tmp/working_dir
              name: working-volume
      initContainers:
        - name: create-db-schemas
          image: {{ include "corda.bootstrapCliImage" . }}
          imagePullPolicy: {{ .Values.imagePullPolicy }}
          {{- include "corda.bootstrapResources" . | nindent 10 }}
          {{- include "corda.containerSecurityContext" . | nindent 10 }}
          args: [ 'database', 'spec', '-g', 'config:{{ .Values.db.cluster.schema }},rbac:{{ .Values.bootstrap.db.rbac.schema }},crypto:{{ .Values.bootstrap.db.crypto.schema }}', '-c', '-l', '/tmp/working_dir', '--jdbc-url', 'jdbc:{{ include "corda.clusterDbType" . }}://{{ required "A db host is required" .Values.db.cluster.host }}:{{ include "corda.clusterDbPort" . }}/{{ include "corda.clusterDbName" . }}', '-u', $(PGUSER), '-p', $(PGPASSWORD) ]
          workingDir: /tmp/working_dir
          volumeMounts:
            - mountPath: /tmp/working_dir
              name: working-volume
            {{ include "corda.log4jVolumeMount" . | nindent 12 }}
          env:
            {{- include "corda.bootstrapClusterDbEnv" . | nindent 12 }}
            {{ include "corda.bootstrapCliEnv" . | nindent 12 }}
        - name: apply-db-schemas
          image: {{ include "corda.bootstrapDbClientImage" . }}
          imagePullPolicy: {{ .Values.imagePullPolicy }}
          {{- include "corda.bootstrapResources" . | nindent 10 }}
          {{- include "corda.containerSecurityContext" . | nindent 10 }}
          command: [ 'sh', '-c', 'for f in /tmp/working_dir/*.sql; do psql -h {{ required "A db host is required" .Values.db.cluster.host }} -p {{ include "corda.clusterDbPort" . }} -f "$f" --dbname {{ include "corda.clusterDbName" . }}; done' ]
          volumeMounts:
            - mountPath: /tmp/working_dir
              name: working-volume
          env:
          {{- include "corda.bootstrapClusterDbEnv" . | nindent 12 }}
        - name: create-initial-rbac-db-config
          image: {{ include "corda.bootstrapCliImage" . }}
          imagePullPolicy: {{ .Values.imagePullPolicy }}
          {{- include "corda.bootstrapResources" . | nindent 10 }}
          {{- include "corda.containerSecurityContext" . | nindent 10 }}
          args: [ 'initial-config', 'create-db-config', '-u', '$(RBAC_DB_USER_USERNAME)', '-p', $(RBAC_DB_USER_PASSWORD), '--name', 'corda-rbac', '--jdbc-url', 'jdbc:{{ include "corda.clusterDbType" . }}://{{ required "A db host is required" .Values.db.cluster.host }}:{{ include "corda.clusterDbPort" . }}/{{ include "corda.clusterDbName" . }}?currentSchema={{ .Values.bootstrap.db.rbac.schema }}', '--jdbc-pool-max-size', {{ .Values.bootstrap.db.rbac.dbConnectionPool.maxSize | quote }}, '--salt', "$(SALT)", '--passphrase', "$(PASSPHRASE)", '-l', '/tmp/working_dir']
          workingDir: /tmp/working_dir
          volumeMounts:
            - mountPath: /tmp/working_dir
              name: working-volume
            {{ include "corda.log4jVolumeMount" . | nindent 12 }}
          env:
            {{ include "corda.configSaltAndPassphraseEnv" . | nindent 12 }}
            {{ include "corda.bootstrapCliEnv" . | nindent 12 }}
            {{ include "corda.rbacDbUserEnv" . | nindent 12 }}
        - name: apply-initial-rbac-db-config
          image: {{ include "corda.bootstrapDbClientImage" . }}
          imagePullPolicy: {{ .Values.imagePullPolicy }}
          {{- include "corda.bootstrapResources" . | nindent 10 }}
          {{- include "corda.containerSecurityContext" . | nindent 10 }}
          command: [ 'sh', '-c', 'psql -h {{ required "A db host is required" .Values.db.cluster.host }} -p {{ include "corda.clusterDbPort" . }} -f /tmp/working_dir/db-config.sql --dbname "dbname={{ include "corda.clusterDbName" . }} options=--search_path={{ .Values.db.cluster.schema }}"' ]
          volumeMounts:
            - mountPath: /tmp/working_dir
              name: working-volume
          env:
          {{- include "corda.bootstrapClusterDbEnv" . | nindent 12 }}
        - name: create-initial-vnodes-db-config
          image: {{ include "corda.bootstrapCliImage" . }}
          imagePullPolicy: {{ .Values.imagePullPolicy }}
          {{- include "corda.bootstrapResources" . | nindent 10 }}
          {{- include "corda.containerSecurityContext" . | nindent 10 }}
          args: [ 'initial-config', 'create-db-config', '-a', '-u', $(DB_CLUSTER_USERNAME), '-p', $(DB_CLUSTER_PASSWORD), '--name', 'corda-virtual-nodes', '--jdbc-url', 'jdbc:{{ include "corda.clusterDbType" . }}://{{ required "A db host is required" .Values.db.cluster.host }}:{{ include "corda.clusterDbPort" . }}/{{ include "corda.clusterDbName" . }}', '--jdbc-pool-max-size', {{ .Values.bootstrap.db.rbac.dbConnectionPool.maxSize | quote }}, '--salt', "$(SALT)", '--passphrase', "$(PASSPHRASE)", '-l', '/tmp/working_dir']
          workingDir: /tmp/working_dir
          volumeMounts:
            - mountPath: /tmp/working_dir
              name: working-volume
            {{ include "corda.log4jVolumeMount" . | nindent 12 }}
          env:
            {{ include "corda.configSaltAndPassphraseEnv" . | nindent 12 }}
            {{ include "corda.bootstrapCliEnv" . | nindent 12 }}
            {{ include "corda.rbacDbUserEnv" . | nindent 12 }}
            {{- include "corda.clusterDbEnv" . | nindent 12 }}
        - name: apply-initial-vnodes-db-config
          image: {{ include "corda.bootstrapDbClientImage" . }}
          imagePullPolicy: {{ .Values.imagePullPolicy }}
          {{- include "corda.bootstrapResources" . | nindent 10 }}
          {{- include "corda.containerSecurityContext" . | nindent 10 }}
          command: [ 'sh', '-c', 'psql -h {{ required "A db host is required" .Values.db.cluster.host }} -p {{ include "corda.clusterDbPort" . }} -f /tmp/working_dir/db-config.sql --dbname "dbname={{ include "corda.clusterDbName" . }} options=--search_path={{ .Values.db.cluster.schema }}"' ]
          volumeMounts:
            - mountPath: /tmp/working_dir
              name: working-volume
          env:
          {{- include "corda.bootstrapClusterDbEnv" . | nindent 12 }}
        - name: create-initial-crypto-db-config
          image: {{ include "corda.bootstrapCliImage" . }}
          imagePullPolicy: {{ .Values.imagePullPolicy }}
          {{- include "corda.bootstrapResources" . | nindent 10 }}
          {{- include "corda.containerSecurityContext" . | nindent 10 }}
          args: [ 'initial-config', 'create-db-config', '-u', '$(CRYPTO_DB_USER_USERNAME)', '-p', '$(CRYPTO_DB_USER_PASSWORD)', '--name', 'corda-crypto', '--jdbc-url', 'jdbc:{{ include "corda.clusterDbType" . }}://{{ required "A db host is required" .Values.db.cluster.host }}:{{ include "corda.clusterDbPort" . }}/{{ include "corda.clusterDbName" . }}?currentSchema={{ .Values.bootstrap.db.crypto.schema }}', '--jdbc-pool-max-size', {{ .Values.bootstrap.db.crypto.dbConnectionPool.maxSize | quote }}, '--salt', "$(SALT)", '--passphrase', "$(PASSPHRASE)", '-l', '/tmp/working_dir']
          workingDir: /tmp/working_dir
          volumeMounts:
            - mountPath: /tmp/working_dir
              name: working-volume
            {{ include "corda.log4jVolumeMount" . | nindent 12 }}
          env:
            {{ include "corda.configSaltAndPassphraseEnv" . | nindent 12 }}
            {{ include "corda.bootstrapCliEnv" . | nindent 12 }}
            {{ include "corda.cryptoDbUserEnv" . | nindent 12 }}
        - name: apply-initial-crypto-db-config
          image: {{ include "corda.bootstrapDbClientImage" . }}
          imagePullPolicy: {{ .Values.imagePullPolicy }}
          {{- include "corda.bootstrapResources" . | nindent 10 }}
          {{- include "corda.containerSecurityContext" . | nindent 10 }}
          command: [ 'sh', '-c', 'psql -h {{ required "A db host is required" .Values.db.cluster.host }} -p {{ include "corda.clusterDbPort" . }} -f /tmp/working_dir/db-config.sql --dbname "dbname={{ include "corda.clusterDbName" . }} options=--search_path={{ .Values.db.cluster.schema }}"' ]
          volumeMounts:
            - mountPath: /tmp/working_dir
              name: working-volume
          env:
          {{- include "corda.bootstrapClusterDbEnv" . | nindent 12 }}
        - name: create-initial-rpc-admin
          image: {{ include "corda.bootstrapCliImage" . }}
          imagePullPolicy: {{ .Values.imagePullPolicy }}
          {{- include "corda.bootstrapResources" . | nindent 10 }}
          {{- include "corda.containerSecurityContext" . | nindent 10 }}
          args: [ 'initial-config', 'create-user-config', '-u', '$(INITIAL_ADMIN_USER_USERNAME)', '-p', '$(INITIAL_ADMIN_USER_PASSWORD)', '-l', '/tmp/working_dir']
          workingDir: /tmp/working_dir
          volumeMounts:
            - mountPath: /tmp/working_dir
              name: working-volume
            {{ include "corda.log4jVolumeMount" . | nindent 12 }}
          env:
            {{ include "corda.initialAdminUserSecretEnv" . | nindent 12 }}
            {{ include "corda.bootstrapCliEnv" . | nindent 12 }}
        - name: apply-initial-rpc-admin
          image: {{ include "corda.bootstrapDbClientImage" . }}
          imagePullPolicy: {{ .Values.imagePullPolicy }}
          {{- include "corda.bootstrapResources" . | nindent 10 }}
          {{- include "corda.containerSecurityContext" . | nindent 10 }}
          command: [ 'sh', '-c', 'psql -h {{ required "A db host is required" .Values.db.cluster.host }} -p {{ include "corda.clusterDbPort" . }} -f /tmp/working_dir/rbac-config.sql --dbname "dbname={{ include "corda.clusterDbName" . }} options=--search_path={{ .Values.bootstrap.db.rbac.schema }}"' ]
          volumeMounts:
            - mountPath: /tmp/working_dir
              name: working-volume
          env:
          {{- include "corda.bootstrapClusterDbEnv" . | nindent 12 }}
        - name: create-db-users-and-grant
          image: {{ include "corda.bootstrapDbClientImage" . }}
          imagePullPolicy: {{ .Values.imagePullPolicy }}
          {{- include "corda.bootstrapResources" . | nindent 10 }}
          {{- include "corda.containerSecurityContext" . | nindent 10 }}
          command: [ '/bin/sh', '-e', '-c' ]
          args:
            - |
              psql -h {{ required "A db host is required" .Values.db.cluster.host }} -p {{ include "corda.clusterDbPort" . }} {{ include "corda.clusterDbName" . }}  -c "GRANT USAGE ON SCHEMA {{ .Values.db.cluster.schema }} to \"$DB_CLUSTER_USERNAME\";"
              psql -h {{ required "A db host is required" .Values.db.cluster.host }} -p {{ include "corda.clusterDbPort" . }} {{ include "corda.clusterDbName" . }}  -c "GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA {{ .Values.db.cluster.schema }} to \"$DB_CLUSTER_USERNAME\";"
              psql -h {{ required "A db host is required" .Values.db.cluster.host }} -p {{ include "corda.clusterDbPort" . }} {{ include "corda.clusterDbName" . }}  -c "GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA {{ .Values.db.cluster.schema }} TO \"$DB_CLUSTER_USERNAME\";"
              psql -h {{ required "A db host is required" .Values.db.cluster.host }} -p {{ include "corda.clusterDbPort" . }} {{ include "corda.clusterDbName" . }}  -c "CREATE USER \"$RBAC_DB_USER_USERNAME\" WITH ENCRYPTED PASSWORD '$RBAC_DB_USER_PASSWORD';"
              psql -h {{ required "A db host is required" .Values.db.cluster.host }} -p {{ include "corda.clusterDbPort" . }} {{ include "corda.clusterDbName" . }}  -c "GRANT USAGE ON SCHEMA {{ .Values.bootstrap.db.rbac.schema }} to \"$RBAC_DB_USER_USERNAME\";"
              psql -h {{ required "A db host is required" .Values.db.cluster.host }} -p {{ include "corda.clusterDbPort" . }} {{ include "corda.clusterDbName" . }}  -c "GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA {{ .Values.bootstrap.db.rbac.schema }} to \"$RBAC_DB_USER_USERNAME\";"
              psql -h {{ required "A db host is required" .Values.db.cluster.host }} -p {{ include "corda.clusterDbPort" . }} {{ include "corda.clusterDbName" . }}  -c "CREATE USER \"$CRYPTO_DB_USER_USERNAME\" WITH ENCRYPTED PASSWORD '$CRYPTO_DB_USER_PASSWORD';"
              psql -h {{ required "A db host is required" .Values.db.cluster.host }} -p {{ include "corda.clusterDbPort" . }} {{ include "corda.clusterDbName" . }}  -c "GRANT USAGE ON SCHEMA {{ .Values.bootstrap.db.crypto.schema }} to \"$CRYPTO_DB_USER_USERNAME\";"
              psql -h {{ required "A db host is required" .Values.db.cluster.host }} -p {{ include "corda.clusterDbPort" . }} {{ include "corda.clusterDbName" . }}  -c "GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA {{ .Values.bootstrap.db.crypto.schema }} to \"$CRYPTO_DB_USER_USERNAME\";"
          volumeMounts:
            - mountPath: /tmp/working_dir
              name: working-volume
          env:
          {{- include "corda.bootstrapClusterDbEnv" . | nindent 12 }}
          {{ include "corda.rbacDbUserEnv" . | nindent 12 }}
          {{ include "corda.cryptoDbUserEnv" . | nindent 12 }}
          {{- include "corda.clusterDbEnv" . | nindent 12 }}
        - name: create-initial-crypto-worker-config
          image: {{ include "corda.bootstrapCliImage" . }}
          imagePullPolicy: {{ .Values.imagePullPolicy }}
          {{- include "corda.bootstrapResources" . | nindent 10 }}
          {{- include "corda.containerSecurityContext" . | nindent 10 }}
          args: [ 'initial-config', 'create-crypto-config', '--salt', "$(SALT)", '--passphrase', "$(PASSPHRASE)", '-l', '/tmp/working_dir']
          workingDir: /tmp/working_dir
          volumeMounts:
            - mountPath: /tmp/working_dir
              name: working-volume
            {{ include "corda.log4jVolumeMount" . | nindent 12 }}
          env:
            {{ include "corda.configSaltAndPassphraseEnv" . | nindent 12 }}
            {{ include "corda.bootstrapCliEnv" . | nindent 12 }}
        - name: apply-initial-crypto-worker-config
          image: {{ include "corda.bootstrapDbClientImage" . }}
          imagePullPolicy: {{ .Values.imagePullPolicy }}
          {{- include "corda.bootstrapResources" . | nindent 10 }}
          {{- include "corda.containerSecurityContext" . | nindent 10 }}
          command: [ 'sh', '-c', 'psql -h {{ required "A db host is required" .Values.db.cluster.host }} -p {{ include "corda.clusterDbPort" . }} -f /tmp/working_dir/crypto-config.sql --dbname "dbname={{ include "corda.clusterDbName" . }} options=--search_path={{ .Values.db.cluster.schema }}"' ]
          volumeMounts:
            - mountPath: /tmp/working_dir
              name: working-volume
          env:
          {{- include "corda.bootstrapClusterDbEnv" . | nindent 12 }}

      volumes:
        - name: working-volume
          emptyDir: {}
        {{ include "corda.log4jVolume" . | nindent 8 }}

      {{- include "corda.bootstrapNodeSelector" . | nindent 6 }}

      restartPolicy: Never
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
      {{- include "corda.bootstrapServiceAccount" . | nindent 6 }}
      securityContext:
        runAsUser: 10001
        runAsGroup: 10002
        fsGroup: 1000
      containers:
        - name: create-topics
          image: {{ include "corda.bootstrapCliImage" . }}
          imagePullPolicy: {{ .Values.imagePullPolicy }}
          {{- include "corda.containerSecurityContext" . | nindent 10 }}
          {{- include "corda.bootstrapResources" . | nindent 10 }}
          args: [
            'topic',
            '-b', '{{ include "corda.kafkaBootstrapServers" . }}',
            '-k', '/tmp/working_dir/config.properties',
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
            '-d'
            {{- end }}
          ]
          volumeMounts:
            - mountPath: /tmp/working_dir
              name: working-volume
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
                touch /tmp/working_dir/config.properties
                {{- if .Values.kafka.tls.enabled }}
                {{- if .Values.kafka.sasl.enabled }}
                echo "security.protocol=SASL_SSL\n" >> /tmp/working_dir/config.properties
                echo "sasl.mechanism={{ .Values.kafka.sasl.mechanism }}\n" >> /tmp/working_dir/config.properties
                {{- if eq .Values.kafka.sasl.mechanism "PLAIN" }}
                echo "sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required username=\"$SASL_USERNAME\" password=\"$SASL_PASSWORD\" ;\n">> /tmp/working_dir/config.properties
                {{- else }}
                echo "sasl.jaas.config=org.apache.kafka.common.security.scram.ScramLoginModule required username=\"$SASL_USERNAME\" password=\"$SASL_PASSWORD\" ;\n">> /tmp/working_dir/config.properties
                {{- end }}
                {{- else }}
                echo "security.protocol=SSL\n" >> /tmp/working_dir/config.properties
                {{- end }}
                {{- if .Values.kafka.tls.truststore.valueFrom.secretKeyRef.name }}
                echo "ssl.truststore.location=/certs/ca.crt\n" >> /tmp/working_dir/config.properties
                echo "ssl.truststore.type={{ .Values.kafka.tls.truststore.type | upper }}\n" >> /tmp/working_dir/config.properties
                {{- if or .Values.kafka.tls.truststore.password.value .Values.kafka.tls.truststore.password.valueFrom.secretKeyRef.name }}
                echo "ssl.truststore.password=\"$TRUSTSTORE_PASSWORD\"\n" >> /tmp/working_dir/config.properties
                {{- end }}
                {{- end }}
                {{- else }}
                {{- if .Values.kafka.sasl.enabled }}
                echo "security.protocol=SASL_PLAINTEXT\n" >> /tmp/working_dir/config.properties
                echo "sasl.mechanism={{ .Values.kafka.sasl.mechanism }}\n" >> /tmp/working_dir/config.properties
                echo "sasl.jaas.config=org.apache.kafka.common.security.scram.ScramLoginModule required username=\"$SASL_USERNAME\" password=\"$SASL_PASSWORD\" ;\n">> /tmp/working_dir/config.properties
                {{- end }}
                {{- end }}
          volumeMounts:
            - mountPath: /tmp/working_dir
              name: working-volume
      volumes:
        - name: working-volume
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
      {{- include "corda.bootstrapServiceAccount" . | nindent 6 }}
      securityContext:
        runAsUser: 10001
        runAsGroup: 10002
        fsGroup: 1000
      containers:
        - name: create-rbac-role-user-admin
          image: {{ include "corda.bootstrapCliImage" . }}
          imagePullPolicy: {{ .Values.imagePullPolicy }}
          {{- include "corda.bootstrapResources" . | nindent 10 }}
          {{- include "corda.containerSecurityContext" . | nindent 10 }}
          args: ['initial-rbac', 'user-admin', '--yield', '300', '--user', "$(INITIAL_ADMIN_USER_USERNAME)",
            '--password', "$(INITIAL_ADMIN_USER_PASSWORD)",
            '--target', "https://{{ include "corda.fullname" . }}-rest-worker:443"]
          volumeMounts:
            {{ include "corda.log4jVolumeMount" . | nindent 12 }}
          env:
            {{ include "corda.initialAdminUserSecretEnv" . | nindent 12 }}
            {{ include "corda.bootstrapCliEnv" . | nindent 12 }}
        - name: create-rbac-role-vnode-creator
          image: {{ include "corda.bootstrapCliImage" . }}
          imagePullPolicy: {{ .Values.imagePullPolicy }}
          {{- include "corda.bootstrapResources" . | nindent 10 }}
          {{- include "corda.containerSecurityContext" . | nindent 10 }}
          args: ['initial-rbac', 'vnode-creator', '--yield', '300', '--user', "$(INITIAL_ADMIN_USER_USERNAME)",
            '--password', "$(INITIAL_ADMIN_USER_PASSWORD)",
            '--target', "https://{{ include "corda.fullname" . }}-rest-worker:443"]
          volumeMounts:
            {{ include "corda.log4jVolumeMount" . | nindent 12 }}
          env:
            {{ include "corda.initialAdminUserSecretEnv" . | nindent 12 }}
            {{ include "corda.bootstrapCliEnv" . | nindent 12 }}
        - name: create-rbac-role-corda-dev
          image: {{ include "corda.bootstrapCliImage" . }}
          imagePullPolicy: {{ .Values.imagePullPolicy }}
          {{- include "corda.bootstrapResources" . | nindent 10 }}
          {{- include "corda.containerSecurityContext" . | nindent 10 }}
          args: ['initial-rbac', 'corda-developer', '--yield', '300', '--user', "$(INITIAL_ADMIN_USER_USERNAME)",
            '--password', "$(INITIAL_ADMIN_USER_PASSWORD)",
            '--target', "https://{{ include "corda.fullname" . }}-rest-worker:443"]
          volumeMounts:
            {{ include "corda.log4jVolumeMount" . | nindent 12 }}
          env:
            {{ include "corda.initialAdminUserSecretEnv" . | nindent 12 }}
            {{ include "corda.bootstrapCliEnv" . | nindent 12 }}
      {{- include "corda.bootstrapNodeSelector" . | nindent 6 }}
      volumes:
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
  {{- with .Values.bootstrap.resources.requests.cpu | default .Values.resources.requests.cpu }}
    cpu: {{ . }}
  {{- end }}
  {{- with .Values.bootstrap.resources.requests.memory | default .Values.resources.requests.memory  }}
    memory: {{ . }}
  {{- end}}
  limits:
  {{- with .Values.bootstrap.resources.limits.cpu | default .Values.resources.limits.cpu }}
    cpu: {{ . }}
  {{- end }}
  {{- with .Values.bootstrap.resources.limits.memory | default .Values.resources.limits.memory  }}
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
Boostrap Corda CLI environment variables
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