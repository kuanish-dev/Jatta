package com.jatta.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.jatta.engine.session.Rung
import com.jatta.study.AiStatus
import com.jatta.study.Banner
import com.jatta.study.RestKind
import com.jatta.study.Screen
import com.jatta.study.StudyController
import com.jatta.study.TopicState
import com.jatta.study.TopicStatus
import com.jatta.study.exercise.Exercise
import com.jatta.study.judge.DiffKind
import com.jatta.study.judge.DiffPart
import com.jatta.study.judge.ModelState
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun StudyScreens(controller: StudyController, nowMs: () -> Long) {
    val screen by controller.screen.collectAsState()
    when (val s = screen) {
        is Screen.Home -> HomeScreen(s, controller)
        is Screen.Present -> PresentScreen(s, controller)
        is Screen.Question -> QuestionScreen(s, controller)
        is Screen.Checking -> CheckingScreen(s, controller)
        is Screen.Judged -> JudgedScreen(s, controller)
        is Screen.SelfCheck -> SelfCheckScreen(s, controller)
        is Screen.Reveal -> RevealScreen(s, controller)
        is Screen.Rest -> RestScreen(s, nowMs, controller::finishRest)
        is Screen.Done -> DoneScreen(s, controller::openHome)
    }
}

@Composable
private fun HomeScreen(home: Screen.Home, controller: StudyController) = Page {
    Text(home.courseTitle, style = MaterialTheme.typography.headlineSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Stat("Повторить", home.reviews, Modifier.weight(1f))
        Stat("Новое", home.newItems, Modifier.weight(1f))
    }
    if (home.postponed > 0) {
        Muted("Ещё ${home.postponed} повт. перенесено на следующие дни, чтобы не перегружать сегодняшний.")
    }
    val nothingToDo = home.reviews + home.newItems == 0
    Button(controller::startSession, Modifier.fillMaxWidth(), enabled = !nothingToDo) {
        Text(if (nothingToDo) "На сегодня всё" else "Начать занятие")
    }
    if (nothingToDo) Muted("Новые повторы появятся, когда память начнёт ослабевать.")
    Text("Темы", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
    home.topics.forEach { TopicRow(it) }
    AiSection(home.ai, controller)
}

/** State of the on-device model and the accuracy test it must pass before checking answers (М21). */
@Composable
private fun AiSection(ai: AiStatus, controller: StudyController) {
    Text("Проверка свободных ответов", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
    val test = ai.lastTest
    when {
        ai.model == ModelState.Unavailable ->
            Muted("На этом устройстве нет встроенной модели. Свободные ответы вы сверяете с образцом сами.")
        ai.model == ModelState.Downloadable -> {
            Muted("Можно скачать Gemini Nano — модель Google, которая работает на телефоне без интернета.")
            OutlinedButton(controller::downloadModel) { Text("Скачать модель") }
        }
        ai.model == ModelState.Downloading ->
            Muted("Модель скачивается" + (ai.downloadedBytes?.let { ": ${it / 1_000_000} МБ" } ?: "…"))
        ai.testedDone != null ->
            Muted("Проверяю точность модели: ${ai.testedDone} из ${ai.testTotal}")
        test == null -> {
            Muted("Модель готова. Прежде чем доверить ей ответы, проверим её на ${ai.testTotal} фразах с известным ответом.")
            OutlinedButton(controller::testModel) { Text("Проверить точность") }
        }
        else -> {
            Muted(
                "Совпала с эталоном: ${(test.agreement * 100).roundToInt()}% из ${test.total}. " +
                    "Пропустила ошибок: ${test.wrongAccepted}, придралась к верным: ${test.rightRejected}, " +
                    "ответила не по формату: ${test.unusable}.",
            )
            Text(
                if (test.enabled) "Модель проверяет свободные ответы" else "Ниже порога 90% — свободные ответы сверяете сами",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            OutlinedButton(controller::testModel) { Text("Проверить ещё раз") }
        }
    }
}

@Composable
private fun Stat(label: String, value: Int, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(value.toString(), style = MaterialTheme.typography.headlineMedium)
            Muted(label)
        }
    }
}

@Composable
private fun TopicRow(topic: TopicStatus) {
    val status = when (topic.state) {
        TopicState.Locked -> "закрыта, откроется после предыдущих"
        TopicState.Open -> "держится в памяти: ${topic.held} из ${topic.total}"
        TopicState.Mastered -> "освоена: ${topic.held} из ${topic.total}"
    }
    Column(Modifier.fillMaxWidth()) {
        Text(topic.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Muted(status)
        HorizontalDivider(Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun PresentScreen(present: Screen.Present, controller: StudyController) =
    StudyPage(present.remaining, present.banner, controller::stopSession) {
        Muted("Новое · ${present.topicTitle}")
        present.topicIntro?.let { Note(it) }
        val item = present.item
        Text(item.en, style = MaterialTheme.typography.headlineMedium)
        Text(item.ru, style = MaterialTheme.typography.titleMedium)
        if (item.note.isNotBlank()) Muted(item.note)
        item.examples.forEach { example ->
            Column {
                Text(example.en, style = MaterialTheme.typography.bodyLarge)
                Muted(example.ru)
            }
        }
        Button(controller::presented, Modifier.fillMaxWidth()) { Text("Понятно") }
    }

@Composable
private fun QuestionScreen(question: Screen.Question, controller: StudyController) =
    StudyPage(question.remaining, question.banner, controller::stopSession) {
        Muted(rungLabel(question.exercise.rung) + " · " + question.exercise.instruction)
        question.hint?.let { Note("Не совсем. Подсказка: $it") }
        when (val exercise = question.exercise) {
            is Exercise.Choice -> {
                Text(exercise.question, style = MaterialTheme.typography.headlineSmall)
                exercise.translation?.let { Muted(it) }
                exercise.options.forEachIndexed { index, option ->
                    OutlinedButton({ controller.choose(index) }, Modifier.fillMaxWidth()) { Text(option) }
                }
            }
            is Exercise.Typed -> {
                Text(exercise.question, style = MaterialTheme.typography.headlineSmall)
                exercise.translation?.let { Muted(it) }
                exercise.cue?.let { Muted("($it)") }
                AnswerField(question.id, singleLine = true, onSubmit = controller::submitText)
            }
            is Exercise.Free -> {
                Text(exercise.task, style = MaterialTheme.typography.titleLarge)
                AnswerField(question.id, singleLine = false, onSubmit = controller::submitText)
            }
            is Exercise.Speak -> {
                Text(exercise.prompt, style = MaterialTheme.typography.headlineSmall)
                Button(controller::showSpokenAnswer, Modifier.fillMaxWidth()) { Text("Сказал(а), показать ответ") }
            }
        }
    }

@Composable
private fun AnswerField(id: Int, singleLine: Boolean, onSubmit: (String) -> Unit) {
    var text by remember(id) { mutableStateOf("") }
    val focus = remember(id) { FocusRequester() }
    LaunchedEffect(id) { focus.requestFocus() }
    val submit = { if (text.isNotBlank()) onSubmit(text) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            imeAction = if (singleLine) ImeAction.Done else ImeAction.Default,
        ),
        keyboardActions = KeyboardActions(onDone = { submit() }),
    )
    Button(submit, Modifier.fillMaxWidth(), enabled = text.isNotBlank()) { Text("Проверить") }
}

@Composable
private fun CheckingScreen(checking: Screen.Checking, controller: StudyController) =
    StudyPage(checking.remaining, banner = null, onStop = controller::stopSession) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator()
            Text("Модель на телефоне проверяет ответ…", style = MaterialTheme.typography.bodyLarge)
        }
    }

@Composable
private fun JudgedScreen(judged: Screen.Judged, controller: StudyController) =
    StudyPage(judged.remaining, banner = null, onStop = controller::stopSession) {
        Text("Модель нашла ошибку", style = MaterialTheme.typography.titleMedium)
        Muted("Ваш вариант")
        Text(diffText(judged.diff, shown = DiffKind.Removed), style = MaterialTheme.typography.bodyLarge)
        Muted("Исправлено")
        Text(diffText(judged.diff, shown = DiffKind.Added), style = MaterialTheme.typography.bodyLarge)
        Note(judged.note)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(controller::acceptCorrection, Modifier.weight(1f)) { Text("Понятно") }
            OutlinedButton(controller::disputeCorrection, Modifier.weight(1f)) { Text("Модель ошиблась") }
        }
    }

/** One side of the correction: unchanged words plus either the removed or the added ones, highlighted. */
@Composable
private fun diffText(parts: List<DiffPart>, shown: DiffKind) = buildAnnotatedString {
    val highlight = if (shown == DiffKind.Removed) {
        SpanStyle(color = MaterialTheme.colorScheme.error, textDecoration = TextDecoration.LineThrough)
    } else {
        SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    }
    parts.filter { it.kind == DiffKind.Same || it.kind == shown }.forEachIndexed { index, part ->
        if (index > 0) append(' ')
        if (part.kind == DiffKind.Same) append(part.text) else withStyle(highlight) { append(part.text) }
    }
}

@Composable
private fun SelfCheckScreen(check: Screen.SelfCheck, controller: StudyController) =
    StudyPage(check.remaining, banner = null, onStop = controller::stopSession) {
        Text("Сравните с образцом", style = MaterialTheme.typography.titleMedium)
        check.response?.let {
            Muted("Ваш ответ")
            Text(it, style = MaterialTheme.typography.bodyLarge)
        }
        Muted("Образец")
        val references = (check.exercise as? Exercise.Free)?.references ?: listOf(check.exercise.answer)
        references.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
        Muted("Смысл и грамматика верны — значит верно, даже если слова другие.")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button({ controller.judgeSelf(true) }, Modifier.weight(1f)) { Text("Верно") }
            OutlinedButton({ controller.judgeSelf(false) }, Modifier.weight(1f)) { Text("Есть ошибка") }
        }
    }

@Composable
private fun RevealScreen(reveal: Screen.Reveal, controller: StudyController) =
    StudyPage(reveal.remaining, banner = null, onStop = controller::stopSession) {
        Text("Правильный ответ", style = MaterialTheme.typography.titleMedium)
        Text(reveal.exercise.answer, style = MaterialTheme.typography.headlineSmall)
        Note(reveal.note)
        Muted("Вернёмся к этому в конце занятия.")
        Button(controller::continueAfterReveal, Modifier.fillMaxWidth()) { Text("Дальше") }
    }

@Composable
private fun RestScreen(rest: Screen.Rest, nowMs: () -> Long, onFinish: () -> Unit) = Page {
    var now by remember(rest) { mutableLongStateOf(nowMs()) }
    LaunchedEffect(rest) {
        while (now < rest.untilMs) {
            delay(1_000)
            now = nowMs()
        }
    }
    val (title, text) = when (rest.kind) {
        RestKind.Scheduled -> "Перерыв" to "Встаньте, пройдитесь, посмотрите вдаль. После паузы внимание возвращается."
        RestKind.Fatigue -> "Похоже, вы устали" to "Ответы стали медленнее и с ошибками. Короткий перерыв — и продолжим."
        RestKind.Quiet -> "Тихий отдых" to "Несколько минут без телефона и без нового материала: так новое лучше закрепляется."
    }
    Text(title, style = MaterialTheme.typography.headlineSmall)
    Text(text, style = MaterialTheme.typography.bodyLarge)
    val left = ((rest.untilMs - now).coerceAtLeast(0) / 1_000).toInt()
    Text(
        "${left / 60}:${(left % 60).toString().padStart(2, '0')}",
        style = MaterialTheme.typography.displayMedium,
        modifier = Modifier.align(Alignment.CenterHorizontally),
    )
    Button(onFinish, Modifier.fillMaxWidth()) { Text(if (left > 0) "Продолжить раньше" else "Продолжить") }
}

@Composable
private fun DoneScreen(done: Screen.Done, onHome: () -> Unit) = Page {
    Text(if (done.endedEarly) "На сегодня хватит" else "Занятие окончено", style = MaterialTheme.typography.headlineSmall)
    if (done.endedEarly) Muted("Усталость не прошла после перерыва. Остальное перенесено, прогресс сохранён.")
    Text("С первого раза: ${done.rightFirstTime} из ${done.answered}", style = MaterialTheme.typography.titleMedium)
    Button(onHome, Modifier.fillMaxWidth()) { Text("На главную") }
}

@Composable
private fun StudyPage(remaining: Int, banner: Banner?, onStop: () -> Unit, content: @Composable ColumnScope.() -> Unit) = Page {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Muted("Осталось шагов: $remaining", Modifier.weight(1f))
        TextButton(onStop) { Text("Закончить") }
    }
    when (banner) {
        Banner.Correct -> Text("✓ Верно", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
        Banner.CheckedByAi -> Text("✓ Верно — проверила модель на телефоне", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
        Banner.CorrectWithHint -> Text("Верно с подсказкой — вернёмся к этому в конце", color = MaterialTheme.colorScheme.primary)
        null -> Unit
    }
    content()
}

@Composable
private fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.widthIn(max = 640.dp).align(Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            content()
        }
    }
}

@Composable
private fun Note(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Text(text, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Muted(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun rungLabel(rung: Rung) = when (rung) {
    Rung.Recognize -> "Узнавание"
    Rung.FillGap -> "Пропуск"
    Rung.Translate -> "Перевод"
    Rung.Compose -> "Своя фраза"
    Rung.Speak -> "Вслух"
}
