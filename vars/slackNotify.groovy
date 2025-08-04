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

    // 🔑 Detectar inicio usando flag global persistente
    def isStart = (env.PIPELINE_STARTED == null)
    if (isStart) {
        env.PIPELINE_STARTED = "true"  // Se marca solo la primera vez
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
            emoji = triggeredBy.toLowerCase() in ['admin', 'andres fornaris'] ? ":crown:" : ":adult:"
        } else {
            def gitAuthor = sh(script: "git log -1 --pretty=format:'%an'", returnStdout: true).trim()
            if (gitAuthor) {
                triggeredBy = "Git Push por ${gitAuthor}"
                emoji = ":bust_in_silhouette:"
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

    // 🔎 Extraer logs si falla (PRIMER error encontrado)
    if (includeLog && !isStart && result == 'FAILURE') {
        try {
            def rawLog = currentBuild.rawBuild.getLog(3000) // 🔥 más líneas
            def keywords = [~/(?i)error/, ~/(?i)exception/, ~/(?i)failed/, ~/(?i)traceback/, ~/(?i)unknown revision/]

            def errorIndex = rawLog.findIndexOf { line -> keywords.any { pattern -> line =~ pattern } }
            if (errorIndex != -1) {
                def start = Math.max(0, errorIndex - 15) // contexto previo
                def end = Math.min(rawLog.size() - 1, errorIndex + 25) // contexto posterior
                def errorSnippet = rawLog[start..end].join('\n').take(2000)
                message += "\n```🔎 Primer error detectado:\n${errorSnippet}\n```"
            } else {
                message += "\n```(No se detectó error específico)\n${rawLog.takeRight(120).join('\n')}```"
            }
        } catch (e) {
            message += "\n_(No se pudo extraer el log de error: ${e.message})_"
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
