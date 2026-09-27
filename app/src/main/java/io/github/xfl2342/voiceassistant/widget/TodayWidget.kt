package io.github.xfl2342.voiceassistant.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import io.github.xfl2342.voiceassistant.AppGraph
import io.github.xfl2342.voiceassistant.R
import io.github.xfl2342.voiceassistant.data.db.EventEntity
import io.github.xfl2342.voiceassistant.ui.AppStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * 桌面上那块「今天」的画法。
 *
 * 应用里任何地方改了行程，调一下 [refresh]，桌面上的那一块就重画。
 * 两件事值得说明：
 * - 读数据库要时间，所以统一丢到后台线程；`RemoteViews` 本身只是一份「怎么画」的
 *   说明，真正的渲染由启动器完成，所以算好之后直接交给系统就行；
 * - 每次都是**整块重画**（先清空行程区再逐行贴上去），不留上一次的行。桌面小组件
 *   最怕的就是这里改一点那里改一点，日子久了界面和数据对不上。
 */
object TodayWidget {

    private const val TAG = "TodayWidget"

    /**
     * 点击目标的请求码。
     *
     * PendingIntent 判重只看请求码和几个「不含 extras」的字段，所以每个点击目标都得
     * 有自己的号，否则后设的那个会把前面的覆盖掉。行程按行号编号，避开前两个。
     */
    private const val REQUEST_CALENDAR = 1
    private const val REQUEST_RECORD = 2
    private const val REQUEST_EVENT_BASE = 100

    /** 小组件的生命周期比界面长，用一个跟着进程走的协程作用域。 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 重画所有已经放在桌面上的小组件。行程有改动、应用被打开时调它。 */
    fun refresh(context: Context) {
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext) ?: return
        val ids = manager.getAppWidgetIds(ComponentName(appContext, TodayWidgetProvider::class.java))
        if (ids.isEmpty()) return
        refresh(appContext, ids)
    }

    /**
     * 重画指定的几个小组件。
     *
     * [onDone] 在全部画完之后回调，广播接收器用它告诉系统「这一次可以结束了」。
     */
    fun refresh(context: Context, widgetIds: IntArray, onDone: () -> Unit = {}) {
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        if (manager == null || widgetIds.isEmpty()) {
            onDone()
            return
        }

        scope.launch {
            try {
                widgetIds.forEach { id ->
                    runCatching { update(appContext, manager, id) }
                        .onFailure { Log.w(TAG, "小组件重画失败（id=$id）", it) }
                }
            } finally {
                onDone()
            }
        }
    }

    private suspend fun update(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val content = loadContent(context, manager, widgetId)
        manager.updateAppWidget(widgetId, views(context, content))
    }

    /** 读出今天要显示的内容；读数据库失败时按「暂时读不到」处理，不让小组件停在旧数据上。 */
    private suspend fun loadContent(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
    ): TodayWidgetContent {
        val zone = ZoneId.of(EventEntity.DEFAULT_TIME_ZONE)
        val today = Instant.now().atZone(zone).toLocalDate()
        val maxRows = widgetRowBudget(
            manager.getAppWidgetOptions(widgetId)
                .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0),
        )

        return runCatching {
            val graph = AppGraph(context)
            TodayWidgetContentBuilder.build(
                events = graph.eventRepository.loadEvents(),
                rules = graph.eventRepository.loadRules(),
                today = today,
                zone = zone,
                weekStartDay = graph.settingsStore.weekStartDay,
                maxRows = maxRows,
            )
        }.getOrElse { error ->
            Log.w(TAG, "读取行程失败，小组件按空处理", error)
            TodayWidgetContent(
                dateLabel = "",
                rows = emptyList(),
                overflow = 0,
                emptyLabel = context.getString(R.string.widget_read_failed),
                nextLabel = null,
            )
        }
    }

    private fun views(context: Context, content: TodayWidgetContent): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_today)

        views.setTextViewText(R.id.widget_date, content.dateLabel)

        // 行程一行一条：先清掉布局里那行样例（以及上一次的内容），再按数据贴上去。
        views.removeAllViews(R.id.widget_rows)
        content.rows.forEachIndexed { index, row ->
            views.addView(R.id.widget_rows, rowViews(context, row, index))
        }

        views.setViewVisibility(
            R.id.widget_rows,
            if (content.rows.isEmpty()) View.GONE else View.VISIBLE,
        )
        views.setViewVisibility(
            R.id.widget_empty,
            if (content.emptyLabel == null) View.GONE else View.VISIBLE,
        )
        content.emptyLabel?.let { views.setTextViewText(R.id.widget_empty, it) }

        views.setViewVisibility(
            R.id.widget_next,
            if (content.nextLabel == null) View.GONE else View.VISIBLE,
        )
        content.nextLabel?.let { views.setTextViewText(R.id.widget_next, it) }

        views.setViewVisibility(
            R.id.widget_more,
            if (content.overflow <= 0) View.GONE else View.VISIBLE,
        )
        if (content.overflow > 0) {
            views.setTextViewText(
                R.id.widget_more,
                context.getString(R.string.widget_more, content.overflow),
            )
        }

        views.setOnClickPendingIntent(
            R.id.widget_header,
            pendingIntent(context, AppStart.Calendar, REQUEST_CALENDAR),
        )
        views.setOnClickPendingIntent(
            R.id.widget_record,
            pendingIntent(context, AppStart.Record, REQUEST_RECORD),
        )
        views.setOnClickPendingIntent(
            R.id.widget_more,
            pendingIntent(context, AppStart.Calendar, REQUEST_CALENDAR),
        )
        return views
    }

    /** 一行行程：时间、标题、左边那根按急迫程度上色的小竖条，点开是它的详情页。 */
    private fun rowViews(context: Context, row: TodayWidgetRow, index: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_row)
        views.setTextViewText(R.id.widget_row_time, row.timeLabel)
        views.setTextViewText(R.id.widget_row_title, row.title)
        views.setImageViewResource(R.id.widget_row_bar, barResource(row.urgency))
        views.setOnClickPendingIntent(
            R.id.widget_row_root,
            pendingIntent(
                context = context,
                start = AppStart.EventDetail(row.eventId),
                requestCode = REQUEST_EVENT_BASE + index,
            ),
        )
        return views
    }

    /**
     * 急迫程度对应的竖条颜色。
     *
     * 这里是界面那套 `EventColors` 的 XML 版本：桌面小组件用的是 RemoteViews +
     * 布局文件，拿不到 Compose 的颜色对象，只能落到三张 drawable 上。
     */
    private fun barResource(urgency: String): Int = when (urgency) {
        EventEntity.URGENCY_HIGH -> R.drawable.widget_bar_high
        EventEntity.URGENCY_LOW -> R.drawable.widget_bar_low
        else -> R.drawable.widget_bar_normal
    }

    private fun pendingIntent(context: Context, start: AppStart, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            AppStart.intent(context, start),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
