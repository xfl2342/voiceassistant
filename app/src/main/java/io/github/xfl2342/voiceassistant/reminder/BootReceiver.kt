package io.github.xfl2342.voiceassistant.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 开机、以及应用被更新后，重新注册所有未来提醒。
 *
 * 系统重启会清掉全部闹钟，不补这一次，用户会发现「提醒再也不响了」。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        val appContext = context.applicationContext
        // 广播的存活时间很短，用 goAsync 争取时间把数据库读完再结束。
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 重新算一遍未来 30 天的提醒：既恢复开机后丢失的闹钟，
                // 顺便把重复行程的续期窗口往前推。
                io.github.xfl2342.voiceassistant.AppGraph(appContext)
                    .eventService
                    .refreshAllReminders()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
