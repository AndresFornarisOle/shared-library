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
        env.PIPELINE_STARTED = "true"
    }

    // 🕑 Calcular duración solo si no es inicio
    def buildDuration = ""
    if (!isStart && result != 'UNKNOWN') {
        def durationMillis = currentBuild.duration ?: 0
        def totalSeconds = (durationMillis / 1000) as long
        buildDuration = "${(totalSeconds / 60).intValue()}m ${(totalSeconds % 60).intValue()}s"
    }

    // 👤 Detección híbrida del usuario
    try {
        // 1️⃣ Prioridad: Usuario que ejecutó el build manualmente
        def userCause = currentBuild.rawBuild.getCauses().find { it instanceof hudson.model.Cause$UserIdCause }
        if (userCause) {
            triggeredBy = userCause.userName
        }
        // 2️⃣ Fallback: Variables de entorno del plugin Build User Vars
        else if (env.BUILD_USER?.trim()) {
            triggeredBy = env.BUILD_USER
        }
        // 3️⃣ Fallback: Autor del último commit
        else {
            def gitAuthor = sh(script: "git log -1 --pretty=format:'%an' || true", returnStdout: true).trim()
            if (gitAuthor) triggeredBy = "Git Push por ${gitAuthor}"
        }
    } catch (e) {
        triggeredBy = "Desconocido"
    }

    // 👑 Emoji según usuario
    emoji = (triggeredBy?.toLowerCase()?.contains("andres fornaris") || triggeredBy?.toLowerCase()?.contains("admin")) ? ":crown:" :
            (triggeredBy?.toLowerCase()?.contains("git push") ? ":male-technologist:" : ":bust_in_silhouette:")

    // 📝 Construir mensaje
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
            def rawLog = currentBuild.rawBuild.getLog(2000)
            def errorIndex = rawLog.findIndexOf { it =~ /(?i)(error|exception|failed|traceback|unknown revision)/ }
            if (errorIndex != -1) {
                def start = Math.max(0, errorIndex - 20)
                def end = Math.min(rawLog.size() - 1, errorIndex + 20)
                def highlightedLog = rawLog[start..end].collectIndexed { idx, line ->
                    idx + start == errorIndex ? "👉 ${line}" : line
                }.join('\n').take(2000)
                message += "\n```🔎 Primer error detectado:\n${highlightedLog}\n```"
            } else {
                message += "\n```(No se detectó error específico)\n${rawLog.takeRight(100).join('\n')}```"
            }
        } catch (e) {
            message += "\n_(No se pudo extraer el log de error)_"
        }
    }

    // 🎨 Color según estado
    if (isStart) {
        color = "#FBBF24"
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
