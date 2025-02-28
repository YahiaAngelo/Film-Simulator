package io.github.yahiaangelo.filmsimulator.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SettingsSlider(
    name: String,
    value: Float,
    steps: Int,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {

    ListItem {
        Column {
            Text(
                style = MaterialTheme.typography.labelLarge,
                text = name
            )
            Slider(
                value = value,
                onValueChange = {
                    onValueChange(it)
                },
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.secondary,
                    activeTrackColor = MaterialTheme.colorScheme.secondary,
                    inactiveTrackColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
                steps = steps,
                valueRange = range
            )
            Text(text = value.toInt().toString())
        }
    }

}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun CenteredSettingsSlider(
    name: String,
    value: Float,
    steps: Int,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Label aligned to the start
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = 16.dp)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = range.start.toInt().toString(), modifier = Modifier.padding(end = 8.dp)) // Min value

            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = range,
                steps = (range.endInclusive - range.start).toInt() / steps - 1,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.secondary,
                    activeTrackColor = MaterialTheme.colorScheme.secondary,
                    inactiveTrackColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
                modifier = Modifier.weight(1f)
            )

            Text(text = range.endInclusive.toInt().toString(), modifier = Modifier.padding(start = 8.dp)) // Max value
        }

        // Displaying the current value at the center
        Text(
            text = value.toInt().toString(),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

