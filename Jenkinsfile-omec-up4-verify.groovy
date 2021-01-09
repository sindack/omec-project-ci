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

// Jenkinsfile-omec-up4-verify.groovy
// Main verification job for UP4


pipeline {

  agent {
    label "${params.buildNode}"
  }

  stages {
    stage("Environment Cleanup") {
      steps {
        step([$class: 'WsCleanup'])
      }
    }

    stage("Checkout") {
      steps {
        checkout([
            $class           : 'GitSCM',
            userRemoteConfigs: [[url: "ssh://git@github.com/omec-project/${params.project}.git", refspec: "pull/${params.ghprbPullId}/head"]],
            branches         : [[name: "FETCH_HEAD"]],
            credentialsId    : 'github-onf-bot-ssh-key',
            extensions       : [
                [$class: 'RelativeTargetDirectory', relativeTargetDir: "${params.project}"],
                [$class: 'SubmoduleOption', recursiveSubmodules: true]]
        ],)
      }
    }
  }
}
