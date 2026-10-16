package dev.huecoder.md3agent.system.control

interface ScriptPhone {
    fun begin(chatId: String)

    fun say(caption: String)

    suspend fun run(request: PhoneRequest): PhoneReport?

    suspend fun read(say: String?, detailed: Boolean, find: ElementSelector?): ScreenReading?
}
