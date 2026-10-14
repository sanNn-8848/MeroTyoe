package com.romannepali.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.romannepali.keyboard.suggestion.CandidatePhrase
import com.romannepali.keyboard.suggestion.PredictiveTextEngine

/**
 * Compose-based SuggestionView component to be rendered within the container_suggestions
 * linear layout, observing state from PredictiveTextEngine to display dynamic word pills.
 */
@Composable
fun SuggestionView(
    predictiveTextEngine: PredictiveTextEngine,
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = true
) {
    val candidates by predictiveTextEngine.candidates.collectAsState()

    SuggestionViewContent(
        candidates = candidates,
        onSuggestionClick = onSuggestionClick,
        modifier = modifier,
        isDarkTheme = isDarkTheme
    )
}

@Composable
fun SuggestionViewContent(
    candidates: List<CandidatePhrase>,
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = true
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .horizontalScroll(scrollState)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        candidates.forEachIndexed { index, candidate ->
            SuggestionWordPill(
                candidate = candidate,
                isPrimary = index == 0,
                onClick = { onSuggestionClick(candidate.text) },
                isDarkTheme = isDarkTheme,
                modifier = Modifier.testTag("suggestion_pill_$index")
            )
        }
    }
}

@Composable
fun SuggestionWordPill(
    candidate: CandidatePhrase,
    isPrimary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = true
) {
    val pillBg = when {
        isDarkTheme && isPrimary -> Color(0xFF383838)
        isDarkTheme -> Color(0xFF262626)
        !isDarkTheme && isPrimary -> Color(0xFFE2E2E2)
        else -> Color(0xFFF0F0F0)
    }

    val borderColor = when {
        isDarkTheme && isPrimary -> Color(0xFFFF8A65)
        isDarkTheme -> Color(0xFF3A3A3A)
        !isDarkTheme && isPrimary -> Color(0xFFFF7043)
        else -> Color(0xFFCCCCCC)
    }

    val primaryTextColor = when {
        isDarkTheme -> Color(0xFFFFFFFF)
        else -> Color(0xFF1E1E1E)
    }

    val secondaryTextColor = when {
        isDarkTheme && isPrimary -> Color(0xFFFFCC80)
        isDarkTheme -> Color(0xFFB0B0B0)
        !isDarkTheme && isPrimary -> Color(0xFFE65100)
        else -> Color(0xFF666666)
    }

    val shape = RoundedCornerShape(16.dp)
    val context = androidx.compose.ui.platform.LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    val hapticManager = remember(context) { HapticFeedbackManager(context) }

    Box(
        modifier = modifier
            .clip(shape)
            .background(pillBg, shape)
            .border(if (isPrimary) 1.5.dp else 1.dp, borderColor, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                onClick = {
                    hapticManager.performKeyHaptic(view, HapticFeedbackManager.FeedbackType.KEY_LETTER)
                    onClick()
                }
            )
            .padding(horizontal = 12.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = candidate.text,
                color = primaryTextColor,
                fontSize = 13.sp,
                fontWeight = if (isPrimary) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (candidate.devanagari.isNotBlank() && !candidate.devanagari.equals(candidate.text, ignoreCase = true)) {
                Text(
                    text = candidate.devanagari,
                    color = secondaryTextColor,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
