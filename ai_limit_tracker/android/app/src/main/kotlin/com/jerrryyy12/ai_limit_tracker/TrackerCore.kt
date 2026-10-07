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
import android.graphics.Color
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import org.json.JSONObject
import java.util.Calendar

/**
 * 앱(Flutter)과 위젯·알람이 공유하는 상태와 계산.
 *
 * 상태는 Flutter 쪽 lib/models.dart 의 TrackerState.encode() JSON(v2) 그대로이며,
 * 남은 양·소진 예상 등은 models.dart 와 같은 규칙으로 현재 시각에서 다시 계산한다.
 */
object TrackerCore {
    private const val PREFS = "ai_limit_tracker"
    private const val KEY_STATE = "state"
    /** 마지막으로 리셋 알림을 확인한 시각 — 이 이후에 지난 리셋만 알린다. */
    private const val KEY_LAST_CHECK = "last_check"

    private const val HOUR_MS = 60L * 60 * 1000
    private const val SESSION_MS = 5 * HOUR_MS
    private const val WEEK_MS = 7 * 24 * HOUR_MS
    private const val CHANNEL_ID = "limit_reset"
    private const val ALARM_REQUEST = 1
    private const val REFRESH_REQUEST = 2

    private const val RED = 0xFFD23B3B.toInt()

    // ───────────── 상태 (models.dart 와 동일 규칙) ─────────────

    class Window(json: JSONObject?, val session: Boolean) {
        val usedPct = json?.optLongOrNull("usedPct")?.toInt()
        val resetAt = json?.optLongOrNull("resetAt")
        val updatedAt = json?.optLongOrNull("updatedAt")
        private val length = if (session) SESSION_MS else WEEK_MS

        val configured get() = usedPct != null || resetAt != null

        fun currentReset(now: Long): Long? {
            val r = resetAt ?: return null
            if (session) return if (r > now) r else null
            return nextPeriodic(r, WEEK_MS, now)
        }

        fun usedNow(now: Long): Int? {
            val used = usedPct ?: return null
            val r = resetAt ?: return used
            if (session) return if (r > now) used else 0
            val start = currentReset(now)!! - WEEK_MS
            if (updatedAt != null && updatedAt < start) return 0
            return used
        }

        fun remainingNow(now: Long) = usedNow(now)?.let { 100 - it }

        /** 소진 예상 시각(리셋 전에 바닥날 때만) 또는 다 쓴 경우 리셋 시각. 경고 아니면 null. */
        fun warningAt(now: Long): Long? {
            val reset = currentReset(now) ?: return null
            val used = usedNow(now) ?: return null
            val at = updatedAt ?: return null
            if (used >= 100) return reset
            val start = reset - length
            val elapsed = at - start
            if (used <= 0 || elapsed <= 0 || at > reset) return null
            val runOut = start + (elapsed * 100.0 / used).toLong()
            return if (runOut < reset) runOut else null
        }
    }

    class Service(json: JSONObject) {
        val id: String = json.getString("id")
        val name: String = json.getString("name")
        val session = Window(json.optJSONObject("session"), true)
        val weekly = Window(json.optJSONObject("weekly"), false)

        /** 남은 양이 적은 쪽(같으면 주간). */
        fun tighter(now: Long): Window {
            val s = session.remainingNow(now) ?: return weekly
            val w = weekly.remainingNow(now) ?: return session
            return if (s < w) session else weekly
        }

        fun urgency(now: Long): Pair<Long, Int> {
            val soonest = listOfNotNull(session.warningAt(now), weekly.warningAt(now)).minOrNull() ?: Long.MAX_VALUE
            val rem = listOfNotNull(session.remainingNow(now), weekly.remainingNow(now)).minOrNull() ?: 101
            return soonest to rem
        }
    }

    class State(
        val services: List<Service>,
        val absoluteTime: Boolean,
        val urgentFirst: Boolean,
        val customOrder: List<String>,
        val resetAlerts: Boolean,
    ) {
        fun ordered(now: Long): List<Service> =
            if (urgentFirst) {
                services.sortedWith(compareBy({ it.urgency(now).first }, { it.urgency(now).second }))
            } else {
                services.sortedBy { customOrder.indexOf(it.id).let { i -> if (i < 0) 999 else i } }
            }
    }

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (has(key) && !isNull(key)) getLong(key) else null

    /** anchor 에서 period 간격으로 반복되는 시각들 중 now 보다 뒤인 첫 시각. */
    fun nextPeriodic(anchor: Long, period: Long, now: Long): Long =
        now - Math.floorMod(now - anchor, period) + period

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun loadRaw(ctx: Context): String? = prefs(ctx).getString(KEY_STATE, null)

    fun loadState(ctx: Context): State? {
        val raw = loadRaw(ctx) ?: return null
        return try {
            val j = JSONObject(raw)
            if (j.optInt("v") != 2) return null
            val arr = j.getJSONArray("services")
            val st = j.optJSONObject("settings") ?: JSONObject()
            val order = st.optJSONArray("customOrder")
            State(
                services = (0 until arr.length()).map { Service(arr.getJSONObject(it)) },
                absoluteTime = st.optBoolean("absoluteTime", false),
                urgentFirst = st.optBoolean("urgentFirst", true),
                customOrder = if (order == null) emptyList() else (0 until order.length()).map { order.getString(it) },
                resetAlerts = st.optBoolean("resetAlerts", true),
            )
        } catch (e: Exception) {
            null
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
        val state = loadState(ctx)
        if (state != null && state.resetAlerts) {
            for (s in state.services) {
                val events = mutableListOf<String>()
                val sr = s.session.resetAt
                if (sr != null && sr > last && sr <= upTo) events += "5시간 한도"
                val wr = s.weekly.resetAt?.let { nextPeriodic(it, WEEK_MS, last) }
                if (wr != null && wr <= upTo) events += "주간 한도"
                if (events.isNotEmpty()) {
                    notifyNow(ctx, "reset_${s.id}", "${s.name} ${events.joinToString(" · ")} 리셋", "지금 다시 사용할 수 있어요.")
                }
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
        // 리셋 순간에 알림과 위젯 표시(남은 %)가 바뀌므로, 알림을 꺼도 위젯 갱신용으로 건다.
        val next = loadState(ctx)?.services
            ?.flatMap { listOfNotNull(it.session.currentReset(now), it.weekly.currentReset(now)) }
            ?.minOrNull()
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

    fun notifyNow(ctx: Context, key: String, title: String, text: String) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.areNotificationsEnabled()) return
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "한도 알림", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "Claude Code·Codex 한도 리셋·잔여량 알림"
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
        nm.notify(key.hashCode(), n)
    }

    private fun openAppIntent(ctx: Context): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            ctx, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun refreshIntent(ctx: Context): PendingIntent {
        val intent = Intent(ctx, ResetAlarmReceiver::class.java).setAction(ResetAlarmReceiver.ACTION_REFRESH)
        return PendingIntent.getBroadcast(
            ctx, REFRESH_REQUEST, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    // ───────────── 위젯 ─────────────

    private class RowIds(
        val root: Int, val icon: Int, val name: Int, val label: Int, val pct: Int,
        val green: Int, val amber: Int, val red: Int, val sub: Int,
    )

    private val rows = listOf(
        RowIds(
            R.id.row1, R.id.row1_icon, R.id.row1_name, R.id.row1_label, R.id.row1_pct,
            R.id.row1_green, R.id.row1_amber, R.id.row1_red, R.id.row1_sub,
        ),
        RowIds(
            R.id.row2, R.id.row2_icon, R.id.row2_name, R.id.row2_label, R.id.row2_pct,
            R.id.row2_green, R.id.row2_amber, R.id.row2_red, R.id.row2_sub,
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
        loadColors(ctx)
        val views = RemoteViews(ctx.packageName, R.layout.widget_limits)
        views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(ctx))
        views.setOnClickPendingIntent(R.id.widget_refresh, refreshIntent(ctx))
        val now = System.currentTimeMillis()
        val state = loadState(ctx)
        val services = state?.ordered(now)
        for ((i, ids) in rows.withIndex()) {
            val s = services?.getOrNull(i)
            if (s == null) {
                // 앱을 아직 안 열었거나 저장된 값이 없을 때 기본 표시.
                val (id, name) = if (i == 0) "codex" to "Codex" else "claude" to "Claude Code"
                bindEmpty(views, ids, id, name)
            } else {
                bindService(views, ids, s, state?.absoluteTime ?: false, now)
            }
        }
        return views
    }

    private fun iconFor(id: String) = if (id == "claude") R.drawable.ic_claude else R.drawable.ic_codex

    private fun showBar(views: RemoteViews, ids: RowIds, which: Int?, progress: Int) {
        for (bar in listOf(ids.green, ids.amber, ids.red)) {
            views.setViewVisibility(bar, if (bar == which) View.VISIBLE else View.GONE)
        }
        if (which != null) views.setProgressBar(which, 100, progress, false)
    }

    private fun bindEmpty(views: RemoteViews, ids: RowIds, id: String, name: String) {
        views.setImageViewResource(ids.icon, iconFor(id))
        views.setTextViewText(ids.name, name)
        views.setTextViewText(ids.label, "")
        views.setTextViewText(ids.pct, "—")
        views.setTextColor(ids.pct, ctxTextColor)
        showBar(views, ids, ids.green, 0)
        views.setTextViewText(ids.sub, "앱에서 사용량 입력")
        views.setTextColor(ids.sub, ctxSubColor)
    }

    // 위젯 기본 글자색은 레이아웃(@color, 낮/밤 자동)에 있고, 경고일 때만 빨강으로 바꾼다.
    private var ctxTextColor = Color.BLACK
    private var ctxSubColor = Color.GRAY

    private fun bindService(views: RemoteViews, ids: RowIds, s: Service, absolute: Boolean, now: Long) {
        views.setImageViewResource(ids.icon, iconFor(s.id))
        views.setTextViewText(ids.name, s.name)
        val w = s.tighter(now)
        views.setTextViewText(ids.label, if (w.session) "5시간" else "주간")
        val rem = w.remainingNow(now)
        if (rem == null) {
            views.setTextViewText(ids.pct, "—")
            showBar(views, ids, ids.green, 0)
        } else {
            views.setTextViewText(ids.pct, "$rem%")
            if (rem < 20) views.setTextColor(ids.pct, RED) else views.setTextColor(ids.pct, ctxTextColor)
            val bar = when {
                rem > 50 -> ids.green
                rem >= 20 -> ids.amber
                else -> ids.red
            }
            showBar(views, ids, bar, rem)
        }

        val warn = w.warningAt(now)
        val reset = w.currentReset(now)
        val (sub, red) = when {
            rem == null -> "앱에서 사용량 입력" to false
            warn != null && (w.usedNow(now) ?: 0) >= 100 -> "모두 사용함 · ${clock(warn, now)} 리셋" to true
            warn != null -> "${clock(warn, now)} 소진 예상" to true
            reset != null -> (if (absolute) "${clock(reset, now)} 리셋" else "${remaining(reset - now)} 후 리셋") to false
            else -> "리셋 시각 미입력" to false
        }
        views.setTextViewText(ids.sub, sub)
        views.setTextColor(ids.sub, if (red) RED else ctxSubColor)
    }

    /** 레이아웃의 낮/밤 글자색을 읽어 둔다(경고 해제 시 되돌리기용). */
    private fun loadColors(ctx: Context) {
        ctxTextColor = ctx.getColor(R.color.widget_text)
        ctxSubColor = ctx.getColor(R.color.widget_subtext)
    }

    private val weekdays = arrayOf("일", "월", "화", "수", "목", "금", "토")

    /** models.dart 의 formatClock 과 같은 형식. */
    private fun clock(ms: Long, now: Long): String {
        val t = Calendar.getInstance().apply { timeInMillis = ms }
        val hm = "%02d:%02d".format(t.get(Calendar.HOUR_OF_DAY), t.get(Calendar.MINUTE))
        val day = Calendar.getInstance().apply {
            timeInMillis = ms
            set(Calendar.HOUR_OF_DAY, 12); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val today = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 12); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val days = Math.round((day.timeInMillis - today.timeInMillis) / (24.0 * HOUR_MS)).toInt()
        return when {
            days == 0 -> hm
            days == 1 -> "내일 $hm"
            days in 2..6 -> "${weekdays[t.get(Calendar.DAY_OF_WEEK) - 1]} $hm"
            else -> "${t.get(Calendar.MONTH) + 1}/${t.get(Calendar.DAY_OF_MONTH)} $hm"
        }
    }

    /** models.dart 의 formatRemaining 과 같은 형식. */
    private fun remaining(ms: Long): String {
        val totalMin = maxOf(0L, ms) / 60_000
        val days = totalMin / (24 * 60)
        val hours = totalMin / 60
        val minutes = totalMin % 60
        return when {
            days > 0 -> if (hours % 24 > 0) "${days}일 ${hours % 24}시간" else "${days}일"
            hours > 0 -> if (minutes > 0) "${hours}시간 ${minutes}분" else "${hours}시간"
            totalMin > 0 -> "${totalMin}분"
            else -> "곧"
        }
    }
}
