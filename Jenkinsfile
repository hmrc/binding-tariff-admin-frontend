#!/usr/bin/env groovy
pipeline {
  agent any

  environment {
    BUILD_TARGET = "target"
  }

  stages {
    stage('Test') {
      steps {
        sh('./run_all_tests.sh')
      }
    }

  }
  post {
      always {
          echo 'Cleaning the workspace'
          deleteDir()
      }
  }
}
