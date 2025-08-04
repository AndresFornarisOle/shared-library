def call(Map config = [:]) {
    def channel      = config.channel ?: '#tech-deploys'
    def color        = config.color ?: 'good'
    def includeLog   = config.includeLog != null ? config.includeLog : true  // 🔥 Ahora por defecto siempre true
    def buildUrl     = env.BUILD_URL ?: ''
    def jobName      = env.JOB_NAME ?: ''
    def buildNumber  = env.BUILD_NUMBER ?: ''
    def result       = currentBuild.currentResult ?: 'UNKNOWN'
    def triggeredBy  = "Sistema"
    def emoji        = ":robot_face:"

    // Determinar quién lo ejecutó
    try {
        def userCause = currentBuild.rawBuild.getCauses().find { it instanceof hudson.model.Cause$UserIdCause }
        if (userCause) {
            triggeredBy = userCause.userName
            emoji = triggeredBy.toLowerCase() in ['admin', 'andres fornaris'] ? ":crown:" : ":bust_in_silhouette:"
        } else {
            def gitAuthor = sh(script: "git log -1 --pretty=format:'%an'", returnStdout: true).trim()
            if (gitAuthor) {
                triggeredBy = "Git Push por ${gitAuthor}"
                emoji = ":octocat:"
            }
        }
    } catch (e) {
        triggeredBy = "Desconocido"
    }

    // Detectar si el build está iniciando
    def isBuilding = (currentBuild.result == null || (currentBuild.result == 'SUCCESS' && currentBuild.duration == 0))

    // Construir mensaje principal
    def message = "*${emoji} ${jobName}* #${buildNumber}"
    if (isBuilding) {
        message += " ha iniciado"
    } else {
        message += " terminó con estado: *${result}*"

        // Calcular duración
        def durationSeconds = (currentBuild.duration ?: 0) / 1000
        def minutes = (int)(durationSeconds / 60)
        def seconds = (int)(durationSeconds % 60)
        message += "\n:stopwatch: Duración: ${minutes}m ${seconds}s"
    }

    // Mostrar errores si falla y está habilitado includeLog (true por defecto)
    if (result == 'FAILURE' && includeLog) {
        try {
            def rawLog = currentBuild.rawBuild.getLog(200)
            def errorLines = rawLog.findAll { it =~ /(?i)(error|exception|fail)/ }
            def logSnippet = errorLines ? errorLines.join('\n') : rawLog.takeRight(20).join('\n')
            message += "\n```" + logSnippet.take(1000) + "```"
        } catch (e) {
            message += "\n_(No se pudo extraer el log de error)_"
        }
    }

    message += "\n👤 Desplegado por: *${triggeredBy}* (<${buildUrl}|Ver ejecución>)"

    // Enviar a Slack
    slackSend(
        channel: channel,
        color: (isBuilding ? '#FBBF24' : (result == 'FAILURE' ? '#FF0000' : color)),
        message: message
    )
}
