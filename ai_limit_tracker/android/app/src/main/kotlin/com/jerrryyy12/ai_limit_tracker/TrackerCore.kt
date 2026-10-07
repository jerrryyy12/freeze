package com.jerrryyy12.ai_limit_tracker

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 앱(Flutter)과 위젯·알람이 공유하는 상태와 계산.
 *
 * 상태는 Flutter 쪽 lib/models.dart 의 TrackerState.encode() JSON 그대로이며,
 * 여기서는 읽기만 하고 "지금 사용 가능한지" 등은 현재 시각으로 다시 계산한다.
 */
object TrackerCore {
    private const val PREFS = "ai_limit_tracker"
    private const val KEY_STATE = "state"
    /** 마지막으로 리셋 알림을 확인한 시각 — 이 이후에 지난 리셋만 알린다. */
    private const val KEY_LAST_CHECK = "last_check"

    private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
    private const val CHANNEL_ID = "limit_reset"
    private const val ALARM_REQUEST = 1

    // ───────────── 상태 ─────────────

    class Service(json: JSONObject) {
        val id: String = json.getString("id")
        val name: String = json.getString("name")
        val sessionResetAt = json.optLongOrNull("sessionResetAt")
        val sessionPct = json.optLongOrNull("sessionPct")?.toInt()
        val sessionLimited = json.optBoolean("sessionLimited", false)
        val weeklyAnchor = json.optLongOrNull("weeklyAnchor")
        val weeklyPct = json.optLongOrNull("weeklyPct")?.toInt()
        val weeklyPctUntil = json.optLongOrNull("weeklyPctUntil")
        val weeklyLimitedUntil = json.optLongOrNull("weeklyLimitedUntil")

        fun sessionActive(now: Long) = sessionResetAt != null && now < sessionResetAt
        fun sessionBlocked(now: Long) = sessionActive(now) && sessionLimited
        fun weeklyBlocked(now: Long) = weeklyLimitedUntil != null && now < weeklyLimitedUntil
        fun nextWeekly(now: Long) = weeklyAnchor?.let { nextPeriodic(it, WEEK_MS, now) }
        fun effectiveWeeklyPct(now: Long) =
            if (weeklyPct != null && weeklyPctUntil != null && now < weeklyPctUntil) weeklyPct else null

        /** 다음에 상태가 바뀌는 시각(세션 리셋·주간 리셋·주간 차단 해제) 중 가장 이른 것. */
        fun nextEvent(now: Long): Long? = listOfNotNull(
            sessionResetAt?.takeIf { it > now },
            weeklyLimitedUntil?.takeIf { it > now },
            nextWeekly(now),
        ).minOrNull()
    }

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (has(key) && !isNull(key)) getLong(key) else null

    /** anchor 에서 period 간격으로 반복되는 시각들 중 now 보다 뒤인 첫 시각. (models.dart 의 nextPeriodic 과 동일) */
    fun nextPeriodic(anchor: Long, period: Long, now: Long): Long =
        now - Math.floorMod(now - anchor, period) + period

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun loadRaw(ctx: Context): String? = prefs(ctx).getString(KEY_STATE, null)

    fun loadServices(ctx: Context): List<Service> {
        val raw = loadRaw(ctx) ?: return emptyList()
        return try {
            val arr = JSONObject(raw).getJSONArray("services")
            (0 until arr.length()).map { Service(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 앱에서 상태를 저장할 때. 위젯 갱신 + 알람 재설정. */
    fun save(ctx: Context, raw: String) {
        prefs(ctx).edit()
            .putString(KEY_STATE, raw)
            .putLong(KEY_LAST_CHECK, System.currentTimeMillis())
            .apply()
        refresh(ctx)
    }

    /** 위젯 다시 그리고 다음 알람 예약. */
    fun refresh(ctx: Context) {
        updateAllWidgets(ctx)
        scheduleNextAlarm(ctx)
    }

    // ───────────── 알람·알림 ─────────────

    /** 알람이 울렸을 때: 지난 확인 이후 리셋된 것들을 알리고 다시 예약. */
    fun onAlarm(ctx: Context) {
        val now = System.currentTimeMillis()
        val p = prefs(ctx)
        val last = p.getLong(KEY_LAST_CHECK, now)
        // 알람이 몇 초 일찍 울려도 놓치지 않도록 약간 여유.
        val upTo = now + 2_000
        for (s in loadServices(ctx)) {
            val events = mutableListOf<String>()
            if (s.sessionResetAt != null && s.sessionResetAt in (last + 1)..upTo) {
                events += "5시간 한도"
            }
            val weekly = s.weeklyAnchor?.let { nextPeriodic(it, WEEK_MS, last) }
            if (weekly != null && weekly <= upTo) events += "주간 한도"
            if (events.isNotEmpty()) {
                notify(ctx, s, "${s.name} ${events.joinToString(" · ")} 리셋", "지금 다시 사용할 수 있어요.")
            }
        }
        p.edit().putLong(KEY_LAST_CHECK, upTo).apply()
        refresh(ctx)
    }

    private fun alarmIntent(ctx: Context): PendingIntent {
        val intent = Intent(ctx, ResetAlarmReceiver::class.java).setAction(ResetAlarmReceiver.ACTION_RESET)
        return PendingIntent.getBroadcast(
            ctx, ALARM_REQUEST, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun scheduleNextAlarm(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = alarmIntent(ctx)
        val now = System.currentTimeMillis()
        val next = loadServices(ctx).mapNotNull { it.nextEvent(now) }.minOrNull()
        if (next == null) {
            am.cancel(pi)
            return
        }
        val exactOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            if (exactOk) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
        }
    }

    private fun notify(ctx: Context, s: Service, title: String, text: String) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !nm.areNotificationsEnabled()) return
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "한도 리셋 알림", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "Claude Code·Codex 사용 한도가 리셋되면 알려줍니다"
                    },
                )
            }
            Notification.Builder(ctx, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(ctx)
        }
        val n = builder
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openAppIntent(ctx))
            .setAutoCancel(true)
            .build()
        nm.notify(s.id.hashCode(), n)
    }

    private fun openAppIntent(ctx: Context): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            ctx, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    // ───────────── 위젯 ─────────────

    private class Ids(
        val name: Int, val status: Int, val chrono: Int,
        val session: Int, val progress: Int, val weekly: Int,
    )

    private val slots = mapOf(
        "claude" to Ids(
            R.id.claude_name, R.id.claude_status, R.id.claude_chrono,
            R.id.claude_session, R.id.claude_progress, R.id.claude_weekly,
        ),
        "codex" to Ids(
            R.id.codex_name, R.id.codex_status, R.id.codex_chrono,
            R.id.codex_session, R.id.codex_progress, R.id.codex_weekly,
        ),
    )

    fun updateAllWidgets(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        val ids = mgr.getAppWidgetIds(ComponentName(ctx, LimitWidgetProvider::class.java))
        if (ids.isEmpty()) return
        val views = buildViews(ctx)
        for (id in ids) mgr.updateAppWidget(id, views)
    }

    private fun buildViews(ctx: Context): RemoteViews {
        val views = RemoteViews(ctx.packageName, R.layout.widget_limits)
        views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(ctx))
        val now = System.currentTimeMillis()
        val byId = loadServices(ctx).associateBy { it.id }
        for ((id, ids) in slots) {
            val s = byId[id]
            if (s == null) {
                // 앱을 한 번도 안 연 상태: 기본값으로 표시.
                views.setTextViewText(ids.status, "사용 가능")
                views.setInt(ids.status, "setBackgroundResource", R.drawable.pill_ok)
                views.setViewVisibility(ids.chrono, View.GONE)
                views.setTextViewText(ids.session, "5시간  대기")
                views.setViewVisibility(ids.progress, View.GONE)
                views.setTextViewText(ids.weekly, "주간  앱에서 설정")
                continue
            }
            bindService(views, ids, s, now)
        }
        return views
    }

    private fun bindService(views: RemoteViews, ids: Ids, s: Service, now: Long) {
        views.setTextViewText(ids.name, s.name)

        // 상태 배지
        val (label, bg) = when {
            s.weeklyBlocked(now) -> "주간 한도" to R.drawable.pill_block
            s.sessionBlocked(now) -> "세션 한도" to R.drawable.pill_block
            s.sessionActive(now) -> "세션 중" to R.drawable.pill_busy
            else -> "사용 가능" to R.drawable.pill_ok
        }
        views.setTextViewText(ids.status, label)
        views.setInt(ids.status, "setBackgroundResource", bg)

        // 5시간 세션: 진행 중이면 실시간 카운트다운
        if (s.sessionActive(now)) {
            val reset = s.sessionResetAt!!
            val base = SystemClock.elapsedRealtime() + (reset - now)
            views.setViewVisibility(ids.chrono, View.VISIBLE)
            views.setChronometer(ids.chrono, base, "%s 남음", true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                views.setChronometerCountDown(ids.chrono, true)
            }
            val pct = s.sessionPct
            views.setTextViewText(
                ids.session,
                "5시간  " + (if (pct != null) "$pct% · " else "") + "${clock(reset, now)} 리셋 · ",
            )
            if (pct != null) {
                views.setViewVisibility(ids.progress, View.VISIBLE)
                views.setProgressBar(ids.progress, 100, pct, false)
            } else {
                views.setViewVisibility(ids.progress, View.GONE)
            }
        } else {
            views.setViewVisibility(ids.chrono, View.GONE)
            views.setViewVisibility(ids.progress, View.GONE)
            views.setTextViewText(ids.session, "5시간  대기 (세션 없음)")
        }

        // 주간
        val next = s.nextWeekly(now)
        val weekly = when {
            next == null -> "주간  리셋 시각 미설정"
            else -> {
                val pct = s.effectiveWeeklyPct(now)
                "주간  " + (if (pct != null) "$pct% · " else "") + "${clock(next, now)} 리셋"
            }
        }
        views.setTextViewText(ids.weekly, weekly)
    }

    /** 오늘이면 "15:30", 아니면 "10/12(일) 15:30" (models.dart 의 formatClock 과 동일 형식) */
    private fun clock(ms: Long, now: Long): String {
        val a = Calendar.getInstance().apply { timeInMillis = ms }
        val b = Calendar.getInstance().apply { timeInMillis = now }
        val sameDay = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
        val pattern = if (sameDay) "HH:mm" else "M/d(E) HH:mm"
        return SimpleDateFormat(pattern, Locale.KOREAN).format(Date(ms))
    }
}
