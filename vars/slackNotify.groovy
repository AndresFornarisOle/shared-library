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

    // 🏷️ ID único por build para evitar conflictos entre ejecuciones simultáneas
    def buildId = "${env.JOB_NAME}-${env.BUILD_NUMBER}"
    if (!binding.hasVariable("PIPELINE_FLAGS")) {
        binding.setVariable("PIPELINE_FLAGS", [:])
    }
    def flags = binding.getVariable("PIPELINE_FLAGS")

    // 🔑 Detectar inicio usando flag único por build
    def isStart = !flags.containsKey(buildId)
    if (isStart) {
        flags[buildId] = true
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

    // 🔎 Extraer logs si falla
    if (includeLog && !isStart && result == 'FAILURE') {
        try {
            def rawLog = currentBuild.rawBuild.getLog(4000) // Capturamos más líneas del log
            def errorPattern = ~/(?i)(error|exception|failed|traceback|unknown revision)/
            def errorIndexes = []

            // Buscar todos los índices de error
            rawLog.eachWithIndex { line, idx ->
                if (line =~ errorPattern) {
                    errorIndexes << idx
                }
            }

            if (!errorIndexes.isEmpty()) {
                def errorBlocks = []
                errorIndexes.each { startIdx ->
                    def block = []
                    for (int i = startIdx; i < rawLog.size() && block.size() < 200; i++) {
                        def line = rawLog[i]
                        block << line
                        if (line.trim().isEmpty()) break // cortar en línea vacía
                    }
                    if (!block.isEmpty()) {
                        block[0] = "👉 ${block[0]}" // resaltar la primera línea del error
                    }
                    errorBlocks << block.join('\n')
                }

                // Combinar todos los bloques y limitar a 2000 caracteres para Slack
                def combinedErrors = errorBlocks.join("\n\n---\n\n").take(2000)
                message += "\n```🔎 Errores detectados:\n${combinedErrors}\n```"
            } else {
                message += "\n```(No se detectó error específico)\n${rawLog.takeRight(100).join('\n')}```"
            }
        } catch (e) {
            message += "\n_(No se pudo extraer el log de error)_"
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
