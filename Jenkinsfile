pipeline {
    agent any
    
    environment {
        ECR_REGISTRY = "123456789012.dkr.ecr.ap-northeast-2.amazonaws.com"
        ECR_REPOSITORY = "system-management-service"
        IMAGE_NAME = "system-management-service"
        CHART_REPO_URL = "https://github.com/your-org/helm-charts.git"
        CHART_REPO_CREDENTIALS_ID = "github-credentials"
        AWS_REGION = "ap-northeast-2"
    }
    
    tools {
        jdk 'OpenJDK-17'
        gradle 'Gradle-8.5'
    }
    
    stages {
        stage('Checkout') {
            steps {
                checkout scm
                script {
                    env.GIT_COMMIT_SHORT = sh(
                        script: 'git rev-parse --short HEAD',
                        returnStdout: true
                    ).trim()
                    env.IMAGE_TAG = "${env.GIT_COMMIT_SHORT}"
                    env.FULL_IMAGE_NAME = "${env.ECR_REGISTRY}/${env.ECR_REPOSITORY}:${env.IMAGE_TAG}"
                }
            }
        }
        
        stage('Test') {
            steps {
                script {
                    sh '''
                        chmod +x gradlew
                        ./gradlew clean test
                    '''
                }
            }
            post {
                always {
                    publishTestResults testResultsPattern: 'build/test-results/test/*.xml'
                    publishHTML([
                        allowMissing: false,
                        alwaysLinkToLastBuild: true,
                        keepAll: true,
                        reportDir: 'build/reports/tests/test',
                        reportFiles: 'index.html',
                        reportName: 'Test Report'
                    ])
                }
            }
        }
        
        stage('Build JAR') {
            steps {
                sh './gradlew bootJar'
            }
            post {
                success {
                    archiveArtifacts artifacts: 'build/libs/*.jar', fingerprint: true
                }
            }
        }
        
        stage('SonarQube Analysis') {
            when {
                anyOf {
                    branch 'main'
                    branch 'develop'
                    changeRequest()
                }
            }
            steps {
                withSonarQubeEnv('SonarQube') {
                    sh '''
                        ./gradlew sonarqube \
                            -Dsonar.projectKey=system-management-service \
                            -Dsonar.host.url=$SONAR_HOST_URL \
                            -Dsonar.login=$SONAR_AUTH_TOKEN
                    '''
                }
            }
        }
        
        stage('Quality Gate') {
            when {
                anyOf {
                    branch 'main'
                    branch 'develop'
                    changeRequest()
                }
            }
            steps {
                timeout(time: 10, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }
        
        stage('Build Docker Image') {
            when {
                anyOf {
                    branch 'main'
                    branch 'develop'
                }
            }
            steps {
                script {
                    sh "docker build -t ${env.FULL_IMAGE_NAME} ."
                    echo "Built Docker image: ${env.FULL_IMAGE_NAME}"
                }
            }
        }
        
        stage('Security Scan') {
            when {
                anyOf {
                    branch 'main'
                    branch 'develop'
                }
            }
            steps {
                script {
                    sh """
                        docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
                            -v \$(pwd):/workspace \
                            aquasec/trivy:latest image \
                            --exit-code 1 \
                            --severity HIGH,CRITICAL \
                            --format table \
                            ${env.FULL_IMAGE_NAME}
                    """
                }
            }
        }
        
        stage('Push to ECR') {
            when {
                anyOf {
                    branch 'main'
                    branch 'develop'
                }
            }
            steps {
                script {
                    withAWS(region: env.AWS_REGION) {
                        def ecrLogin = ecrLogin()
                        sh "docker login -u ${ecrLogin.user} -p ${ecrLogin.password} https://${ecrLogin.endpoint}"
                        sh "docker push ${env.FULL_IMAGE_NAME}"
                        echo "Pushed image to ECR: ${env.FULL_IMAGE_NAME}"
                    }
                }
            }
        }
        
        stage('Update Helm Chart') {
            when {
                branch 'main'
            }
            steps {
                script {
                    withCredentials([usernamePassword(credentialsId: env.CHART_REPO_CREDENTIALS_ID, 
                                                   usernameVariable: 'GIT_USERNAME', 
                                                   passwordVariable: 'GIT_PASSWORD')]) {
                        dir('helm-charts') {
                            git credentialsId: env.CHART_REPO_CREDENTIALS_ID, url: env.CHART_REPO_URL
                            
                            sh "yq e '.image.tag = \"${env.IMAGE_TAG}\"' -i ./charts/system-management-service/values.yaml"
                            
                            def newChartVersion = sh(
                                returnStdout: true, 
                                script: "yq e '.version = (.version | semver | bump_patch) | .version' ./charts/system-management-service/Chart.yaml"
                            ).trim()
                            
                            sh "yq e '.version = \"${newChartVersion}\"' -i ./charts/system-management-service/Chart.yaml"
                            
                            sh """
                                git config user.email 'jenkins@example.com'
                                git config user.name 'Jenkins CI'
                                git add .
                                git commit -m 'Update system-management-service to ${env.IMAGE_TAG} (Chart v${newChartVersion})'
                                git push origin main
                            """
                        }
                    }
                }
            }
        }
        
        stage('Deploy to Staging') {
            when {
                branch 'develop'
            }
            steps {
                script {
                    withCredentials([usernamePassword(credentialsId: env.CHART_REPO_CREDENTIALS_ID, 
                                                   usernameVariable: 'GIT_USERNAME', 
                                                   passwordVariable: 'GIT_PASSWORD')]) {
                        dir('helm-charts') {
                            git credentialsId: env.CHART_REPO_CREDENTIALS_ID, url: env.CHART_REPO_URL
                            
                            sh "yq e '.image.tag = \"${env.IMAGE_TAG}\"' -i ./charts/system-management-service/values-staging.yaml"
                            
                            sh """
                                git config user.email 'jenkins@example.com'
                                git config user.name 'Jenkins CI'
                                git add .
                                git commit -m 'Deploy system-management-service to staging: ${env.IMAGE_TAG}'
                                git push origin main
                            """
                        }
                    }
                }
            }
        }
        
        stage('Notify ArgoCD') {
            when {
                anyOf {
                    branch 'main'
                    branch 'develop'
                }
            }
            steps {
                script {
                    def environment = env.BRANCH_NAME == 'main' ? 'production' : 'staging'
                    
                    withCredentials([string(credentialsId: 'argocd-auth-token', variable: 'ARGOCD_TOKEN')]) {
                        sh """
                            # Trigger ArgoCD sync
                            curl -X POST \
                                -H "Authorization: Bearer \$ARGOCD_TOKEN" \
                                -H "Content-Type: application/json" \
                                -d '{"prune":true,"dryRun":false,"strategy":{"hook":{}}}' \
                                https://argocd.your-company.com/api/v1/applications/system-management-${environment}/sync
                        """
                    }
                }
            }
        }
    }
    
    post {
        always {
            cleanWs()
            sh 'docker system prune -f'
        }
        success {
            script {
                def message = """
                ✅ **Jenkins Build Success**
                
                **Project:** System Management Service
                **Branch:** ${env.BRANCH_NAME}
                **Build:** #${env.BUILD_NUMBER}
                **Commit:** ${env.GIT_COMMIT_SHORT}
                **Image:** ${env.FULL_IMAGE_NAME}
                
                **Build URL:** ${env.BUILD_URL}
                """
                
                // Slack notification
                slackSend(
                    color: 'good',
                    message: message,
                    channel: '#deployments'
                )
            }
        }
        failure {
            script {
                def message = """
                ❌ **Jenkins Build Failed**
                
                **Project:** System Management Service
                **Branch:** ${env.BRANCH_NAME}
                **Build:** #${env.BUILD_NUMBER}
                **Commit:** ${env.GIT_COMMIT_SHORT}
                
                **Build URL:** ${env.BUILD_URL}
                **Console:** ${env.BUILD_URL}console
                """
                
                // Slack notification
                slackSend(
                    color: 'danger',
                    message: message,
                    channel: '#deployments'
                )
            }
        }
    }
}