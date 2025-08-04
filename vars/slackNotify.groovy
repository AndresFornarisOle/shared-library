def call(Map config = [:]) {
    def channel      = config.channel ?: '#tech-deploys'
    def color        = config.color ?: 'good'
    def includeLog   = config.includeLog != null ? config.includeLog : true
    def showStatus   = config.get('showStatus', null)  // Si no se pasa, detectamos automáticamente
    def result       = currentBuild.currentResult
    def buildUrl     = env.BUILD_URL ?: ''
    def jobName      = env.JOB_NAME ?: ''
    def buildNumber  = env.BUILD_NUMBER ?: ''
    def triggeredBy  = "Sistema"
    def emoji        = ":robot_face:"

    // 🔥 Detectar si estamos en INICIO (fuera de post actions)
    def isPostAction = currentBuild.rawBuild.execution.currentHeads*.displayName.any { it == "Declarative: Post Actions" }
    if (!isPostAction && showStatus == null) {
        showStatus = false  // Forzamos inicio
    } else if (showStatus == null) {
        showStatus = true   // Post actions: mostramos estado real
    }

    // Determinar quién ejecutó el pipeline
    try {
        def userCause = currentBuild.rawBuild.getCauses().find { it instanceof hudson.model.Cause$UserIdCause }
        if (userCause) {
            triggeredBy = userCause.userName
            emoji = ":bust_in_silhouette:"
        } else {
            def gitAuthor = sh(script: "git log -1 --pretty=format:'%an'", returnStdout: true).trim()
            if (gitAuthor) {
                triggeredBy = "Git Push por ${gitAuthor}"
                emoji = ":technologist:"  // Alternativa nativa de Slack
            }
        }
    } catch (e) {
        triggeredBy = "Desconocido"
    }

    // Ajustar emoji y color según estado
    if (!showStatus) {
        emoji = ":rocket:"
        color = "#FBBF24"
    } else {
        switch (result) {
            case 'SUCCESS':
                emoji = ":white_check_mark:"
                color = "good"
                break
            case 'FAILURE':
                emoji = ":x:"
                color = "danger"
                break
            case 'ABORTED':
                emoji = ":no_entry:"
                color = "#AAAAAA"
                break
        }
    }

    // Construir mensaje
    def message = "*${emoji} ${jobName}* #${buildNumber}"
    if (!showStatus) {
        message += " ha iniciado"
    } else {
        message += " terminó con estado: *${result}*"
    }

    // Duración solo si es post
    if (showStatus && binding.hasVariable('buildStartTime')) {
        def buildEndTime = System.currentTimeMillis()
        def totalSeconds = ((buildEndTime - binding.getVariable('buildStartTime')) / 1000) as long
        def duration = "${(totalSeconds / 60).intValue()}m ${(totalSeconds % 60).intValue()}s"
        message += "\n:stopwatch: *Duración:* ${duration}"
    }

    // Logs en caso de fallo
    if (includeLog && showStatus && result == 'FAILURE') {
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

    slackSend(
        channel: channel,
        color: color,
        message: message
    )
}
