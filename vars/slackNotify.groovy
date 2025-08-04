def call(Map config = [:]) {
    def channel      = config.channel ?: '#tech-deploys'
    def color        = config.color ?: 'good'
    def includeLog   = config.includeLog != null ? config.includeLog : true // 🔥 Por defecto true
    def isStartMsg   = config.containsKey('showStatus') ? !config.showStatus : false // 🔥 Detecta inicio sin estado
    def buildUrl     = env.BUILD_URL ?: ''
    def jobName      = env.JOB_NAME ?: ''
    def buildNumber  = env.BUILD_NUMBER ?: ''
    def result       = currentBuild.currentResult ?: 'UNKNOWN'
    def triggeredBy  = "Sistema"
    def emoji        = ":robot_face:"
    def buildDuration = ""

    // ⏱️ Calcular duración solo si NO es inicio
    if (!isStartMsg && result != 'UNKNOWN') {
        def durationMillis = currentBuild.duration ?: 0
        def totalSeconds = (durationMillis / 1000) as long
        buildDuration = "${(totalSeconds / 60).intValue()}m ${(totalSeconds % 60).intValue()}s"
    }

    // 👤 Determinar quién ejecutó el pipeline
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

    // 📝 Construir mensaje
    def message = "*${emoji} ${jobName}* #${buildNumber}"

    if (isStartMsg) {
        message += " ha iniciado"
    } else {
        message += " terminó con estado: *${result}*"
        if (buildDuration) {
            message += "\n:stopwatch: *Duración:* ${buildDuration}"
        }
    }

    // 🔎 Extraer logs solo si falló
    if (includeLog && !isStartMsg && result == 'FAILURE') {
        try {
            def rawLog = currentBuild.rawBuild.getLog(1000)
            def errorIndex = rawLog.findIndexOf { it =~ /(?i)(error|exception|failed|traceback)/ }

            if (errorIndex != -1) {
                def start = Math.max(0, errorIndex - 20)
                def end = Math.min(rawLog.size() - 1, errorIndex + 20)
                def contextLog = rawLog[start..end].join('\n')
                message += "\n```🔎 Posible error detectado:\n${contextLog.take(1000)}\n```"
            } else {
                message += "\n```(No se detectó error específico)\n${rawLog.takeRight(50).join('\n')}```"
            }
        } catch (e) {
            message += "\n_(No se pudo extraer el log de error)_"
        }
    }

    // 👤 Autor de ejecución y enlace
    message += "\n:adult: Desplegado por: *${triggeredBy}* (<${buildUrl}|Ver ejecución>)"

    // 📤 Enviar a Slack
    slackSend(
        channel: channel,
        color: (isStartMsg ? '#FBBF24' : color),
        message: message
    )
}
