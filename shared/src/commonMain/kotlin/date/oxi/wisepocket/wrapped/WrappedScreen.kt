package date.oxi.wisepocket.wrapped

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import date.oxi.wisepocket.insights.RankEntry
import date.oxi.wisepocket.insights.WrappedCard
import org.koin.compose.viewmodel.koinViewModel

/**
 * The Financial Wrapped: the user's own numbers, one per screen, swipeable.
 *
 * A renderer and nothing more. Which cards exist, and what they say, is decided in `WrappedStory` — so the
 * claims this screen makes about someone's money are all unit-tested, and the only thing that can go wrong
 * here is how they look.
 */
@Composable
fun WrappedScreen(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: WrappedViewModel = koinViewModel()
    val cards by viewModel.cards.collectAsStateWithLifecycle()

    if (cards.isEmpty()) {
        EmptyPanel(onClose, modifier)
        return
    }

    Box(modifier.fillMaxSize()) {
        val pagerState = rememberPagerState { cards.size }
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            StoryCard(cards[page], palette(page))
        }
        // The way out of a full-screen story has to be visible on every card: there is no tab bar behind
        // this any more, and swiping only ever moves sideways.
        TextButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopEnd).safeContentPadding().padding(8.dp),
        ) {
            Text("Close", color = Color.White)
        }
        Dots(
            count = cards.size,
            selected = pagerState.currentPage,
            modifier = Modifier.align(Alignment.BottomCenter).safeContentPadding().padding(bottom = 20.dp),
        )
    }
}

@Composable
private fun EmptyPanel(onClose: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("🎁", style = MaterialTheme.typography.displayLarge)
            Text("No Wrapped yet", style = MaterialTheme.typography.titleLarge)
            Text(
                "Import a bank statement or add a few transactions, and your year in money appears here.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onClose) { Text("Close") }
        }
    }
}

@Composable
private fun StoryCard(card: WrappedCard, colors: Pair<Color, Color>) {
    val (top, bottom) = colors
    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .padding(horizontal = 28.dp, vertical = 56.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (card) {
            is WrappedCard.Intro -> IntroCard(card)
            is WrappedCard.BigNumber -> BigNumberCard(card)
            is WrappedCard.Ranking -> RankingCard(card)
            is WrappedCard.Nudge -> NudgeCard(card)
        }
    }
}

@Composable
private fun IntroCard(card: WrappedCard.Intro) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("✨", style = MaterialTheme.typography.displayMedium)
        Text(
            "Your Wrapped",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Text(
            card.period,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = SUBTLE),
        )
        Text(
            "${card.transactionCount} transactions, all read on this phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = SUBTLE),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Swipe →",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = SUBTLE),
        )
    }
}

@Composable
private fun BigNumberCard(card: WrappedCard.BigNumber) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(card.emoji, style = MaterialTheme.typography.displayMedium)
        Text(
            card.label,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = SUBTLE),
            textAlign = TextAlign.Center,
        )
        Text(
            card.value,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Text(
            card.caption,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = SUBTLE),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RankingCard(card: WrappedCard.Ranking) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(card.emoji, style = MaterialTheme.typography.displaySmall)
        Text(
            card.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            color = Color.White,
        )
        card.entries.forEach { Bar(it) }
    }
}

/** Bars are drawn from the precomputed share — the screen never derives a number it displays. */
@Composable
private fun Bar(entry: RankEntry) {
    val width by animateFloatAsState(entry.share, label = "bar")
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${entry.emoji}  ${entry.label}", color = Color.White, modifier = Modifier.weight(1f))
            Text(entry.value, color = Color.White, fontWeight = FontWeight.Bold)
        }
        Box(
            Modifier.fillMaxWidth().height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color.White.copy(alpha = TRACK)),
        ) {
            Box(
                Modifier.fillMaxWidth(width.coerceIn(MIN_BAR, 1f)).fillMaxHeight()
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color.White),
            )
        }
    }
}

@Composable
private fun NudgeCard(card: WrappedCard.Nudge) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(card.emoji, style = MaterialTheme.typography.displayMedium)
        Text(
            card.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Text(
            card.message,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = SUBTLE),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Dots(count: Int, selected: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            Box(
                Modifier.size(if (i == selected) DOT_ON else DOT_OFF)
                    .clip(RoundedCornerShape(DOT_ON))
                    .background(Color.White.copy(alpha = if (i == selected) 1f else TRACK)),
            )
        }
    }
}

/**
 * Card backgrounds, cycled by position.
 *
 * Still hardcoded, and still deliberately outside the Material scheme: a Wrapped is meant to look nothing
 * like the rest of the app — that contrast is the whole feature, and a story that obeyed the app's theme
 * would just be the app with bigger text.
 *
 * What changed with the design system is that these are now a *family* rather than six unrelated Material
 * hues. Every pair travels through blue-violet and ends deep, so the story reads as somewhere the app could
 * plausibly have taken you — the old teal→green and orange→rust pairs shared nothing with `#1B1BD1` or with
 * each other. The brand blue itself appears exactly once, as the last card's opening: the story leaves you
 * where the app begins.
 *
 * All text on these is white, so every stop stays dark enough to carry it — that constraint is what rules
 * out the bright cyans and magentas this family otherwise invites.
 */
private fun palette(page: Int): Pair<Color, Color> = PALETTE[page % PALETTE.size]

private val PALETTE = listOf(
    Color(0xFF3A0CA3) to Color(0xFF1B1BD1), // violet → brand
    Color(0xFF0353A4) to Color(0xFF012A4A), // azure → navy
    Color(0xFF6D28D9) to Color(0xFF2410A8), // purple → indigo
    Color(0xFF0E7490) to Color(0xFF0A2A5E), // teal → deep blue
    Color(0xFFA21CAF) to Color(0xFF4C1D95), // magenta → violet
    Color(0xFF1B1BD1) to Color(0xFF0A0A4F), // brand → midnight
)

private const val SUBTLE = 0.75f
private const val TRACK = 0.25f

/** A sliver, so a tiny category still reads as a bar rather than as nothing. */
private const val MIN_BAR = 0.02f

private val DOT_ON = 8.dp
private val DOT_OFF = 6.dp
