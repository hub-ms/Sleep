package com.sleepytime.shared.platform

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.zoyi.channel.plugin.android.ChannelIO

class SleepFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        ChannelIO.initPushToken(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val pushMessage = remoteMessage.data
        if (ChannelIO.isChannelPushNotification(pushMessage)) {
            ChannelIO.receivePushNotification(application, pushMessage)
        } else {
            Log.d(TAG, "Received non-ChannelIO push message: $pushMessage")
        }
    }

    companion object {
        private const val TAG = "SleepFcmService"
    }
}