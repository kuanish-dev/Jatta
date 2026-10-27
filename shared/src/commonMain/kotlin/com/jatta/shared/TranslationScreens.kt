package com.jatta.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.jatta.study.Screen
import com.jatta.study.StudyController
import com.jatta.study.TranslationOutcome
import com.jatta.study.exercise.AnswerCheck
import com.jatta.study.judge.DiffKind
import com.jatta.study.translate.Direction
import com.jatta.study.translate.TranslationTask
import kotlin.math.roundToInt

/** М25: a sentence of held words to translate, at the end of a session. */
@Composable
internal fun TranslateScreen(screen: Screen.Translate, controller: StudyController) =
    StudyPage(screen.steps, banner = null, onStop = controller::stopSession, counter = "Перевод") {
        val task = screen.task
        Label(if (task.direction == Direction.EnRu) "Переведите на русский" else "Переведите на английский", color = Neon.Magenta)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(task.source, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            if (task.direction == Direction.EnRu) ListenButton(task.english)
        }
        if (screen.steps.done == 0) Muted("Фразы из слов, которые вы держите в памяти. Засчитывается от 80% сходства с переводом человека.")
        AnswerField(screen.id, singleLine = false, onSubmit = controller::submitTranslation)
    }

/** М25: the translation against the closest human one, its similarity, and the verdict or the question. */
@Composable
internal fun TranslatedScreen(screen: Screen.Translated, controller: StudyController) =
    StudyPage(screen.steps, banner = null, onStop = controller::stopSession, counter = "Перевод") {
        val task = screen.task
        val similarity = "сходство ${(screen.score * 100).roundToInt()}%"
        when (screen.outcome) {
            TranslationOutcome.Close -> Label("✓ Засчитано · $similarity", color = Neon.Acid)
            TranslationOutcome.RightByAi -> Label("✓ Верно другими словами — проверила модель · $similarity", color = Neon.Acid)
            TranslationOutcome.FixedByAi -> Label("Модель нашла ошибку · $similarity", color = Neon.Danger)
            TranslationOutcome.LearnerDecides -> Label("$similarity — меньше 80%", color = Neon.Magenta)
        }
        Muted(task.source)
        Muted("Ваш перевод")
        Text(diffText(screen.diff, shown = DiffKind.Removed), style = MaterialTheme.typography.bodyLarge)
        Muted(if (screen.outcome == TranslationOutcome.FixedByAi) "Исправлено" else "Ближайший перевод человека")
        Text(diffText(screen.diff, shown = DiffKind.Added), style = MaterialTheme.typography.bodyLarge)
        ListenButton(if (task.direction == Direction.RuEn) screen.target else task.english, "▶ СЛУШАТЬ")
        if (screen.outcome == TranslationOutcome.LearnerDecides) OtherTranslations(task, screen.target)
        SentenceCredit(task)
        when (screen.outcome) {
            TranslationOutcome.Close, TranslationOutcome.RightByAi ->
                NeonButton(controller::nextTranslation, Modifier.fillMaxWidth()) { Text("ДАЛЬШЕ") }
            TranslationOutcome.FixedByAi -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NeonButton({ controller.judgeTranslation(false) }, Modifier.weight(1f)) { Text("ПОНЯТНО") }
                NeonOutlinedButton({ controller.judgeTranslation(true) }, Modifier.weight(1f)) { Text("МОДЕЛЬ ОШИБЛАСЬ") }
            }
            TranslationOutcome.LearnerDecides -> {
                Muted("Смысл и грамматика верны — значит верно, даже если слова другие.")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NeonButton({ controller.judgeTranslation(true) }, Modifier.weight(1f)) { Text("ВЕРНО") }
                    NeonOutlinedButton({ controller.judgeTranslation(false) }, Modifier.weight(1f)) { Text("ЕСТЬ ОШИБКА") }
                }
            }
        }
    }

/** More human translations to compare with, besides the closest one. */
@Composable
private fun OtherTranslations(task: TranslationTask, closest: String) {
    val others = task.references.filter { AnswerCheck.normalize(it) != AnswerCheck.normalize(closest) }
    if (others.isEmpty()) return
    Muted("Ещё переводы людей")
    Panel(Modifier.fillMaxWidth()) {
        others.forEach { Text(it, style = MaterialTheme.typography.bodyLarge, color = Neon.Cyan) }
    }
}

/** Tatoeba's license asks for credit; the sentence page names its authors and translators. */
@Composable
private fun SentenceCredit(task: TranslationTask) {
    Text(
        buildAnnotatedString {
            withLink(LinkAnnotation.Url(task.sourceUrl, TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline)))) {
                append("Предложение и переводы: Tatoeba · CC BY 2.0 FR")
            }
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
