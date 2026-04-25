// =============================================================================
// Jenkins CI/CD Pipeline — POC Keycloak Spring Boot
// Stages : DEV → OWASP → SonarQube → Trivy → Docker → UAT → PREPROD
//
// Credentials Jenkins requises (Manage Jenkins > Credentials) :
//   dockerhub-credentials         → Username/Password (Docker Hub)
//   nvd-api-key                   → Secret text (OWASP NVD — optionnel mais recommandé)
//   keycloak-url-uat              → Secret text (ex: http://keycloak-uat:8080)
//   keycloak-url-preprod          → Secret text (ex: http://keycloak-preprod:8080)
//   keycloak-client-secret-uat    → Secret text
//   keycloak-client-secret-preprod→ Secret text
//
// Serveur SonarQube configuré sous le nom "SonarQube" :
//   Manage Jenkins > Configure System > SonarQube Servers
// =============================================================================

pipeline {

    agent {
        docker {
            image 'moustaphisene/devsecops-pipeline:latest'
            args '-v /var/run/docker.sock:/var/run/docker.sock'
        }
    }

    tools {
        maven 'Maven-3.9'
        jdk   'JDK-17'
    }

    environment {
        DOCKER_HUB_USER = 'moustaphisene'
        APP_IMAGE       = "${DOCKER_HUB_USER}/poc-keycloak-app"
        SVC_IMAGE       = "${DOCKER_HUB_USER}/poc-keycloak-service"
        IMAGE_TAG       = "v${BUILD_NUMBER}"
        SONAR_KEY_APP   = 'poc-keycloak-app'
        SONAR_KEY_SVC   = 'poc-keycloak-service'
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 90, unit: 'MINUTES')
        disableConcurrentBuilds()
        timestamps()
        retry(2)
    }

    stages {

        // ================= CHECKOUT =================
        stage('Checkout') {
            steps {
                checkout scm
                script {
                    def shortCommit = env.GIT_COMMIT.take(8)
                    echo "Branch: ${env.GIT_BRANCH} | Commit: ${shortCommit}"
                }
            }
        }

        // ================= BUILD =================
        stage('DEV - Build & Tests') {
            parallel {

                stage('App') {
                    steps {
                        dir('demo/app-springboot') {
                            sh 'mvn clean package -DskipTests -B'
                        }
                    }
                }

                stage('Service') {
                    steps {
                        dir('demo/service-springboot-rest') {
                            sh 'mvn clean verify -B'
                        }
                    }
                    post {
                        always {
                            junit 'demo/service-springboot-rest/target/surefire-reports/*.xml'
                        }
                    }
                }
            }
        }

        // ================= OWASP =================
        stage('OWASP Scan') {
            steps {
                withCredentials([string(credentialsId: 'nvd-api-key', variable: 'NVD_KEY')]) {
                    sh '''
                        mvn org.owasp:dependency-check-maven:10.0.3:check \
                        -DnvdApiKey=${NVD_KEY} \
                        -DfailBuildOnCVSS=7 \
                        -B
                    '''
                }
            }
        }

        // ================= SONAR =================
        stage('SonarQube') {
            steps {
                withSonarQubeEnv('SonarQube') {
                    sh '''
                        mvn sonar:sonar \
                        -Dsonar.projectKey=${SONAR_KEY_APP} \
                        -B
                    '''
                }
            }
        }

        stage('Quality Gate') {
            steps {
                timeout(time: 10, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        // ================= TRIVY FS =================
        stage('Trivy FS') {
            steps {
                sh 'trivy fs --severity HIGH,CRITICAL .'
            }
        }

        // ================= DOCKER =================
        stage('Docker Build') {
            steps {
                sh '''
                    docker build -t ${APP_IMAGE}:${IMAGE_TAG} demo/app-springboot
                    docker build -t ${SVC_IMAGE}:${IMAGE_TAG} demo/service-springboot-rest
                '''
            }
        }

        // ================= TRIVY IMAGE =================
        stage('Trivy Image') {
            steps {
                sh '''
                    trivy image ${APP_IMAGE}:${IMAGE_TAG}
                    trivy image ${SVC_IMAGE}:${IMAGE_TAG}
                '''
            }
        }

        // ================= PUSH =================
        stage('Docker Push') {
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'dockerhub-credentials',
                    usernameVariable: 'DOCKER_USER',
                    passwordVariable: 'DOCKER_PASS'
                )]) {
                    sh '''
                        echo "$DOCKER_PASS" | docker login -u "$DOCKER_USER" --password-stdin
                        docker push ${APP_IMAGE}:${IMAGE_TAG}
                        docker push ${SVC_IMAGE}:${IMAGE_TAG}
                    '''
                }
            }
        }

        // ================= APPROVAL =================
        stage('UAT Approval') {
            steps {
                input message: "Deploy ${IMAGE_TAG} to UAT?"
            }
        }

        // ================= DEPLOY =================
        stage('Deploy UAT') {
            steps {
                sh '''
                    docker stop poc-keycloak-uat || true
                    docker rm poc-keycloak-uat || true

                    docker run -d \
                        --name poc-keycloak-uat \
                        -p 8091:8090 \
                        ${APP_IMAGE}:${IMAGE_TAG}
                '''
            }
        }
    }

    post {
        success {
            echo "SUCCESS — ${IMAGE_TAG}"
        }
        failure {
            echo "FAILURE — ${IMAGE_TAG}"
        }
        always {
            sh 'docker logout || true'
            cleanWs()
        }
    }
}