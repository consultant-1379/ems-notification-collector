{{/*
Expand the name of the chart.
*/}}
{{- define "context-simulator.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "context-simulator.fullname" -}}
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
{{- define "context-simulator.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "context-simulator.labels" -}}
helm.sh/chart: {{ include "context-simulator.chart" . }}
{{ include "context-simulator.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "context-simulator.selectorLabels" -}}
app.kubernetes.io/name: {{ include "context-simulator.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "context-simulator.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "context-simulator.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{/*
Create image pull secrets
*/}}
{{- define "context-simulator.pullSecrets" }}
  {{- $pullSecret := "" }}
  {{- if .Values.global }}
    {{- if .Values.global.pullSecret }}
      {{- $pullSecret = .Values.global.pullSecret }}
    {{- end }}
  {{- end }}
  {{- if index .Values "imageCredentials" }}
    {{- if index .Values "imageCredentials" "pullSecret" }}
      {{- $pullSecret = index .Values "imageCredentials" "pullSecret" }}
    {{- end }}
  {{- end }}
  {{- print $pullSecret }}
{{- end }}