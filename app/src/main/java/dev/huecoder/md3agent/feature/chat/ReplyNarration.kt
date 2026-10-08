package dev.huecoder.md3agent.feature.chat

import dev.huecoder.md3agent.voice.ReplySpeaker

class ReplyNarration(private val speaker: ReplySpeaker) {
    private var armed = false
    private var armedOverId: String? = null
    private var followingId: String? = null

    fun arm(latestReplyId: String?) {
        armed = true
        armedOverId = latestReplyId
    }

    fun disarm() {
        armed = false
        followingId = null
    }

    fun onLatest(reply: ChatItem.Reply) {
        if (armed && (reply.id != armedOverId || reply.isActive)) {
            armed = false
            followingId = reply.id
        }
        if (reply.id != followingId) return
        val text = reply.spokenText()
        if (reply.isActive) {
            speaker.follow(reply.id, text)
        } else {
            speaker.finish(reply.id, text)
            followingId = null
        }
    }
}

fun ChatItem.Reply.spokenText(): String = parts.filterIsInstance<ReplyPart.Text>().joinToString("\n\n") { it.content }
