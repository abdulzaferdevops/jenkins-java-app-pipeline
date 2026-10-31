// Lab 3: jenkins-java-app-pipeline
// GitHub push -> Jenkins -> Maven build, JUnit tests and JaCoCo coverage gate in a
// throwaway Maven container -> archive the versioned JAR -> release on the app server
// (checksum-verified) -> restart java-app.service -> health check through nginx
// -> automatic rollback if unhealthy.
//
// One-time Jenkins setup (from Lab 1): credential 'app-server-deploy', global APP_HOST,
// app server host key in known_hosts.

pipeline {
  agent any

  options {
    disableConcurrentBuilds()
    buildDiscarder(logRotator(numToKeepStr: '20', artifactNumToKeepStr: '10'))
    timeout(time: 20, unit: 'MINUTES')
  }

  triggers {
    githubPush()
  }

  parameters {
    choice(name: 'ACTION', choices: ['deploy', 'rollback'],
           description: 'deploy = build, test and release this commit; rollback = switch back to the previous healthy release')
  }

  environment {
    APP_ROOT      = '/opt/java-app'
    HEALTH_URL    = 'http://127.0.0.1:8085/health'   // through nginx, as users reach it
    KEEP_RELEASES = '5'
    BUILD_IMAGE   = 'maven:3.9-eclipse-temurin-17'
    SSH_OPTS      = '-o BatchMode=yes -o ConnectTimeout=10'
  }

  stages {
    stage('Preflight') {
      steps {
        script {
          if (!env.APP_HOST?.trim()) {
            error('APP_HOST is not set: Manage Jenkins > System > Global properties > Environment variables')
          }
          env.RELEASE = sh(
            script: 'printf "%05d-%s" "$BUILD_NUMBER" "$(echo "${GIT_COMMIT:-manual}" | cut -c1-7)"',
            returnStdout: true
          ).trim()
          currentBuild.displayName = "#${env.BUILD_NUMBER} ${params.ACTION}"
          currentBuild.description = (params.ACTION == 'deploy') ? "release ${env.RELEASE}" : 'rollback'
        }
        sh '''
          if [ "${ACTION:-deploy}" = "deploy" ]; then
            docker info >/dev/null 2>&1 || { echo "Jenkins cannot use Docker (needed for the build stage)"; exit 1; }
          fi
        '''
        sshagent(credentials: ['app-server-deploy']) {
          sh '''ssh $SSH_OPTS deploy@$APP_HOST 'echo "connected to $(hostname -s) as $(whoami)"' '''
        }
      }
    }

    stage('Build & test') {
      when { expression { params.ACTION == 'deploy' } }
      steps {
        sh '''
          if grep -rIlE --exclude-dir=.git --exclude-dir=target -e "-----BEGIN [A-Z ]*PRIVATE KEY-----" . ; then
            echo "Refusing to build: a private key is committed in the file(s) above"
            exit 1
          fi

          # Throwaway Maven container sharing this Jenkins container's volumes, so the
          # workspace is at the same path. Runs as the Jenkins user (no root-owned files).
          # The Maven repository lives in JENKINS_HOME, so dependencies download once.
          JC=$(hostname)
          docker inspect "$JC" >/dev/null 2>&1 || JC=jenkins
          M2="${JENKINS_HOME:-/var/jenkins_home}/.m2/repository"

          docker run --rm --volumes-from "$JC" -w "$WORKSPACE" \
            -u "$(id -u):$(id -g)" -e HOME=/tmp \
            --entrypoint mvn "$BUILD_IMAGE" \
            -B -ntp -Duser.home=/tmp -Dmaven.repo.local="$M2" clean verify
        '''
      }
      post {
        always {
          junit testResults: 'target/surefire-reports/*.xml', allowEmptyResults: true
          sh '''
            CSV=target/site/jacoco/jacoco.csv
            if [ -f "$CSV" ]; then
              awk -F, 'NR > 1 { lm += $8; lc += $9; bm += $6; bc += $7 }
                END {
                  printf "coverage: lines %.1f%% (%d/%d)", 100 * lc / (lm + lc), lc, lm + lc
                  if (bm + bc > 0) printf ", branches %.1f%% (%d/%d)", 100 * bc / (bm + bc), bc, bm + bc
                  printf "\\n"
                }' "$CSV"
            fi
          '''
        }
      }
    }

    stage('Archive') {
      when { expression { params.ACTION == 'deploy' } }
      steps {
        archiveArtifacts artifacts: 'target/fx-rates-service.jar', fingerprint: true
        archiveArtifacts artifacts: 'target/site/jacoco/**', allowEmptyArchive: true
      }
    }

    stage('Package') {
      when { expression { params.ACTION == 'deploy' } }
      steps {
        sh '''
          rm -rf build release.tgz
          mkdir -p build
          cp target/fx-rates-service.jar build/app.jar
          {
            echo "release=$RELEASE"
            echo "commit=${GIT_COMMIT:-unknown}"
            echo "built=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
            echo "sha256=$(sha256sum build/app.jar | cut -d' ' -f1)"
          } > build/RELEASE
          cat build/RELEASE
          tar -C build -czf release.tgz .
        '''
      }
    }

    stage('Deploy') {
      when { expression { params.ACTION == 'deploy' } }
      steps {
        sshagent(credentials: ['app-server-deploy']) {
          sh '''
            ssh $SSH_OPTS deploy@$APP_HOST "mkdir -p $APP_ROOT/releases/$RELEASE && tar -xzf - -C $APP_ROOT/releases/$RELEASE" < release.tgz
            ssh $SSH_OPTS deploy@$APP_HOST bash -s -- deploy "$APP_ROOT" "$RELEASE" "$KEEP_RELEASES" "$HEALTH_URL" < scripts/release.sh
          '''
        }
      }
    }

    stage('Smoke test') {
      when { expression { params.ACTION == 'deploy' } }
      steps {
        sshagent(credentials: ['app-server-deploy']) {
          sh '''
            ssh $SSH_OPTS deploy@$APP_HOST "curl -fsS 'http://127.0.0.1:8085/api/convert?from=OMR&to=USD&amount=100'" | tee smoke.json
            echo
            grep -q '"result":' smoke.json || { echo "smoke test failed"; exit 1; }
            echo "smoke test passed"
          '''
        }
      }
    }

    stage('Rollback') {
      when { expression { params.ACTION == 'rollback' } }
      steps {
        sshagent(credentials: ['app-server-deploy']) {
          sh 'ssh $SSH_OPTS deploy@$APP_HOST bash -s -- rollback "$APP_ROOT" "$HEALTH_URL" < scripts/release.sh'
        }
      }
    }
  }

  post {
    always {
      sh 'rm -rf build release.tgz smoke.json'
    }
  }
}
