def call(Map config = [:]) {
    def channel      = config.channel ?: '#tech-deploys'
    def color        = config.color ?: 'good'
    def includeLog   = config.includeLog ?: true
    def result       = currentBuild.currentResult ?: 'STARTING'  // Detecta si es inicio
    def showStatus   = (result != 'STARTING')  // Evita mostrar estado en inicio automáticamente
    def buildUrl     = env.BUILD_URL ?: ''
    def jobName      = env.JOB_NAME ?: ''
    def buildNumber  = env.BUILD_NUMBER ?: ''
    def triggeredBy  = "Sistema"
    def emoji        = ":robot_face:"

    // Determinar quién ejecutó el pipeline
    try {
        def userCause = currentBuild.rawBuild.getCauses().find { it instanceof hudson.model.Cause$UserIdCause }
        if (userCause) {
            triggeredBy = userCause.userName
            emoji = ":bust_in_silhouette:" // Emoji de usuario
        } else {
            def gitAuthor = sh(script: "git log -1 --pretty=format:'%an'", returnStdout: true).trim()
            if (gitAuthor) {
                triggeredBy = "Git Push por ${gitAuthor}"
                emoji = ":git:" // Emoji nativo para commits
            }
        }
    } catch (e) {
        triggeredBy = "Desconocido"
    }

    // Ajustar emoji según estado
    if (result == 'STARTING') { 
        emoji = ":rocket:" 
        color = "#FBBF24" 
    } else if (result == 'SUCCESS') { 
        emoji = ":white_check_mark:" 
        color = "good"
    } else if (result == 'FAILURE') { 
        emoji = ":x:" 
        color = "danger"
    } else if (result == 'ABORTED') { 
        emoji = ":no_entry:" 
        color = "#AAAAAA"
    }

    // Construir mensaje
    def message = "*${emoji} ${jobName}* #${buildNumber}"

    if (showStatus) {
        message += " terminó con estado: *${result}*"
    } else {
        message += " ha iniciado"
    }

    // Calcular duración solo si NO es inicio
    if (showStatus && binding.hasVariable('buildStartTime')) {
        def buildEndTime = System.currentTimeMillis()
        def totalSeconds = ((buildEndTime - binding.getVariable('buildStartTime')) / 1000) as long
        def duration = "${(totalSeconds / 60).intValue()}m ${(totalSeconds % 60).intValue()}s"
        message += "\n:stopwatch: *Duración:* ${duration}"
    }

    // Incluir logs si es fallo y está habilitado
    if (includeLog && result == 'FAILURE') {
        try {
            def rawLog = currentBuild.rawBuild.getLog(200)
            def errorLines = rawLog.findAll { it =~ /(?i)(error|exception|fail)/ }
            def logSnippet = errorLines ? errorLines.join('\n') : rawLog.takeRight(20).join('\n')
            message += "\n```" + logSnippet.take(1000) + "```"
        } catch (e) {
            message += "\n_(No se pudo extraer el log de error)_"
        }
    }

    // Agregar quién ejecutó y link a ejecución
    message += "\n👤 Desplegado por: *${triggeredBy}* (<${buildUrl}|Ver ejecución>)"

    // Enviar mensaje a Slack
    slackSend(
        channel: channel,
        color: color,
        message: message
    )
}
