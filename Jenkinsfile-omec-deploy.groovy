// Copyright 2020-present Open Networking Foundation
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// Jenkinsfile-omec-deploy.groovy: Deploys OMEC containers


pipeline {

  /* executor is determined by parameter */
  agent {
    label "${params.buildNode}"
  }

  options {
    timeout(time: 30, unit: 'MINUTES')
  }

  stages {
    stage('Environment Setup') {
      steps {
        step([$class: 'WsCleanup'])
        sshagent (credentials: ['cord-jenkins-ssh']) {
          sh label: "Clone aether-helm-charts and aether-pod-configs", script: """
          git clone ssh://jenkins@gerrit.opencord.org:29418/aether-helm-charts
          git clone ssh://jenkins@gerrit.opencord.org:29418/aether-pod-configs
          """
        }
        script {
          helm_args_control_plane = ""
          if (params.hssdbImage) { helm_args_control_plane += " --set images.tags.hssdb=${params.hssdbImage}" }
          if (params.hssImage) { helm_args_control_plane += " --set images.tags.hss=${params.hssImage}" }
          if (params.mmeImage) { helm_args_control_plane += " --set images.tags.mme=${params.mmeImage}" }
          if (params.spgwcImage) { helm_args_control_plane += " --set images.tags.spgwc=${params.spgwcImage}" }

          helm_args_data_plane = ""
          if (params.bessImage) { helm_args_data_plane += " --set images.tags.bess=${params.bessImage}" }
          if (params.zmqifaceImage) { helm_args_data_plane += " --set images.tags.zmqiface=${params.zmqifaceImage}" }
          if (params.pfcpifaceImage) { helm_args_data_plane += " --set images.tags.pfcpiface=${params.pfcpifaceImage}" }
        }
      }
    }
    stage('Clean up') {
      steps {
        sh label: 'Reset Deployment', script: """
          helm delete --kube-context ${params.dpContext} -n omec omec-data-plane || true
          helm delete --kube-context ${params.dpContext} -n omec omec-user-plane || true
          helm delete --kube-context ${params.cpContext} -n omec omec-control-plane || true

          kubectl --context ${params.cpContext} -n omec wait \
                  --for=delete \
                  --timeout=300s \
                  pod -l release=omec-control-plane || true
          kubectl --context ${params.dpContext} -n omec wait \
                  --for=delete \
                  --timeout=300s \
                  pod -l release=omec-user-plane || true

        """
      }
    }

    stage('Deploy Control Plane') {
      steps {
        withCredentials([string(credentialsId: 'aether-secret-deploy-test', variable: 'deploy_path'),
                         string(credentialsId: 'aether-secret-helm-charts', variable: 'helm_charts_path'),
                         usernamePassword(credentialsId: 'registry.aetherproject.org', passwordVariable: 'pass', usernameVariable: 'user')]) {
          sh label: "helm dep up", script: """
            cd ${helm_charts_path}/omec-control-plane
            helm dep up
          """
          sh label: "${params.cpContext}", script: """
            helm install --kube-context ${params.cpContext} \
                         omec-control-plane \
                         --namespace omec \
                         --values ${deploy_path}/${params.centralConfig} \
                         --set images.credentials.registry=registry.aetherproject.org \
                         --set images.credentials.username=${user} \
                         --set images.credentials.password=${pass} \
                         --set images.pullPolicy=Always \
                         ${helm_args_control_plane} \
                         ${helm_charts_path}/omec-control-plane
            kubectl --context ${params.cpContext} -n omec wait \
                    --for=condition=Ready \
                    --timeout=1200s \
                    pod -l release=omec-control-plane
            sleep 5
            kubectl --context ${params.cpContext} -n omec wait \
                    --for=condition=Ready \
                    --timeout=1200s \
                    pod -l release=omec-control-plane
          """
        }
      }
    }

    stage('Deploy Data Plane') {
      steps {
        withCredentials([string(credentialsId: 'aether-secret-deploy-test', variable: 'deploy_path'),
                         string(credentialsId: 'aether-secret-helm-charts', variable: 'helm_charts_path'),
                         usernamePassword(credentialsId: 'registry.aetherproject.org', passwordVariable: 'pass', usernameVariable: 'user')]) {
          sh label: "${params.dpContext}", script: """
            helm install --kube-context ${params.dpContext} \
                         omec-user-plane \
                         --namespace omec \
                         --values ${deploy_path}/${params.edgeConfig} \
                         --set images.credentials.registry=registry.aetherproject.org \
                         --set images.credentials.username=${user} \
                         --set images.credentials.password=${pass} \
                         --set images.pullPolicy=Always \
                         ${helm_args_data_plane} \
                         ${helm_charts_path}/omec-user-plane
            kubectl --context ${params.dpContext} -n omec wait \
                    --for=condition=Ready \
                    --timeout=300s \
                    pod -l release=omec-user-plane
            sleep 5
            kubectl --context ${params.dpContext} -n omec wait \
                    --for=condition=Ready \
                    --timeout=300s \
                    pod -l release=omec-user-plane
          """
        }
      }
    }
  }
}
