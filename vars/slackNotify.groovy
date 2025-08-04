def call(Map config = [:]) {
    def channel      = config.channel ?: '#tech-deploys'
    def color        = config.color ?: 'good'
    def includeLog   = config.includeLog ?: true
    def result       = currentBuild.currentResult
    def buildUrl     = env.BUILD_URL ?: ''
    def jobName      = env.JOB_NAME ?: ''
    def buildNumber  = env.BUILD_NUMBER ?: ''
    def triggeredBy  = "Sistema"
    def emoji        = ":robot_face:"

    // 🔥 Forzar estado "STARTING" si no hay etapas previas ejecutadas
    if (!currentBuild.rawBuild.getExecution().getCurrentHeads().any { it.execution != null && it.getDisplayName() != 'Declarative: Post Actions' }) {
        result = 'STARTING'
    }

    // Determinar quién ejecutó el pipeline
    try {
        def userCause = currentBuild.rawBuild.getCauses().find { it instanceof hudson.model.Cause$UserIdCause }
        if (userCause) {
            triggeredBy = userCause.userName
            emoji = ":bust_in_silhouette:" // Usuario
        } else {
            def gitAuthor = sh(script: "git log -1 --pretty=format:'%an'", returnStdout: true).trim()
            if (gitAuthor) {
                triggeredBy = "Git Push por ${gitAuthor}"
                emoji = ":git:" // Commit
            }
        }
    } catch (e) {
        triggeredBy = "Desconocido"
    }

    // Ajustar emoji y color según estado
    switch (result) {
        case 'STARTING':
            emoji = ":rocket:"
            color = "#FBBF24"
            break
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

    // Construir mensaje
    def message = "*${emoji} ${jobName}* #${buildNumber}"
    if (result == 'STARTING') {
        message += " ha iniciado"
    } else {
        message += " terminó con estado: *${result}*"
    }

    // Duración solo en post-actions
    if (result != 'STARTING' && binding.hasVariable('buildStartTime')) {
        def buildEndTime = System.currentTimeMillis()
        def totalSeconds = ((buildEndTime - binding.getVariable('buildStartTime')) / 1000) as long
        def duration = "${(totalSeconds / 60).intValue()}m ${(totalSeconds % 60).intValue()}s"
        message += "\n:stopwatch: *Duración:* ${duration}"
    }

    // Logs si es fallo
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

    // Footer con usuario y link
    message += "\n👤 Desplegado por: *${triggeredBy}* (<${buildUrl}|Ver ejecución>)"

    slackSend(
        channel: channel,
        color: color,
        message: message
    )
}
