package com.example.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.example.network.RewardsApiService
import com.example.network.RegisterPushRequest

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        Log.d("FCM", "From: ${remoteMessage.from}")
        
        // Handle FCM messages here.
        val title = remoteMessage.notification?.title ?: remoteMessage.data["title"] ?: "New Alert"
        val body = remoteMessage.notification?.body ?: remoteMessage.data["body"] ?: ""
        
        showNotification(applicationContext, title, body)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "Refreshed token: $token")
        
        com.example.network.SessionManager.init(applicationContext)
        
        // Register token with the backend
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val api = com.example.network.NetworkClient.apiService
                api.registerDevicePush(RegisterPushRequest(token, "ANDROID"))
            } catch (e: Exception) {
                Log.e("FCM", "Failed to register push token: ${e.message}")
            }
        }
    }

    private fun showNotification(context: Context, title: String, message: String) {
        val channelId = "TX_CHANNEL"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Transactions"
            val descriptionText = "Transaction and Governance Notifications"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }
}
