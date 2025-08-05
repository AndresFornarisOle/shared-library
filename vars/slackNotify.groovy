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

    // 🔑 Detectar inicio sin variables globales usando parámetros del build
    def isStart = !currentBuild.rawBuild.getAction(hudson.model.ParametersAction)?.getParameter('PIPELINE_STARTED')
    if (isStart) {
        currentBuild.rawBuild.addAction(new hudson.model.ParametersAction(
            new hudson.model.StringParameterValue('PIPELINE_STARTED', 'true')
        ))
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

    // 🔎 Detección de etapa fallida
    def failedStage = "No detectada"
    if (!isStart && result == 'FAILURE') {
        try {
            def flowNodes = currentBuild.rawBuild?.getExecution()?.getCurrentHeads()*.getExecution()?.getNodes()?.flatten()
            def errorNodes = flowNodes.findAll { it.getError() != null }
            if (errorNodes) {
                def firstFailedNode = errorNodes.first()
                failedStage = firstFailedNode.getDisplayName()
            }
        } catch (e) {
            failedStage = "No detectada"
        }
    }

    // 🔎 Logs si falla
    if (includeLog && !isStart && result == 'FAILURE') {
        try {
            def rawLog = currentBuild.rawBuild.getLog(5000)
            def errorLines = rawLog.findAll { it =~ /(?i)(error|exception|failed|traceback|unknown revision)/ }

            if (errorLines) {
                def lastErrorLine = errorLines.last() // Tomar el ÚLTIMO error real
                def errorIndex = rawLog.indexOf(lastErrorLine)

                // Capturamos contexto alrededor del error
                def start = Math.max(0, errorIndex - 20)
                def end = Math.min(rawLog.size() - 1, errorIndex + 20)

                def context = rawLog[start..end].collect { line ->
                    (line =~ /(?i)(error|exception|failed|traceback|unknown revision)/) ? "👉 ${line}" : line
                }

                message += "\n:boom: *Falló en la etapa:* `${failedStage}`"
                message += "\n```🔎 Primer error detectado:\n${context.join('\n').take(2000)}\n```"
            } else {
                message += "\n:boom: *Falló en la etapa:* `${failedStage}`"
                message += "\n```(No se detectó error específico)\n${rawLog.takeRight(100).join('\n')}```"
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
