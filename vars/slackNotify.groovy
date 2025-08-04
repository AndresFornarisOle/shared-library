def call(Map config = [:]) {
    def channel      = config.channel ?: '#tech-deploys'
    def color        = config.color ?: 'good'
    def includeLog   = config.includeLog != null ? config.includeLog : true  // 🔥 Siempre true por defecto
    def showStatus   = config.get('showStatus', true)
    def buildUrl     = env.BUILD_URL ?: ''
    def jobName      = env.JOB_NAME ?: ''
    def buildNumber  = env.BUILD_NUMBER ?: ''
    def result       = currentBuild.currentResult ?: 'UNKNOWN'
    def duration     = ''
    def triggeredBy  = "Sistema"
    def emoji        = ":robot_face:"
    def buildStart   = currentBuild.startTimeInMillis ?: System.currentTimeMillis()
    def buildEnd     = System.currentTimeMillis()
    def totalSeconds = ((buildEnd - buildStart) / 1000) as long
    def isStart      = (totalSeconds < 1)  // 🔥 Detectar inicio REAL del pipeline

    // Calcular duración solo si no es inicio
    if (!isStart) {
        duration = "${(totalSeconds / 60).intValue()}m ${(totalSeconds % 60).intValue()}s"
    }

    // Determinar quién ejecutó el build
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

    // Construir mensaje
    def message = "*${emoji} ${jobName}* #${buildNumber}"

    if (isStart) {
        message += " ha iniciado"
    } else {
        message += " terminó con estado: *${result}*"
        message += "\n:stopwatch: *Duración:* ${duration}"
    }

    message += "\n:person_with_blond_hair: *Desplegado por:* ${triggeredBy} (<${buildUrl}|Ver ejecución>)"

    // Adjuntar logs si falla y no es inicio
    if (includeLog && !isStart && result == 'FAILURE') {
        try {
            def rawLog = currentBuild.rawBuild.getLog(3000) // 🔥 Aumentamos tamaño
            def errorLines = []
            def keywords = [~/error/i, ~/exception/i, ~/failed/i, ~/traceback/i, ~/unknown revision/i]

            // Detectar el último stage ejecutado antes del fallo
            def failedStage = rawLog.findAll { it =~ /\[Pipeline\] \{ \((.*?)\)/ }
                                       .collect { it.replaceAll(/.*\((.*?)\).*/, '$1') }
                                       .findAll { it && !it.toLowerCase().contains("declarative") }
                                       .last()

            if (failedStage) {
                message += "\n\n:triangular_flag_on_post: *Stage con fallo:* `${failedStage}`"
            }

            // Buscar errores con contexto
            rawLog.eachWithIndex { line, idx ->
                if (keywords.any { pattern -> line =~ pattern }) {
                    def start = Math.max(0, idx - 5)
                    def end = Math.min(rawLog.size() - 1, idx + 15)
                    errorLines += rawLog[start..end]
                }
            }

            if (errorLines) {
                def uniqueErrors = errorLines.unique().join('\n').take(2000)
                message += "\n```🔎 Posibles errores detectados:\n${uniqueErrors}\n```"
            } else {
                def tailLog = rawLog.takeRight(120).join('\n')
                message += "\n```(No se detectaron errores específicos)\n${tailLog}```"
            }

        } catch (e) {
            message += "\n_(No se pudo extraer el log de error: ${e.message})_"
        }
    }

    // Enviar a Slack
    slackSend(
        channel: channel,
        color: (isStart ? '#FBBF24' : (result == 'SUCCESS' ? 'good' : 'danger')),
        message: message
    )
}
