package com.soundsleeper.app.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import com.soundsleeper.app.ui.theme.SleepTheme.primary
import com.soundsleeper.app.ui.theme.SleepTheme.surface
import com.soundsleeper.app.ui.theme.bodyText

@Composable
fun <T> SelectableChipGroup(
    items: List<T>,
    selectedItem: T,
    onSelectItem: (T) -> Unit,
    modifier: Modifier = Modifier,
    itemContent: @Composable (T) -> Any
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            SelectableChip(
                isSelected = item == selectedItem,
                onClick = { onSelectItem(item) }
            ) {
                when (val resolved = itemContent(item)) {
                    is Painter -> Icon(
                        modifier = Modifier.size(24.dp),
                        painter = resolved,
                        contentDescription = null,
                        tint = primary
                    )
                    is String -> Text(
                        text = resolved,
                        style = MaterialTheme.typography.bodyText,
                        color = Color.White
                    )
                    else -> Text(text = resolved.toString())
                }
            }
        }
    }
}

@Composable
fun SelectableChip(
    isSelected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (isSelected) primary else surface,
        modifier = Modifier.padding(4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            content()
        }
    }
}
