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
            userRemoteConfigs: [[url          : "ssh://git@github.com/omec-project/${params.project}.git",
                                 refspec      : "pull/${params.ghprbPullId}/head",
                                 credentialsId: 'github-onf-bot-ssh-key',]],
            branches         : [[name: "FETCH_HEAD"]],
            extensions       : [
                [$class: 'RelativeTargetDirectory', relativeTargetDir: "${params.project}"],
                [$class: 'SubmoduleOption', recursiveSubmodules: true, parentCredentials: true]]
        ],)
      }
    }
    stage("Docker login") {
      steps {
        withCredentials([[$class          : 'UsernamePasswordMultiBinding',
                          credentialsId   : 'registry.aetherproject.org',
                          usernameVariable: 'USERNAME',
                          passwordVariable: 'PASSWORD']]) {
          sh 'docker login registry.aetherproject.org -u $USERNAME -p $PASSWORD'
        }
      }
    }
    stage("Set JDK 11") {
      steps {
        sh 'sudo update-java-alternatives -s java-11-amazon-corretto'
      }
    }
    stage("Dependencies") {
      options { retry(3) }
      steps {
        dir("${env.WORKSPACE}/up4") {
          sh 'make deps'
          sh 'cd app && make deps'
          sh 'cd scenarios && make deps'
        }
      }
    }
    stage("Build P4 program") {
      steps {
        dir("${env.WORKSPACE}/up4") {
          sh 'make build'
        }
      }
    }
    stage("Check P4Info") {
      steps {
        dir("${env.WORKSPACE}/up4") {
          script {
            def modifiedFiles = sh returnStdout: true, script: 'git status --porcelain'
            if (modifiedFiles.toString().length() > 0) {
              error("The following P4 build artifacts do not correspond to the expected ones, " +
                  "please run the build locally and commit any changes to these files:\n" + modifiedFiles.toString())
            }
          }
        }
      }
    }
    stage("Run PTF tests") {
      steps {
        dir("${env.WORKSPACE}/up4") {
          sh 'make check'
        }
      }
    }
    stage("Build ONOS app") {
      steps {
        dir("${env.WORKSPACE}/up4/app") {
          sh 'make build-ci'
        }
      }
    }
    stage("Smoke test") {
      options { retry(3) }
      steps {
        dir("${env.WORKSPACE}/up4/scenarios") {
          sh 'make reset smoke.xml stcColor=false stcDumpLogs=true'
        }
      }
    }
  }
}
