package io.github.xfl2342.voiceassistant.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle

/**
 * 「今天」小组件在系统里的登记处。
 *
 * 系统要求更新、用户改了小组件尺寸、小组件刚被放到桌面上，都从这里进来；
 * 具体怎么画交给 [TodayWidget]。
 *
 * 广播回来之后进程随时可能被回收，读数据库又是异步的，所以用 `goAsync()`
 * 把这次广播「挂着」，等画完了再告诉系统可以结束（和 [io.github.xfl2342.voiceassistant.reminder.BootReceiver] 同一个道理）。
 */
class TodayWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        render(context, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        // 拉大拉小都要重画：能放几行是按当前高度算出来的。
        render(context, intArrayOf(appWidgetId))
    }

    private fun render(context: Context, widgetIds: IntArray) {
        if (widgetIds.isEmpty()) return
        val pendingResult = goAsync()
        TodayWidget.refresh(context, widgetIds) { pendingResult.finish() }
    }
}
