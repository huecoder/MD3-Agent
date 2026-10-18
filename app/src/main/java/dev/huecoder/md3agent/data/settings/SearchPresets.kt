package dev.huecoder.md3agent.data.settings

data class SearchPreset(val name: String, val template: CustomSearch) {
    fun applyTo(current: CustomSearch): CustomSearch = template.copy(apiKey = current.apiKey)
}

object SearchPresets {
    val EXA = SearchPreset(
        name = "Exa",
        template = CustomSearch(
            url = "https://api.exa.ai/search",
            method = SearchMethod.POST,
            headers = listOf(HeaderEntry("x-api-key", CustomSearch.KEY_PLACEHOLDER)),
            body = """{"query": "${CustomSearch.QUERY_PLACEHOLDER}", "numResults": ${CustomSearch.COUNT_PLACEHOLDER}, "type": "auto", "contents": {"highlights": true}}""",
            snippetFields = "highlights,text",
        ),
    )

    val all = listOf(EXA)
}
