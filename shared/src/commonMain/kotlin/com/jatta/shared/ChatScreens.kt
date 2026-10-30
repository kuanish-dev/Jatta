package com.jatta.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.jatta.study.Screen
import com.jatta.study.StudyController
import com.jatta.study.chat.Change
import com.jatta.study.chat.ChatPartner
import com.jatta.study.chat.Review
import com.jatta.study.judge.DiffKind
import com.jatta.study.judge.DiffPart

@Composable
internal fun ChatTopicsScreen(screen: Screen.ChatTopics, controller: StudyController) = Page {
    BackBar("Разговор", Neon.Magenta, controller::openHome)
    Text("О чём поговорим?", style = MaterialTheme.typography.headlineMedium)
    Muted(
        "Собеседник — модель на телефоне. Каждую вашу фразу она сразу проверяет: под фразой появится разбор ошибок. " +
            "Слова из курса, которые вы уже встречали, подсвечены в его репликах.",
    )
    if (!screen.reviewTrusted) {
        Note("Модель ещё не прошла проверку точности уроков (она на главной), поэтому разбор ошибок приблизительный: спорное сверяйте с правилом курса.")
    }
    screen.topics.forEachIndexed { index, topic ->
        NeonOutlinedButton({ controller.startChat(topic.id) }, Modifier.fillMaxWidth().appear(index + 1)) { Text(topic.ru) }
    }
}

@Composable
internal fun ChatScreen(screen: Screen.Chat, controller: StudyController) {
    val busy = screen.typing || screen.messages.lastOrNull()?.review == Review.Checking
    val list = rememberLazyListState()
    LaunchedEffect(screen.messages.size, screen.typing, screen.messages.lastOrNull()?.review) {
        list.animateScrollToItem(list.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1)
    }
    Column(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 640.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BackBar("Разговор · ${screen.topic.ru}", Neon.Magenta, controller::openHome)
            LazyColumn(Modifier.weight(1f), state = list, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(screen.messages) { _, message ->
                    if (message.fromLearner) LearnerLine(message.text, message.review, screen.reviewTrusted) else PartnerLine(message.text, screen.words)
                }
                if (screen.typing) item { Typing() }
                if (screen.silent) item { Muted("Модель не ответила. Напишите ещё раз — она продолжит разговор.") }
            }
            ChatInput(enabled = !busy, onSend = controller::sendChat)
        }
    }
}

@Composable
private fun PartnerLine(text: String, words: List<String>) {
    Column(Modifier.fillMaxWidth(0.88f).appear(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Label(ChatPartner.NAME, color = Neon.Cyan)
        Panel(Modifier.fillMaxWidth(), accent = Neon.Cyan.copy(alpha = 0.45f)) {
            Text(highlighted(text, words.map { it.lowercase() }.toSet()), style = MaterialTheme.typography.bodyLarge)
        }
        ListenButton(text, "▶ СЛУШАТЬ")
    }
}

@Composable
private fun LearnerLine(text: String, review: Review?, trusted: Boolean) {
    Column(Modifier.fillMaxWidth().appear(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Panel(Modifier.fillMaxWidth(0.88f), accent = Neon.Magenta.copy(alpha = 0.45f)) {
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
        Column(Modifier.fillMaxWidth(0.88f)) { review?.let { ReviewBlock(it, trusted) } }
    }
}

@Composable
private fun ReviewBlock(review: Review, trusted: Boolean) = when (review) {
    Review.Checking -> Muted("Проверяю…", Modifier.pulse(fadeBy = 0.6f))
    Review.Fine -> Label("✓ Без ошибок", color = Neon.Acid, modifier = Modifier.pop(origin = LeftCenter))
    Review.Unclear -> Muted("Модель не смогла разобрать эту фразу.")
    is Review.Fixed -> Mistakes(review, trusted)
}

@Composable
private fun Mistakes(fixed: Review.Fixed, trusted: Boolean) {
    Panel(Modifier.fillMaxWidth().shake(), accent = Neon.Danger.copy(alpha = 0.55f)) {
        Label(if (trusted) "Разбор" else "Разбор · приблизительно", color = Neon.Danger)
        fixed.changes.forEach { Text(changeText(it), style = MaterialTheme.typography.bodyLarge) }
        Muted("Как правильно")
        Text(correctedText(fixed.diff), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Typing() {
    Row(Modifier.appear(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Label(ChatPartner.NAME, color = Neon.Cyan)
        Muted("печатает…", Modifier.pulse(fadeBy = 0.7f, periodMs = 700))
    }
}

@Composable
private fun changeText(change: Change) = buildAnnotatedString {
    val wrong = SpanStyle(color = Neon.Danger, textDecoration = TextDecoration.LineThrough)
    val right = SpanStyle(color = Neon.Cyan, fontWeight = FontWeight.Bold)
    when {
        change.wrong == null -> withStyle(right) { append("+ ${change.right}") }
        change.right == null -> withStyle(wrong) { append("− ${change.wrong}") }
        else -> {
            withStyle(wrong) { append(change.wrong) }
            append("  →  ")
            withStyle(right) { append(change.right) }
        }
    }
}

@Composable
private fun correctedText(diff: List<DiffPart>) = buildAnnotatedString {
    val added = SpanStyle(color = Neon.Cyan, fontWeight = FontWeight.Bold)
    diff.filter { it.kind != DiffKind.Removed }.forEachIndexed { index, part ->
        if (index > 0) append(' ')
        if (part.kind == DiffKind.Added) withStyle(added) { append(part.text) } else append(part.written)
    }
}
