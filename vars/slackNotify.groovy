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

    // 🏷️ Usar variable asociada a currentBuild para evitar reinicios en post
    if (!currentBuild.hasAction(ParametersAction)) {
        currentBuild.addAction(new ParametersAction(new StringParameterValue("PIPELINE_STARTED", "false")))
    }
    def param = currentBuild.getAction(ParametersAction).getParameter("PIPELINE_STARTED")
    def isStart = (param?.value == "false")

    if (isStart) {
        currentBuild.actions.removeAll { it instanceof ParametersAction }
        currentBuild.addAction(new ParametersAction(new StringParameterValue("PIPELINE_STARTED", "true")))
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

    // 🔎 Logs si falla
    if (includeLog && !isStart && result == 'FAILURE') {
        try {
            def rawLog = currentBuild.rawBuild.getLog(4000)
            def errorPattern = ~/(?i)(error|exception|failed|traceback|unknown revision)/
            def errorIndexes = []
            rawLog.eachWithIndex { line, idx -> if (line =~ errorPattern) errorIndexes << idx }

            if (!errorIndexes.isEmpty()) {
                def errorBlocks = []
                errorIndexes.each { idx ->
                    def block = []
                    for (int i = idx; i < rawLog.size() && block.size() < 200; i++) {
                        def line = rawLog[i]
                        block << (i == idx ? "👉 ${line}" : line)
                        if (line.trim().isEmpty()) break
                    }
                    errorBlocks << block.join('\n')
                }
                message += "\n```🔎 Errores detectados:\n${errorBlocks.join('\n\n---\n\n').take(2000)}\n```"
            } else {
                message += "\n```(No se detectó error específico)\n${rawLog.takeRight(100).join('\n')}```"
            }
        } catch (e) {
            message += "\n_(No se pudo extraer el log de error)_"
        }
    }

    // 🎨 Color según estado
    if (isStart) color = "#FBBF24"
    else if (result == 'FAILURE') color = "danger"
    else if (result == 'ABORTED') color = "#808080"
    else color = "good"

    // 📢 Enviar a Slack
    slackSend(channel: channel, color: color, message: message)
}
