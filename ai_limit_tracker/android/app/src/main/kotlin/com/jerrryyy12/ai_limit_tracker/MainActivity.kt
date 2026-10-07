package com.jerrryyy12.ai_limit_tracker

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private var pendingPermission: MethodChannel.Result? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "ai_limit_tracker/native")
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "load" -> result.success(TrackerCore.loadRaw(this))
                    "save" -> {
                        TrackerCore.save(this, call.arguments as String)
                        result.success(null)
                    }
                    "requestNotificationPermission" -> requestNotificationPermission(result)
                    "requestPinWidget" -> result.success(requestPinWidget())
                    "notify" -> {
                        TrackerCore.notifyNow(
                            this,
                            call.argument<String>("id") ?: "app",
                            call.argument<String>("title") ?: "",
                            call.argument<String>("body") ?: "",
                        )
                        result.success(null)
                    }
                    "openUrl" -> {
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(call.arguments as String)))
                        } catch (e: Exception) {
                            // 열 수 있는 앱이 없으면 무시
                        }
                        result.success(null)
                    }
                    else -> result.notImplemented()
                }
            }
    }

    private fun requestNotificationPermission(result: MethodChannel.Result) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            result.success(true)
            return
        }
        pendingPermission?.success(false)
        pendingPermission = result
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATIONS)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_NOTIFICATIONS) {
            pendingPermission?.success(grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
            pendingPermission = null
        }
    }

    /** 런처가 지원하면 "홈 화면에 위젯 추가" 창을 띄운다. */
    private fun requestPinWidget(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val mgr = getSystemService(AppWidgetManager::class.java) ?: return false
        if (!mgr.isRequestPinAppWidgetSupported) return false
        return mgr.requestPinAppWidget(ComponentName(this, LimitWidgetProvider::class.java), null, null)
    }

    companion object {
        private const val REQ_NOTIFICATIONS = 1001
    }
}
