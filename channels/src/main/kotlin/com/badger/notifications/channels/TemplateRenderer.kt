package com.badger.notifications.channels

object TemplateRenderer {
    fun render(
        template: String,
        variables: Map<String, String>,
    ): String =
        variables.entries.fold(template) { acc, (k, v) ->
            acc.replace("{{$k}}", v)
        }
}
