package dev.nothingisland

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Nach einem App-Update (MY_PACKAGE_REPLACED) oder Neustart wird der Benachrichtigungszugriff
 * auf manchen Geräten nicht automatisch wieder verbunden. Hier stoßen wir das Neuverbinden an.
 */
class RebindReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        IslandListenerService.rebind(context.applicationContext)
    }
}
