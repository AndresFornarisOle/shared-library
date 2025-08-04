def call(Map config = [:]) {
    def channel      = config.channel ?: '#tech-deploys'
    def includeLog   = config.containsKey('includeLog') ? config.includeLog : true // Por defecto TRUE
    def showStatus   = config.get('showStatus', true)
    def buildUrl     = env.BUILD_URL ?: ''
    def jobName      = env.JOB_NAME ?: ''
    def buildNumber  = env.BUILD_NUMBER ?: ''
    def result       = currentBuild.currentResult ?: 'UNKNOWN'
    def triggeredBy  = "Sistema"
    def emoji        = ":robot_face:"
    def color        = config.color ?: getColorForResult(result, showStatus) // Color dinámico según estado

    // Determinar quién ejecutó el build
    try {
        def userCause = currentBuild.rawBuild.getCauses().find { it instanceof hudson.model.Cause$UserIdCause }
        if (userCause) {
            triggeredBy = userCause.userName
            emoji = triggeredBy.toLowerCase() in ['admin', 'andres fornaris'] ? ":crown:" : ":bust_in_silhouette:"
        } else {
            def gitAuthor = sh(script: "git log -1 --pretty=format:'%an'", returnStdout: true).trim()
            if (gitAuthor) {
                triggeredBy = "Git Push por ${gitAuthor}"
                emoji = ":male-technologist:" // Emoji nativo de Slack
            }
        }
    } catch (e) {
        triggeredBy = "Desconocido"
    }

    // Construir mensaje inicial/final
    def message = "*${emoji} ${jobName}* #${buildNumber}"

    if (showStatus) {
        // Estado dinámico (SUCCESS, FAILURE, ABORTED, UNKNOWN)
        if (result == 'ABORTED') {
            message += " fue *CANCELADO* manualmente"
        } else {
            message += " terminó con estado: *${result}*"
        }

        // Calcular duración solo si es cierre
        try {
            def durationMillis = currentBuild.duration ?: 0
            def totalSeconds = (durationMillis / 1000).toLong()
            def minutes = (totalSeconds / 60).toLong()
            def seconds = (totalSeconds % 60).toLong()
            message += "\n:stopwatch: *Duración:* ${minutes}m ${seconds}s"
        } catch (ignored) { }
    } else {
        message += " ha iniciado"
    }

    // Adjuntar logs si falla
    if (includeLog && result == 'FAILURE' && showStatus) {
        try {
            def rawLog = currentBuild.rawBuild.getLog(100)
            def errorLines = rawLog.findAll { it =~ /(?i)(error|exception|fail)/ }
            def logSnippet = errorLines ? errorLines.join('\n') : rawLog.takeRight(20).join('\n')
            message += "\n```" + logSnippet.take(1000) + "```"
        } catch (e) {
            message += "\n_(No se pudo extraer el log de error)_"
        }
    }

    // Añadir usuario al mensaje
    message += "\n👤 Desplegado por: *${triggeredBy}* (<${buildUrl}|Ver ejecución>)"

    // Enviar a Slack
    slackSend(
        channel: channel,
        color: color,
        message: message
    )
}

// Determinar color según el resultado
def getColorForResult(result, showStatus) {
    if (!showStatus) return '#FBBF24' // Inicio (amarillo)
    switch (result) {
        case 'SUCCESS': return 'good'     // Verde
        case 'FAILURE': return 'danger'   // Rojo
        case 'ABORTED': return '#808080'  // Gris
        default:        return '#439FE0'  // Azul para UNKNOWN u otros
    }
}
