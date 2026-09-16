package com.sleepytime.shared.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sleepytime.shared.ui.theme.bodyText

@Composable
fun <T> SelectableChipGroup(
    items: List<T>,
    selectedItem: T,
    onSelectItem: (T) -> Unit,
    itemLabel: (T) -> String = { it.toString() }
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            SelectableChip(
                text = itemLabel(item),
                isSelected = item == selectedItem,
                onClick = { onSelectItem(item) }
            )
        }
    }
}

@Composable
fun SelectableChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clickable { onClick() },
        shape = RoundedCornerShape(50.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.05f),
        border = if (isSelected) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            text = text,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyText.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            ),
            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f)
        )
    }
}
