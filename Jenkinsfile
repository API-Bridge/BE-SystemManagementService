pipeline {
    agent any
    
    environment {
        REGISTRY = 'your-docker-registry.com'
        IMAGE_NAME = 'system-management-service'
        ARGOCD_REPO = 'https://github.com/your-org/k8s-manifests.git'
        ARGOCD_REPO_BRANCH = 'main'
        KUBERNETES_NAMESPACE = 'system-management'
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
                    env.BUILD_TAG = "${env.BUILD_NUMBER}-${env.GIT_COMMIT_SHORT}"
                    env.IMAGE_TAG = "${env.REGISTRY}/${env.IMAGE_NAME}:${env.BUILD_TAG}"
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
                    def image = docker.build("${env.IMAGE_TAG}")
                    echo "Built Docker image: ${env.IMAGE_TAG}"
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
                            ${env.IMAGE_TAG}
                    """
                }
            }
        }
        
        stage('Push to Registry') {
            when {
                anyOf {
                    branch 'main'
                    branch 'develop'
                }
            }
            steps {
                script {
                    docker.withRegistry("https://${env.REGISTRY}", 'docker-registry-credentials') {
                        docker.image("${env.IMAGE_TAG}").push()
                        docker.image("${env.IMAGE_TAG}").push('latest')
                        echo "Pushed image to registry: ${env.IMAGE_TAG}"
                    }
                }
            }
        }
        
        stage('Update ArgoCD Manifests') {
            when {
                branch 'main'
            }
            steps {
                script {
                    withCredentials([usernamePassword(credentialsId: 'git-credentials', 
                                                   usernameVariable: 'GIT_USERNAME', 
                                                   passwordVariable: 'GIT_PASSWORD')]) {
                        sh """
                            git clone https://${GIT_USERNAME}:${GIT_PASSWORD}@${env.ARGOCD_REPO.replace('https://', '')} argocd-repo
                            cd argocd-repo
                            
                            # Update image tag in Kustomization or deployment manifest
                            sed -i 's|${env.REGISTRY}/${env.IMAGE_NAME}:.*|${env.IMAGE_TAG}|g' \
                                overlays/production/kustomization.yaml
                            
                            # Commit and push changes
                            git config user.name "Jenkins CI"
                            git config user.email "jenkins@your-company.com"
                            git add .
                            git commit -m "Update ${env.IMAGE_NAME} image to ${env.BUILD_TAG}"
                            git push origin ${env.ARGOCD_REPO_BRANCH}
                        """
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
                    withCredentials([usernamePassword(credentialsId: 'git-credentials', 
                                                   usernameVariable: 'GIT_USERNAME', 
                                                   passwordVariable: 'GIT_PASSWORD')]) {
                        sh """
                            git clone https://${GIT_USERNAME}:${GIT_PASSWORD}@${env.ARGOCD_REPO.replace('https://', '')} argocd-repo
                            cd argocd-repo
                            
                            # Update staging environment
                            sed -i 's|${env.REGISTRY}/${env.IMAGE_NAME}:.*|${env.IMAGE_TAG}|g' \
                                overlays/staging/kustomization.yaml
                            
                            git config user.name "Jenkins CI"
                            git config user.email "jenkins@your-company.com"
                            git add .
                            git commit -m "Deploy ${env.IMAGE_NAME} to staging: ${env.BUILD_TAG}"
                            git push origin ${env.ARGOCD_REPO_BRANCH}
                        """
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
                **Image:** ${env.IMAGE_TAG}
                
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