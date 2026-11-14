package dev.nothingisland

import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/** Gegenstelle für die Test-Meldung: zeigt, was bei Antworten bzw. "Als gelesen markieren" angekommen ist. */
class TestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val msg = when (intent.action) {
            ACTION_REPLY -> {
                val t = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_REPLY)
                "Antwort angekommen: ${t ?: "(leer)"}"
            }
            ACTION_READ -> "Als gelesen markiert"
            else -> return
        }
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val ACTION_REPLY = "dev.nothingisland.TEST_REPLY"
        const val ACTION_READ = "dev.nothingisland.TEST_READ"
        const val KEY_REPLY = "test_reply"
    }
}
