def call(Map config = [:]) {
    def channel    = config.channel ?: '#tech-deploys'
    def color      = config.color ?: 'good'
    def includeLog = config.includeLog ?: false
    def result     = currentBuild.currentResult ?: 'UNKNOWN'
    def buildUrl   = env.BUILD_URL ?: ''
    def jobName    = env.JOB_NAME ?: ''
    def buildNumber = env.BUILD_NUMBER ?: ''
    def triggeredBy = "Sistema"
    def emoji = ":robot_face:"

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

    def message = "*${emoji} ${jobName}* #${buildNumber} terminó con estado: *${result}*"

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

    message += "\n👤 Desplegado por: *${triggeredBy}*"
    message += " (<${buildUrl}|Ver ejecución>)"

    slackSend(
        channel: channel,
        color: color,
        message: message
    )
}
