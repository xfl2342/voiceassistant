package io.github.xfl2342.voiceassistant.domain

import android.net.Uri
import io.github.xfl2342.voiceassistant.data.BackupCounts
import io.github.xfl2342.voiceassistant.data.BackupRepository
import io.github.xfl2342.voiceassistant.data.BackupResult
import io.github.xfl2342.voiceassistant.data.ImportPlan
import io.github.xfl2342.voiceassistant.data.ImportSummary

/**
 * 备份与恢复的写操作。
 *
 * 为什么恢复要单独架一层：**写库和注册系统闹钟必须一起做**（和 [EventService] 同一个道理）。
 * 恢复把行程写进数据库之后，那些行程的提醒还只是数据库里的几行字，系统闹钟并没有注册；
 * 少了补这一步，用户会以为「恢复完了」，到点却什么都不响。
 */
class BackupService(
    private val repository: BackupRepository,
    private val reminderSync: ReminderSync,
    /** 恢复之后的额外动作（目前是重画桌面小组件），理由同 [EventService]。 */
    private val onScheduleChanged: () -> Unit = {},
) {

    suspend fun counts(): BackupCounts = repository.counts()

    suspend fun exportToDownloads(): BackupResult? = repository.exportToDownloads()

    suspend fun exportTo(uri: Uri): Boolean = repository.exportTo(uri)

    /** 「另存为」对话框里的建议文件名。 */
    fun suggestedFileName(): String = repository.suggestedFileName()

    /** 读一份备份、算清会动到什么，先不写库。文件读不懂时抛 [BackupFormatException]。 */
    suspend fun prepareImport(uri: Uri): ImportPlan = repository.planImport(uri)

    /**
     * 照计划恢复，然后把提醒重新注册一遍。
     *
     * 提醒走「全部重算」而不是只补刚导入的那几条：重算的代价只是遍历一遍行程，
     * 换来的好处是不必再维护一份「这次导入了哪些行程」的清单——少一份要同步的状态，
     * 就少一处会出错的地方。[ReminderSync.refreshAll] 本来也在应用启动和开机后跑，
     * 走的是同一条路，不额外引入一整套没被验证过的逻辑。
     */
    suspend fun restore(plan: ImportPlan): ImportSummary {
        val summary = repository.import(plan.backup)
        if (summary.changedAny) {
            reminderSync.refreshAll()
            onScheduleChanged()
        }
        return summary
    }
}
