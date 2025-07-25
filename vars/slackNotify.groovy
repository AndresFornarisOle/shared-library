
def call(Map config = [:]) {
    def channel = config.channel ?: '#tech-deploys'
    def color = config.color ?: 'good'
    def message = config.message ?: "Mensaje por defecto de notificación."

    slackSend(
        channel: channel,
        color: color,
        message: message
    )
}
