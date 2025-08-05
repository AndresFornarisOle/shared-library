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

    // 🔑 Detectar inicio usando una variable del entorno del pipeline actual
    def isStart = (env.PIPELINE_STARTED == null)
    if (isStart) {
        env.PIPELINE_STARTED = "true"
    }

    // 🕑 Calcular duración solo si no es inicio
    def buildDuration = ""
    if (!isStart && result != 'UNKNOWN') {
        def durationMillis = currentBuild.duration ?: 0
        def totalSeconds = (durationMillis / 1000).toLong()
        buildDuration = "${(totalSeconds / 60).intValue()}m ${(totalSeconds % 60).intValue()}s"
    }

    // 👤 Determinar quién disparó el pipeline (por usuario o por Git Push)
    try {
        def userCause = currentBuild.rawBuild.getCauses().find { it instanceof hudson.model.Cause$UserIdCause }
        if (userCause) {
            triggeredBy = userCause.userName
            emoji = triggeredBy.toLowerCase() in ['admin', 'andres fornaris'] ? ":crown:" : ":bust_in_silhouette:"
        } else {
            // Detectar si fue un Git Push
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
            def rawLog = currentBuild.rawBuild.getLog(3000)
            def errorIndex = rawLog.findIndexOf { it =~ /(?i)(error|exception|failed|traceback|unknown revision)/ }
            if (errorIndex != -1) {
                def start = Math.max(0, errorIndex - 20)
                def end = Math.min(rawLog.size() - 1, errorIndex + 30)
                def highlighted = rawLog[start..end].collect { line ->
                    (line == rawLog[errorIndex]) ? "👉 ${line}" : line
                }
                message += "\n:boom: *Falló en la etapa:* `${env.FAILED_STAGE ?: 'No detectada'}`"
                message += "\n```🔎 Primer error detectado:\n${highlighted.join('\n').take(2000)}\n```"
            } else {
                message += "\n```(No se detectó error específico)\n${rawLog.takeRight(100).join('\n')}```"
            }
        } catch (e) {
            message += "\n_(No se pudo extraer el log de error)_"
        }
    }

    // 🎨 Color según estado
    if (isStart) {
        color = "#FBBF24" // Amarillo inicio
    } else if (result == 'FAILURE') {
        color = "danger"
    } else if (result == 'ABORTED') {
        color = "#808080"
    } else {
        color = "good"
    }

    // 📢 Enviar a Slack
    slackSend(channel: channel, color: color, message: message)
}
