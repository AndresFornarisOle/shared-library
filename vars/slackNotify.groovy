@Library('shared-library') _

pipeline {
  agent any

  environment {
    DEPLOY_REGION     = 'us-east-1'
    AWS_S3_BUCKET     = 'ole-web-widget'
    SOURCE_DIR        = 'dist'
    DISTRIBUTION_ID   = 'EEEPLHJL4DKP2'
    SLACK_CHANNEL     = '#tech-deploys'
  }

  stages {
    stage('Inicio') {
      steps {
        script {
          slackNotify(
            channel: SLACK_CHANNEL,
            color: '#FBBF24',
            showStatus: false, // 🔥 Evita "SUCCESS" al inicio
            message: ":rocket: *Desplegando `${env.JOB_NAME}`* (Build #${env.BUILD_NUMBER}) en `${DEPLOY_REGION}`\n🔗 <${env.BUILD_URL}|Ver detalles>"
          )
        }
      }
    }

    stage('Build Docker Image') {
      steps {
        script {
          echo '🐳 Construyendo imagen Docker...'
          sh 'docker build --no-cache -t react-deploy .'
        }
      }
    }

    stage('Build y Deploy a S3') {
      steps {
        withCredentials([
          [$class: 'AmazonWebServicesCredentialsBinding', credentialsId: 'aws-credentials-global', accessKeyVariable: 'AWS_AK', secretKeyVariable: 'AWS_SK']
        ]) {
          script {
            sh '''
              docker run --rm \
                -v $PWD:/app \
                -w /app \
                -e AWS_ACCESS_KEY_ID=$AWS_AK \
                -e AWS_SECRET_ACCESS_KEY=$AWS_SK \
                -e AWS_DEFAULT_REGION=$DEPLOY_REGION \
                react-deploy bash -c "
                  echo '📦 Instalando dependencias y compilando...'
                  npm install --legacy-peer-deps
                  npm run build
                  echo '📤 Subiendo a S3...'
                  aws s3 sync $SOURCE_DIR s3://$AWS_S3_BUCKET --delete --region $DEPLOY_REGION
                  echo '🧹 Invalidando caché de CloudFront...'
                  aws cloudfront create-invalidation --distribution-id $DISTRIBUTION_ID --paths '/*'
                "
            '''
          }
        }
      }
    }
  }

  post {
    success {
      script {
        slackNotify(
          channel: SLACK_CHANNEL,
          color: "good",
          message: "✅ *Despliegue exitoso* en `${DEPLOY_REGION}` para `${env.JOB_NAME}` (Build #${env.BUILD_NUMBER})"
        )
      }
    }

    failure {
      script {
        slackNotify(
          channel: SLACK_CHANNEL,
          color: "danger",
          includeLog: true, // 🔥 Incluye errores
          message: "❌ *Despliegue fallido* en `${DEPLOY_REGION}` para `${env.JOB_NAME}` (Build #${env.BUILD_NUMBER})"
        )
      }
    }
  }
}
