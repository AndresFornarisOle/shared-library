def call(Map config = [:]) {
    def channel      = config.channel ?: '#tech-deploys'
    def color        = config.color ?: 'good'
    def includeLog   = config.includeLog ?: false
    def showStatus   = config.get('showStatus', true)  // Flag para diferenciar inicio/fin
    def buildUrl     = env.BUILD_URL ?: ''
    def jobName      = env.JOB_NAME ?: ''
    def buildNumber  = env.BUILD_NUMBER ?: ''
    def result       = currentBuild.currentResult ?: 'UNKNOWN'
    def triggeredBy  = "Sistema"
    def emoji        = ":robot_face:"
    def durationStr  = ""

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

    // Mensaje base
    def message = "*${emoji} ${jobName}* #${buildNumber}"

    if (showStatus) {
        result = currentBuild.currentResult ?: 'UNKNOWN'
        message += " terminó con estado: *${result}*"

        // Calcular duración SOLO al finalizar
        if (result in ['SUCCESS', 'FAILURE', 'UNSTABLE']) {
            def durationMs = (currentBuild.duration ?: 0).toLong()  // Convertir a long
            def minutes = (durationMs / 1000 / 60) as int
            def seconds = ((durationMs / 1000) % 60) as int
            durationStr = "\n⏱️ *Duración:* ${minutes}m ${seconds}s"
        }
    } else {
        message += " ha iniciado"
    }

    // Logs en caso de fallo
    if (includeLog && result == 'FAILURE') {
        try {
            def rawLog = currentBuild.rawBuild.getLog(100)
            def errorLines = rawLog.findAll { it =~ /(?i)(error|exception|fail)/ }
            def logSnippet = errorLines ? errorLines.join('\n') : rawLog.takeRight(20).join('\n')
            message += "\n```" + logSnippet.take(1000) + "```"
        } catch (e) {
            message += "\n_(No se pudo extraer el log de error)_"
        }
    }

    // Agregar info de quién lo disparó y duración
    message += "${durationStr}\n👤 Desplegado por: *${triggeredBy}* (<${buildUrl}|Ver ejecución>)"

    // Enviar mensaje a Slack
    slackSend(
        channel: channel,
        color: color,
        message: message
    )
}
