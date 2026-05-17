package com.akshay.musicplayer.media.session

import androidx.media3.common.Player
import androidx.media3.session.MediaSession

class MusicSessionCallback : MediaSession.Callback {
    override fun onConnect(
        session: MediaSession,
        controller: androidx.media3.session.MediaSession.ControllerInfo
    ): androidx.media3.session.MediaSession.ConnectionResult {
        val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().build()
        val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
            .add(Player.COMMAND_PLAY_PAUSE)
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
            .build()
            
        return MediaSession.ConnectionResult.accept(sessionCommands, playerCommands)
    }
}
