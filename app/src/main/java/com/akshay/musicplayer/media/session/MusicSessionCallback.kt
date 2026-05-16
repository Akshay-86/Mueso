package com.akshay.musicplayer.media.session

import androidx.media3.common.Player
import androidx.media3.session.MediaSession

class MusicSessionCallback : MediaSession.Callback {
    override fun onConnect(
        session: MediaSession,
        controller: androidx.media3.session.MediaSession.ControllerInfo
    ): androidx.media3.session.MediaSession.ConnectionResult {
        return super.onConnect(session, controller)
    }
}
