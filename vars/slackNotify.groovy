def call(Map config = [:]) {
    def channel      = config.channel ?: '#tech-deploys'
    def color        = config.color ?: 'good'
    def includeLog   = config.includeLog != null ? config.includeLog : true
    def buildUrl     = env.BUILD_URL ?: ''
    def jobName      = env.JOB_NAME ?: ''
    def buildNumber  = env.BUILD_NUMBER ?: ''
    def result       = currentBuild.currentResult ?: 'UNKNOWN'
    def triggeredBy  = "Sistema"
    def emoji        = ":robot_face:"

    // 🔑 Detectar inicio usando flag global
    def isStart = (env.PIPELINE_STARTED == null)
    if (isStart) {
        env.PIPELINE_STARTED = "true" // Seteamos el flag solo en la primera llamada
    }

    // 🕑 Calcular duración solo si no es inicio
    def buildDuration = ""
    if (!isStart && result != 'UNKNOWN') {
        def durationMillis = currentBuild.duration ?: 0
        def totalSeconds = (durationMillis / 1000) as long
        buildDuration = "${(totalSeconds / 60).intValue()}m ${(totalSeconds % 60).intValue()}s"
    }

    // 👤 Determinar quién disparó el pipeline
    try {
        def userCause = currentBuild.rawBuild.getCauses().find { it instanceof hudson.model.Cause$UserIdCause }
        if (userCause) {
            triggeredBy = userCause.userName
            emoji = triggeredBy.toLowerCase() in ['admin', 'andres fornaris'] ? ":crown:" : ":bust_in_silhouette:"
        } else {
            def gitAuthor = sh(script: "git log -1 --pretty=format:'%an'", returnStdout: true).trim()
            if (gitAuthor) {
                triggeredBy = "Git Push por ${gitAuthor}"
                emoji = ":male-technologist:"
            }
        }
    } catch (e) {
        triggeredBy = "Desconocido"
    }

    // 📝 Construir mensaje según el caso
    def message = ""
    if (isStart) {
        message = ":rocket: *${jobName}* #${buildNumber} ha iniciado\n:adult: Desplegado por: *${triggeredBy}* (<${buildUrl}|Ver ejecución>)"
    } else {
        message = "*${emoji} ${jobName}* #${buildNumber} terminó con estado: *${result}*"
        if (buildDuration) message += "\n:stopwatch: *Duración:* ${buildDuration}"
        message += "\n:adult: Desplegado por: *${triggeredBy}* (<${buildUrl}|Ver ejecución>)"
    }

    // 🔎 Logs y detección de etapa fallida
    if (includeLog && !isStart && result == 'FAILURE') {
        try {
            // ✅ Detectar la PRIMERA etapa fallida
            def failedStage = "Desconocida"
            def flowGraph = currentBuild.rawBuild.getExecution().getCurrentHeads()[0].getExecution().getNodes()
            def stageNodes = flowGraph.findAll { it.displayFunctionName == 'Stage' }
            for (node in stageNodes) {
                def execActions = node.getActions(hudson.model.ErrorAction)
                if (!execActions.isEmpty()) {
                    failedStage = node.getDisplayName()
                    break
                }
            }

            // ✅ Extraer logs del error (primer a último match)
            def rawLog = currentBuild.rawBuild.getLog(4000)
            def errorPattern = ~/(?i)(error|exception|failed|traceback|unknown revision)/
            def matches = rawLog.findIndexValues { it =~ errorPattern }

            if (!matches.isEmpty()) {
                def firstErrorIndex = matches.first()
                def lastErrorIndex = matches.last()
                def start = Math.max(0, firstErrorIndex - 10)
                def end = Math.min(rawLog.size() - 1, lastErrorIndex + 30)

                message += "\n:boom: *Falló en la etapa:* `${failedStage}`"
                message += "\n```🔎 Error detectado:\n${rawLog[start..end].join('\n').take(3000)}\n```"
            } else {
                message += "\n:boom: *Falló en la etapa:* `${failedStage}`"
                message += "\n```(No se detectó error específico)\n${rawLog.takeRight(150).join('\n')}```"
            }
        } catch (e) {
            message += "\n_(No se pudo extraer el log de error ni la etapa fallida)_"
        }
    }

    // 🎨 Color según estado
    if (isStart) {
        color = "#FBBF24" // Amarillo para inicio
    } else if (result == 'FAILURE') {
        color = "danger"
    } else if (result == 'ABORTED') {
        color = "#808080"
    } else {
        color = "good"
    }

    // 📢 Enviar a Slack
    slackSend(
        channel: channel,
        color: color,
        message: message
    )
}
