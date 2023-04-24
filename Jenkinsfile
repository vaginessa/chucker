maven_push.gradle#!groovy
import groovy.transform.Field

@Field apkStashName
def gitRef
def scmVars
def shortCommit
def versionExt

@Library('Utils@master')
import co.bird.Utils
@Field utils = new Utils()

@Field buildType

timestamps {
  node('android-agent') {
    try {
      stage('Checkout') {
        scmVars = checkout scm
        shortCommit = scmVars.GIT_COMMIT.take(7)
        versionExt = "${env.BUILD_NUMBER}-${shortCommit}"
        sh '''
          cleanWs()
          git config --global user.email "devops+jenkins@bird.co" && git config --global user.name "Jenkins"
        '''

        apkStashName = "${scmVars.GIT_BRANCH}-${env.BUILD_NUMBER}"
        gitRef = scmVars.GIT_BRANCH
      }
      stage('Build and Publish') {
        docker.withRegistry('https://168995956934.dkr.ecr.us-west-2.amazonaws.com', 'ecr:us-west-2:ecs-credentials') {
          docker.build('local/android').inside('-v /root/.gradle:/root/.gradle -v /root/.android:/root/.android') {
            sh "./gradlew clean build"
            if (scmVars.GIT_BRANCH == "master") {
              def secrets = [
                [$class: 'VaultSecret', path: "secret/services/jenkins/artifactory", secretValues: [
                  [$class: 'VaultSecretValue', envVar: 'ARTIFACTORY_USER', vaultKey: 'USER'],
                  [$class: 'VaultSecretValue', envVar: 'ARTIFACTORY_API_KEY', vaultKey: 'API_KEY'],
                ]]
              ]
              wrap([$class: 'VaultBuildWrapper', vaultSecrets: secrets]) {
                sh "./gradlew publish -PreleaseVersionExt='${versionExt}'"
              }
            }
          }
        }
      }
    } catch (e) {
      currentBuild.result = 'FAILURE'
      throw e
    } finally {
      if (currentBuild.result == null) {
        currentBuild.result = 'SUCCESS'
      }
    }
  }
} //end timestamps
