package dev.huecoder.md3agent.data.db

import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

class MessageThreadMigration : AutoMigrationSpec {
    override fun onPostMigrate(connection: SQLiteConnection) {
        connection.execSQL(LINK_TO_PREVIOUS_MESSAGE)
        connection.execSQL(POINT_AT_NEWEST_MESSAGE)
    }

    private companion object {
        const val LINK_TO_PREVIOUS_MESSAGE = """
            UPDATE messages SET parentId = (
                SELECT previous.id FROM messages previous
                WHERE previous.chatId = messages.chatId
                    AND previous.createdAt <= messages.createdAt
                    AND previous.id != messages.id
                    AND (previous.createdAt < messages.createdAt OR previous.rowid < messages.rowid)
                ORDER BY previous.createdAt DESC, previous.rowid DESC
                LIMIT 1
            )
        """

        const val POINT_AT_NEWEST_MESSAGE = """
            UPDATE chats SET leafId = (
                SELECT newest.id FROM messages newest
                WHERE newest.chatId = chats.id
                ORDER BY newest.createdAt DESC, newest.rowid DESC
                LIMIT 1
            )
        """
    }
}
