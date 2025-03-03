package io.github.yahiaangelo.filmsimulator.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CenteredSlider(
    value: Float,
    onValueChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = -1f..1f,
    thumb: @Composable () -> Unit = DefaultThumb,
    center: Float = 0f,
    centerThreshold: Float = .05f,
    centerIndicator: @Composable () -> Unit = DefaultCenterIndicator,
    centerTrack: @Composable (@Composable (@Composable (Boolean) -> Unit) -> Unit) -> Unit = DefaultTrack,
) {

    val hapticFeedback = LocalHapticFeedback.current
    val valueState = rememberUpdatedState(value)
    LaunchedEffect(Unit) {
        snapshotFlow { valueState.value }
            .map { it == center }
            .filter { it }
            .drop(if (value == center) 1 else 0)
            .collect {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            }
    }

    Slider(
        value = value,
        onValueChange = {
            onValueChanged(
                when {
                    (it - center).absoluteValue < centerThreshold -> center
                    else -> it
                }
            )
        },
        modifier = modifier,
        valueRange = valueRange,
        thumb = {
            Box(contentAlignment = Alignment.Center) {
                thumb()
            }
        },
        track = { sliderState ->

            val fraction by remember {
                derivedStateOf {
                    (sliderState.value - sliderState.valueRange.start) / (sliderState.valueRange.endInclusive - sliderState.valueRange.start)
                }
            }

            val centerFraction by remember {
                derivedStateOf {
                    (center - sliderState.valueRange.start) / (sliderState.valueRange.endInclusive - sliderState.valueRange.start)
                }
            }

            BoxWithConstraints(
                contentAlignment = Alignment.Center
            ) {
                val isAboveCenter = fraction > centerFraction
                val width = this@BoxWithConstraints.maxWidth
                centerTrack { activeSection ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset {
                                when {
                                    isAboveCenter -> IntOffset(
                                        x = (width.toPx() * centerFraction).roundToInt(),
                                        y = 0
                                    )

                                    else -> IntOffset(
                                        x = (width.toPx() * fraction).roundToInt(),
                                        y = 0
                                    )
                                }
                            }
                            .width(
                                when {
                                    isAboveCenter -> width * (fraction - centerFraction)
                                    else -> width * (centerFraction - fraction)
                                }
                            )
                            .height(20.dp),
                        contentAlignment = Alignment.Center,
                        content = { activeSection(isAboveCenter) }
                    )
                }

                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .offset {
                            IntOffset(
                                x = (width.toPx() * centerFraction).roundToInt(),
                                y = 0
                            )
                        }
                        .centerHorizontally(),
                    content = { centerIndicator() }
                )
            }
        }
    )
}


private val DefaultThumb = @Composable {
    Box(
        Modifier
            .size(24.dp)
            .padding(4.dp)
            .background(MaterialTheme.colorScheme.primary, CircleShape)
    )
}

private val DefaultTrack: @Composable (@Composable (@Composable (Boolean) -> Unit) -> Unit) -> Unit =
    @Composable { activeSection ->
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .2f),
                    shape = CircleShape
                )
        )
        activeSection { isAboveCenter ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(MaterialTheme.colorScheme.primary, shape = CircleShape)
            )
        }
    }

private val DefaultCenterIndicator = @Composable {
    Box(
        Modifier
            .height(24.dp)
            .width(2.dp)
            .background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 1f),
                CircleShape
            )
    )
}


@Stable
@Composable
fun Modifier.centerHorizontally(): Modifier {
    var width by remember { mutableIntStateOf(0) }
    return onSizeChanged { width = it.width }
        .offset { IntOffset(x = -width / 2, y = 0) }
}
