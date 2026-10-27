package com.jatta.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.jatta.study.translate.TranslationTask
import kotlin.math.roundToInt

@Composable
internal fun TranslateScreen(screen: Screen.Translate, controller: StudyController) =
    Page {
        Label("Переведите на английский", color = Neon.Magenta)
        Text(screen.task.source, Modifier.pop(origin = LeftCenter), style = MaterialTheme.typography.headlineSmall)
        if (screen.steps.done == 0) {
            Muted("Фразы из слов, которые вы держите в памяти. Засчитывается от 80% сходства с переводом человека или когда смысл передан.")
        }
        AnswerField(screen.id, singleLine = false, onSubmit = controller::submitTranslation)
    }

@Composable
internal fun TranslatedScreen(screen: Screen.Translated, controller: StudyController) =
    Page {
        val task = screen.task
        val similarity = "сходство ${countUp((screen.score * 100).roundToInt())}%"
        val passed = Modifier.pop(origin = LeftCenter)
        when (screen.outcome) {
            TranslationOutcome.Close -> Label("✓ Засчитано · $similarity", color = Neon.Acid, modifier = passed)
            TranslationOutcome.RightByAi -> Label("✓ Верно другими словами — проверила модель · $similarity", color = Neon.Acid, modifier = passed)
            TranslationOutcome.WrongByAi -> Label("Модель: смысл другой · $similarity", color = Neon.Danger, modifier = Modifier.shake())
            TranslationOutcome.LearnerDecides -> Label("$similarity — меньше 80%", color = Neon.Magenta, modifier = Modifier.appear())
        }
        Muted(task.source)
        Muted("Ваш перевод")
        Text(diffText(screen.diff, shown = DiffKind.Removed), Modifier.appear(1), style = MaterialTheme.typography.bodyLarge)
        Muted("Ближайший перевод человека")
        Text(diffText(screen.diff, shown = DiffKind.Added), Modifier.appear(2), style = MaterialTheme.typography.bodyLarge)
        ListenButton(screen.target, "▶ СЛУШАТЬ")
        if (screen.outcome == TranslationOutcome.LearnerDecides) OtherTranslations(task, screen.target)
        SentenceCredit(task)
        when (screen.outcome) {
            TranslationOutcome.Close, TranslationOutcome.RightByAi ->
                NeonButton(controller::nextTranslation, Modifier.fillMaxWidth()) { Text("ДАЛЬШЕ") }
            TranslationOutcome.WrongByAi -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NeonButton({ controller.judgeTranslation(false) }, Modifier.weight(1f)) { Text("ПОНЯТНО") }
                NeonOutlinedButton({ controller.judgeTranslation(true) }, Modifier.weight(1f)) { Text("МОДЕЛЬ ОШИБЛАСЬ") }
            }
            TranslationOutcome.LearnerDecides -> {
                Muted("Смысл передан — значит верно, даже если слова другие или есть мелкие ошибки. Неверно — если смысл другой.")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NeonButton({ controller.judgeTranslation(true) }, Modifier.weight(1f)) { Text("ВЕРНО") }
                    NeonOutlinedButton({ controller.judgeTranslation(false) }, Modifier.weight(1f)) { Text("ЕСТЬ ОШИБКА") }
                }
            }
        }
    }

@Composable
private fun OtherTranslations(task: TranslationTask, closest: String) {
    val others = task.references.filter { AnswerCheck.normalize(it) != AnswerCheck.normalize(closest) }
    if (others.isEmpty()) return
    Muted("Ещё переводы людей")
    Panel(Modifier.fillMaxWidth().appear(3)) {
        others.forEach { Text(it, style = MaterialTheme.typography.bodyLarge, color = Neon.Cyan) }
    }
}

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
