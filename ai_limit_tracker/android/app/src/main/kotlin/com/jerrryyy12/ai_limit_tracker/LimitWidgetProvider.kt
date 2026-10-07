package com.jerrryyy12.ai_limit_tracker

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

/** 홈 화면 위젯. 그리기는 TrackerCore 가 담당. */
class LimitWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        TrackerCore.refresh(ctx)
    }

    override fun onEnabled(ctx: Context) {
        TrackerCore.refresh(ctx)
    }
}
