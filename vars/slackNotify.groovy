def call(Map config = [:]) {
    def channel      = config.channel ?: '#tech-deploys'
    def color        = config.color ?: 'good'
    def includeLog   = config.includeLog != null ? config.includeLog : true  // Por defecto en true
    def showStatus   = config.get('showStatus', true)
    def buildUrl     = env.BUILD_URL ?: ''
    def jobName      = env.JOB_NAME ?: ''
    def buildNumber  = env.BUILD_NUMBER ?: ''
    def result       = currentBuild.currentResult ?: 'UNKNOWN'
    def triggeredBy  = "Sistema"
    def emoji        = ":computer:"  // Por defecto ejecución automática

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
                emoji = ":rocket:" // Cambio de octocat por rocket para inicio vía Git
            }
        }
    } catch (e) {
        triggeredBy = "Desconocido"
    }

    // Construcción del mensaje
    def message = "*${emoji} ${jobName}* #${buildNumber}"

    if (showStatus) {
        if (result == 'SUCCESS') {
            emoji = ":white_check_mark:"
        } else if (result == 'FAILURE') {
            emoji = ":x:"
        } else if (result == 'UNSTABLE') {
            emoji = ":warning:"
        } else {
            emoji = ":hourglass_flowing_sand:" // Estado desconocido o en progreso
        }
        message = "*${emoji} ${jobName}* #${buildNumber} terminó con estado: *${result}*"
    } else {
        message += " ha iniciado :rocket:"
    }

    // Incluir log en caso de fallo
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

    // Tiempo de ejecución
    def duration = ''
    try {
        def durationMillis = currentBuild.duration ?: 0
        def totalSeconds = (durationMillis / 1000) as long
        duration = "\n:stopwatch: *Duración:* ${(totalSeconds / 60).intValue()}m ${(totalSeconds % 60).intValue()}s"
    } catch (ignored) { }

    message += duration
    message += "\n👤 Desplegado por: *${triggeredBy}* (<${buildUrl}|Ver ejecución>)"

    // Enviar a Slack
    slackSend(
        channel: channel,
        color: color,
        message: message
    )
}
