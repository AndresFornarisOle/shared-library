
package org.olelife

class SlackHelper implements Serializable {

    static String buildMessage(String jobName, String buildNumber, String buildUrl, boolean success) {
        def statusEmoji = success ? "✅" : "❌"
        def statusText = success ? "exitosamente" : "fallado"
        return "${statusEmoji} Job `${jobName}` #${buildNumber} ha ${statusText}. <${buildUrl}|Ver detalles>"
    }
}
