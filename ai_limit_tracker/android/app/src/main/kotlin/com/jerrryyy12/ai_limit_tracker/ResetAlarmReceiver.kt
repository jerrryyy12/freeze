package com.jerrryyy12.ai_limit_tracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 리셋 시각 알람, 위젯 새로고침 버튼, 재부팅·시간 변경을 받아
 * (필요하면 알림을 띄우고) 위젯을 갱신한 뒤 다음 알람을 건다.
 */
class ResetAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            ACTION_RESET -> TrackerCore.onAlarm(ctx)
            // 새로고침 버튼, 재부팅·업데이트·시계 변경: 다시 그리고 알람을 다시 건다.
            else -> TrackerCore.refresh(ctx)
        }
    }

    companion object {
        const val ACTION_RESET = "com.jerrryyy12.ai_limit_tracker.RESET"
        const val ACTION_REFRESH = "com.jerrryyy12.ai_limit_tracker.REFRESH"
    }
}
