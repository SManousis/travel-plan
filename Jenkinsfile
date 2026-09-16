pipeline {
  agent any
  options { timestamps(); disableConcurrentBuilds() }
  stages {
    stage('Backend build and tests') { steps { sh './mvnw -B -ntp verify' } }
    stage('UI lint and build') { steps { dir('admin-ui') { sh 'npm ci'; sh 'npm run lint --if-present'; sh 'npm run build' } } }
    stage('Sonar analysis') { when { expression { env.SONAR_HOST_URL?.trim() } }; steps { withSonarQubeEnv('travel-plan-sonarqube') { sh './mvnw -B -ntp sonar:sonar -Dsonar.projectKey=travel-plan' } } }
    stage('Quality gate') { when { expression { env.SONAR_HOST_URL?.trim() } }; steps { timeout(time: 10, unit: 'MINUTES') { waitForQualityGate abortPipeline: true } } }
  }
  post { always { junit testResults: '**/target/surefire-reports/*.xml', allowEmptyResults: true; archiveArtifacts artifacts: '**/target/*.jar,admin-ui/dist/**', allowEmptyArchive: true } }
}
