package dev.huecoder.md3agent.system.tiles

import android.app.PendingIntent
import android.os.Build
import android.util.Log
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.huecoder.md3agent.MainActivity
import dev.huecoder.md3agent.ui.navigation.LaunchTarget

abstract class LauncherTile(private val target: LaunchTarget) : TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        Log.i(TAG, "tile clicked target=$target")
        val intent = MainActivity.targetIntent(this, target)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(this, target.ordinal, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private companion object {
        const val TAG = "Tiles"
    }
}

class NewChatTile : LauncherTile(LaunchTarget.NEW_CHAT)

class FilesTile : LauncherTile(LaunchTarget.FILES)

class SettingsTile : LauncherTile(LaunchTarget.SETTINGS)
