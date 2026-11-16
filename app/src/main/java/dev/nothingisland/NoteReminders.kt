package dev.nothingisland

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

/** Erinnerungen zu Notizen: Alarm zur gewählten Uhrzeit, dann bleibt eine Benachrichtigung stehen, bis man sie antippt. */
object NoteReminders {
    const val CH = "note_reminders"
    private const val PREFS = "island_reminders"

    private fun load(ctx: Context): JSONArray =
        runCatching { JSONArray(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("list", "[]")) }
            .getOrDefault(JSONArray())

    private fun save(ctx: Context, a: JSONArray) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("list", a.toString()).apply()
    }

    fun schedule(ctx: Context, text: String, at: Long) {
        val id = (System.currentTimeMillis() / 1000L).toInt()
        val a = load(ctx)
        a.put(JSONObject().put("id", id).put("at", at).put("text", text))
        save(ctx, a)
        setAlarm(ctx, id, at, text)
    }

    private fun setAlarm(ctx: Context, id: Int, at: Long, text: String) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            ctx, id,
            Intent(ctx, NoteReminderReceiver::class.java).putExtra("id", id).putExtra("text", text),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        }
    }

    /** Nach einem Neustart gehen Alarme verloren: offene Erinnerungen neu setzen, abgelaufene sofort zeigen. */
    fun rescheduleAll(ctx: Context) {
        val a = load(ctx)
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            val at = o.getLong("at")
            if (at <= System.currentTimeMillis()) fire(ctx, o.getInt("id"), o.getString("text"))
            else setAlarm(ctx, o.getInt("id"), at, o.getString("text"))
        }
    }

    fun fire(ctx: Context, id: Int, text: String) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CH, "Notiz-Erinnerungen", NotificationManager.IMPORTANCE_HIGH),
            )
        }
        val open = PendingIntent.getActivity(
            ctx, id, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(ctx, CH)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Notiz")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true) // verschwindet erst, wenn man sie antippt
            .setOngoing(true)
            .build()
        runCatching { nm.notify(id, n) }
        val a = load(ctx)
        val rest = JSONArray()
        for (i in 0 until a.length()) if (a.getJSONObject(i).getInt("id") != id) rest.put(a.getJSONObject(i))
        save(ctx, rest)
    }
}

class NoteReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) NoteReminders.rescheduleAll(ctx)
        else NoteReminders.fire(ctx, intent.getIntExtra("id", 0), intent.getStringExtra("text") ?: return)
    }
}
