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

    // 🔑 Detectar inicio
    def isStart = (env.PIPELINE_STARTED == null)
    if (isStart) env.PIPELINE_STARTED = "true"

    // 🕑 Calcular duración
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

    // 📝 Mensaje inicial/final
    def message = ""
    if (isStart) {
        message = ":rocket: *${jobName}* #${buildNumber} ha iniciado\n:adult: Desplegado por: *${triggeredBy}* (<${buildUrl}|Ver ejecución>)"
    } else {
        message = "*${emoji} ${jobName}* #${buildNumber} terminó con estado: *${result}*"
        if (buildDuration) message += "\n:stopwatch: *Duración:* ${buildDuration}"
        message += "\n:adult: Desplegado por: *${triggeredBy}* (<${buildUrl}|Ver ejecución>)"
    }

    // 🔎 Extraer PRIMERA etapa fallida y error raíz
    if (includeLog && !isStart && result == 'FAILURE') {
        try {
            def rawLog = currentBuild.rawBuild.getLog(8000)
            def failedStage = "No detectada"

            // 1️⃣ Detectar primera ocurrencia de "skipped"
            def skipIndex = rawLog.findIndexOf { it =~ /skipped due to earlier failure/ }
            if (skipIndex > 0) {
                // Buscar las dos últimas líneas con formato de etapa antes de skip
                def stagesFound = []
                for (int i = skipIndex - 1; i >= 0; i--) {
                    def match = (rawLog[i] =~ /\[Pipeline\] \{ \((.+)\)/)
                    if (match) {
                        stagesFound << match[0][1]
                        if (stagesFound.size() == 2) break
                    }
                }
                // Tomar la inmediatamente anterior (segunda encontrada)
                if (stagesFound.size() >= 2) {
                    failedStage = stagesFound[1]
                } else if (stagesFound) {
                    failedStage = stagesFound[0]
                }
            }

            // 2️⃣ Buscar error raíz (última ocurrencia relevante)
            def errorPattern = ~/(?i)(unknown revision|error:|exception|failed|traceback)/
            def allErrors = rawLog.findIndexValues { it =~ errorPattern }
            def errorIndex = allErrors ? allErrors.last() : -1

            if (errorIndex != -1) {
                def start = Math.max(0, errorIndex - 5)
                def end = Math.min(rawLog.size() - 1, errorIndex + 15)
                message += "\n:boom: *Falló en la etapa:* `${failedStage}`"
                message += "\n```🔎 Raíz del error:\n${rawLog[start..end].join('\n').take(3000)}\n```"
            } else {
                message += "\n:boom: *Falló en la etapa:* `${failedStage}`"
                message += "\n```(No se detectó error específico)\n${rawLog.takeRight(120).join('\n')}```"
            }
        } catch (e) {
            message += "\n_(No se pudo extraer el log ni la etapa fallida)_"
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
    slackSend(channel: channel, color: color, message: message)
}
