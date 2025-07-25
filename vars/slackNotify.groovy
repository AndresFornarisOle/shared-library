def call(Map config = [:]) {
    def channel = config.channel ?: '#tech-deploys'
    def color = config.color ?: 'good'
    def message = config.message ?: "📦 Job `${env.JOB_NAME}` #${env.BUILD_NUMBER} terminó con estado: *${currentBuild.currentResult}*"
    def includeLog = config.includeLog ?: false
    def user = currentBuild.rawBuild.getCause(hudson.model.Cause$UserIdCause)?.userName ?: "Sistema o Trigger automático"

    // Adjuntar log si hubo error
    if (includeLog && currentBuild.currentResult == 'FAILURE') {
        try {
            def rawLog = currentBuild.rawBuild.getLog(100)
            def errorLines = rawLog.findAll { it =~ /(?i)(error|exception|fail)/ }
            def logSnippet = errorLines ? errorLines.join('\n') : rawLog.takeRight(20).join('\n')
            logSnippet = logSnippet.take(1000)
            message += "\n```" + logSnippet + "```"
        } catch (e) {
            message += "\n_(No se pudo extraer el log de error)_"
        }
    }

    // Agregar nombre de usuario al final
    message += "\n👤 Desplegado por: *${user}*"

    slackSend(
        channel: channel,
        color: color,
        message: "${message} (<${env.BUILD_URL}|Ver ejecución>)"
    )
}
