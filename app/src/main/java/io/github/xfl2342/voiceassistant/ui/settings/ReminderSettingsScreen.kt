package io.github.xfl2342.voiceassistant.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.xfl2342.voiceassistant.reminder.ReminderPermissionStatus
import io.github.xfl2342.voiceassistant.reminder.ReminderPermissions
import kotlinx.coroutines.delay

/**
 * 提醒设置页（权限引导）。
 *
 * 提醒要准时弹出来，需要三项系统设置都对。这三项分散在系统设置的不同角落，
 * 用户很难自己找齐，所以这里逐项说明原因、给出状态，并直接把人送过去。
 */
@Composable
fun ReminderSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var status by remember { mutableStateOf(ReminderPermissions.read(context)) }

    // 用户去系统设置里改完再回来，状态要跟着刷新。
    // 这里用轮询而不是生命周期回调，逻辑简单、不会漏掉任何返回路径。
    LaunchedEffect(Unit) {
        while (true) {
            status = ReminderPermissions.read(context)
            delay(1500)
        }
    }

    BackHandler(onBack = onBack)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ 返回") }
            Text(
                text = "提醒设置",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (status.allGood) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "三项设置都已就绪，提醒会准时弹出。",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        PermissionItem(
            title = "精确闹钟",
            granted = status.exactAlarmAllowed,
            reason = "不开启的话，系统只保证「大致准时」，提醒可能晚几分钟。",
            onOpen = { ReminderPermissions.openExactAlarmSettings(context) },
        )

        PermissionItem(
            title = "通知权限",
            granted = status.notificationsEnabled,
            reason = "关掉通知，提醒就不会出现在通知栏里。",
            onOpen = { ReminderPermissions.openNotificationSettings(context) },
        )

        PermissionItem(
            title = "后台运行",
            granted = status.batteryOptimizationIgnored,
            reason = "系统为了省电会限制后台应用，提醒可能因此被推迟甚至丢掉。",
            onOpen = { ReminderPermissions.openBatteryOptimizationSettings(context) },
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "小米 / 澎湃系统还需要手动开三处",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "1. 应用信息页 → 自启动：开启\n" +
                        "2. 同一页 → 省电策略：改为「无限制」\n" +
                        "3. 多任务界面下拉应用卡片并加锁，避免被一键清理",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = { ReminderPermissions.openAppDetailSettings(context) }) {
                    Text("打开应用信息页")
                }
            }
        }
    }
}

@Composable
private fun PermissionItem(
    title: String,
    granted: Boolean,
    reason: String,
    onOpen: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = if (granted) "已开启" else "未开启",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (granted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
            Text(
                text = reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!granted) {
                OutlinedButton(onClick = onOpen) { Text("去设置") }
            }
        }
    }
}
