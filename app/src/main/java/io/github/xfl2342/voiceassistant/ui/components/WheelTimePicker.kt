package io.github.xfl2342.voiceassistant.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 滚轮式时间选择器：左右两列，分别是小时与分钟，上下滑动选择。
 *
 * Compose 没有内置这种控件，所以自己用列表加「吸附滚动」拼一个：
 * 松手后自动对齐到某一项，中间那项就是当前选中值。
 */
@Composable
fun WheelTimePicker(
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WheelColumn(
            count = 24,
            selected = hour,
            onSelected = onHourChange,
            modifier = Modifier.width(88.dp),
        )
        Text(text = ":", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        WheelColumn(
            count = 60,
            selected = minute,
            onSelected = onMinuteChange,
            modifier = Modifier.width(88.dp),
        )
        Text(
            text = "时 / 分",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WheelColumn(
    count: Int,
    selected: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val itemHeight: Dp = 44.dp
    val visibleCount = 5
    val itemHeightPx = with(LocalDensity.current) { itemHeight.toPx() }
    // 上下各留出两格，让选中项停在正中间。
    val verticalPadding = itemHeight * ((visibleCount - 1) / 2)

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selected)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    // 滚动停下后，把最接近中间的那一项报给上层。
    LaunchedEffect(listState) {
        snapshotFlow {
            val offset = listState.firstVisibleItemScrollOffset
            listState.firstVisibleItemIndex + if (offset > itemHeightPx / 2) 1 else 0
        }
            .distinctUntilChanged()
            .collect { index -> if (index in 0 until count) onSelected(index) }
    }

    Box(
        modifier = modifier.height(itemHeight * visibleCount),
        contentAlignment = Alignment.Center,
    ) {
        // 选中那一行的高亮底色，画在数字下面。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        )

        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(vertical = verticalPadding),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(count) { index ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val isSelected = index == selected
                    Text(
                        text = "%02d".format(index),
                        style = if (isSelected) {
                            MaterialTheme.typography.titleLarge
                        } else {
                            MaterialTheme.typography.bodyLarge
                        },
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        },
                    )
                }
            }
        }
    }
}
