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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.jatta.engine.session.Rung
import com.jatta.study.AiStatus
import com.jatta.study.Banner
import com.jatta.study.CourseProgress
import com.jatta.study.RestKind
import com.jatta.study.Screen
import com.jatta.study.Steps
import com.jatta.study.StudyController
import com.jatta.study.ThemeChoice
import com.jatta.study.TopicState
import com.jatta.study.TopicStatus
import com.jatta.study.content.KindFile
import com.jatta.study.exercise.Exercise
import com.jatta.study.judge.DiffKind
import com.jatta.study.judge.DiffPart
import com.jatta.study.judge.ModelState
import com.jatta.study.reading.Reading
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun StudyScreens(controller: StudyController, nowMs: () -> Long) {
    val screen by controller.screen.collectAsState()
    when (val s = screen) {
        is Screen.Home -> HomeScreen(s, controller)
        is Screen.Read -> ReadScreen(s, controller)
        is Screen.PlacementIntro -> PlacementIntroScreen(s, controller)
        is Screen.PlacementWord -> PlacementWordScreen(s, controller)
        is Screen.PlacementGrammar -> PlacementGrammarScreen(s, controller)
        is Screen.PlacementDone -> PlacementDoneScreen(s, controller)
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
    val theme by controller.theme.collectAsState()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Label("Jatta // заучивай", color = Neon.Cyan, modifier = Modifier.weight(1f))
        TextButton(controller::cycleTheme) {
            val name = when (theme) {
                ThemeChoice.System -> "система"
                ThemeChoice.Light -> "светлая"
                ThemeChoice.Dark -> "тёмная"
            }
            Label("Тема: $name")
        }
    }
    Text(home.courseTitle, style = MaterialTheme.typography.headlineMedium)
    home.vocabulary?.let { Muted("Словарь при старте: ≈ ${it.words} из ${it.total} самых частых слов (${levelOf(it.words)})") }
    ProgressPanel(home.progress)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Stat("Повторить", home.reviews, Neon.Acid, Modifier.weight(1f))
        Stat("Новое", home.newItems, Neon.Magenta, Modifier.weight(1f))
    }
    if (home.postponed > 0) {
        Muted("Ещё ${home.postponed} повт. перенесено на следующие дни, чтобы не перегружать сегодняшний.")
    }
    val nothingToDo = home.reviews + home.newItems == 0
    NeonButton(controller::startSession, Modifier.fillMaxWidth(), enabled = !nothingToDo) {
        Text(if (nothingToDo) "НА СЕГОДНЯ ВСЁ" else "НАЧАТЬ ЗАНЯТИЕ")
    }
    if (nothingToDo) Muted("Новые повторы появятся, когда память начнёт ослабевать.")
    home.reading?.let { ReadingCard(it, controller::openReading) }
    Label("Темы · в памяти / всего", modifier = Modifier.padding(top = 8.dp))
    home.topics.forEach { TopicRow(it) }
    if (home.topics.any { it.state == TopicState.Locked }) Muted("Закрытые темы откроются, когда темы до них будут держаться в памяти.")
    AiSection(home.ai, controller)
}

/** М23: before the first lesson, offer to find where the course should start. */
@Composable
private fun PlacementIntroScreen(intro: Screen.PlacementIntro, controller: StudyController) = Page {
    Label("Jatta // уровень", color = Neon.Cyan)
    Text("С чего начнём?", style = MaterialTheme.typography.headlineMedium)
    Text(
        "Короткий тест покажет, что вы уже знаете, и курс начнётся с вашего уровня: " +
            "${intro.words} слов «знаю / не знаю» и ${intro.questions} вопросов по грамматике, около 5 минут.",
        style = MaterialTheme.typography.bodyLarge,
    )
    Note(
        "Некоторых слов в тесте не существует: так тест учитывает угадывание. Всё, что тест отметит знакомым, " +
            "придёт на проверку через пару дней, поэтому ошибка теста ничего не сломает.",
    )
    NeonButton(controller::startPlacement, Modifier.fillMaxWidth()) { Text("ПРОЙТИ ТЕСТ") }
    NeonOutlinedButton(controller::skipPlacement, Modifier.fillMaxWidth()) { Text("НАЧАТЬ С НУЛЯ") }
}

@Composable
private fun PlacementWordScreen(probe: Screen.PlacementWord, controller: StudyController) = PlacementPage(probe.steps) {
    Label("Слова // знаете, что значит?", color = Neon.Magenta)
    Text(
        probe.word,
        Modifier.fillMaxWidth().padding(vertical = 24.dp),
        style = MaterialTheme.typography.headlineLarge,
        color = Neon.Cyan,
        textAlign = TextAlign.Center,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NeonButton({ controller.answerPlacementWord(true) }, Modifier.weight(1f)) { Text("ЗНАЮ") }
        NeonOutlinedButton({ controller.answerPlacementWord(false) }, Modifier.weight(1f)) { Text("НЕ ЗНАЮ") }
    }
    Muted("«Знаю» — можете сказать, что оно значит. Сомневаетесь — «не знаю».")
}

@Composable
private fun PlacementGrammarScreen(probe: Screen.PlacementGrammar, controller: StudyController) = PlacementPage(probe.steps) {
    Label("Грамматика // " + probe.exercise.instruction, color = Neon.Magenta)
    Text(probe.exercise.question, style = MaterialTheme.typography.headlineSmall)
    probe.exercise.translation?.let { Muted(it) }
    probe.exercise.options.forEachIndexed { index, option ->
        NeonOutlinedButton({ controller.answerPlacementGrammar(index) }, Modifier.fillMaxWidth()) { Text(option) }
    }
}

@Composable
private fun PlacementDoneScreen(done: Screen.PlacementDone, controller: StudyController) = Page {
    val result = done.result
    Label("Результат", color = Neon.Acid)
    Panel(Modifier.fillMaxWidth(), accent = Neon.Cyan.copy(alpha = 0.5f)) {
        Label("Словарь")
        NeonNumber("≈ ${result.estimatedWords}")
        Muted("из ${result.totalWords} самых частых слов английского · ${levelOf(result.estimatedWords)}")
        NeonBar(result.estimatedWords.toFloat() / result.totalWords, segments = 24, height = 12.dp)
    }
    if (!result.reliable) {
        Note("Вы отметили много несуществующих слов, поэтому отдельным ответам «знаю» тест не доверяет: слова курса начнутся с начала.")
    }
    val units = plural(result.knownItems.size, "единица", "единицы", "единиц")
    Muted("Отмечено знакомым: ${result.knownItems.size} $units курса. Они придут на проверку через пару дней; что забыто, вернётся в учёбу.")
    if (result.knownTopics.isNotEmpty()) Muted("Грамматика, которую вы знаете: ${result.knownTopics.joinToString()}.")
    Muted("Уровень оценён по словарю (Milton 2010: A2 ≈ 1500–2500 частых слов, B1 ≈ 3000). Тест меряет 2800 самых частых слов, выше B1 он не различает.")
    NeonButton(controller::openHome, Modifier.fillMaxWidth()) { Text("К КУРСУ") }
}

/** Placement steps: a progress bar, no way out but finishing or the back of the phone. */
@Composable
private fun PlacementPage(steps: Steps, content: @Composable ColumnScope.() -> Unit) = Page {
    Label("Тест уровня · ${steps.done + 1}/${steps.done + steps.remaining}", color = Neon.Cyan)
    NeonBar(steps.fraction, height = 6.dp)
    content()
}

/** Russian noun form for a count: 1 единица, 3 единицы, 5 единиц, 21 единица. */
private fun plural(n: Int, one: String, few: String, many: String) = when {
    n % 10 == 1 && n % 100 != 11 -> one
    n % 10 in 2..4 && n % 100 !in 12..14 -> few
    else -> many
}

/** A rough level from vocabulary size, after Milton (2010); the test can't tell levels above B1 apart. */
private fun levelOf(words: Int) = when {
    words < 1500 -> "около A1"
    words < 2750 -> "около A2"
    else -> "B1 или выше"
}

/** М1, М2: the course in memory: items that would still be recalled tomorrow, out of all items. */
@Composable
private fun ProgressPanel(progress: CourseProgress) {
    val fraction = if (progress.total == 0) 0f else progress.held.toFloat() / progress.total
    Panel(Modifier.fillMaxWidth(), accent = Neon.Cyan.copy(alpha = 0.5f)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Label("Память")
                NeonNumber("${progress.held}/${progress.total}")
            }
            Text(
                "${(fraction * 100).roundToInt()}%",
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = MaterialTheme.typography.labelLarge.fontFamily),
                color = Neon.Magenta,
            )
        }
        NeonBar(fraction, segments = 24, height = 12.dp)
        Muted("Держится в памяти: ${progress.held} · встречено: ${progress.met} · всего в курсе: ${progress.total}")
    }
}

@Composable
private fun Stat(label: String, value: Int, color: Color, modifier: Modifier) {
    Panel(modifier, accent = color.copy(alpha = 0.45f)) {
        NeonNumber(value.toString(), color = color, style = MaterialTheme.typography.headlineLarge.copy(fontFamily = MaterialTheme.typography.displaySmall.fontFamily))
        Label(label)
    }
}

@Composable
private fun TopicRow(topic: TopicStatus) {
    val fraction = if (topic.total == 0) 0f else topic.held.toFloat() / topic.total
    val (status, color) = when (topic.state) {
        TopicState.Locked -> "закрыта" to Neon.Muted
        TopicState.Open -> "${topic.held}/${topic.total}" to Neon.Cyan
        TopicState.Mastered -> "✓ ${topic.held}/${topic.total}" to Neon.Acid
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(topic.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Label(status, color = color)
        }
        NeonBar(fraction, segments = topic.total.coerceIn(1, 24), height = 5.dp, dim = topic.state == TopicState.Locked)
    }
}

/** М11: a real text the learner can read now. */
@Composable
private fun ReadingCard(reading: Reading, onOpen: () -> Unit) {
    Panel(Modifier.fillMaxWidth(), accent = Neon.Magenta.copy(alpha = 0.5f)) {
        Label("Почитать", color = Neon.Magenta)
        Text(reading.text.title, style = MaterialTheme.typography.titleMedium)
        Muted(
            reading.text.source.name + " · " +
                if (reading.courseWords.isEmpty()) "почти все слова знакомы" else "слов из курса: ${reading.courseWords.size}",
        )
        NeonOutlinedButton(onOpen) { Text("ЧИТАТЬ") }
    }
}

@Composable
private fun ReadScreen(read: Screen.Read, controller: StudyController) = Page {
    val reading = read.reading
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Label("Чтение", color = Neon.Magenta, modifier = Modifier.weight(1f))
        TextButton(controller::openHome) { Text("ЗАКРЫТЬ") }
    }
    Text(reading.text.title, style = MaterialTheme.typography.headlineSmall)
    Panel(Modifier.fillMaxWidth()) {
        Text(highlighted(reading.text.text, reading.courseWords), style = MaterialTheme.typography.bodyLarge)
    }
    if (reading.courseWords.isNotEmpty()) Muted("Выделены слова из курса: встреча в живом тексте закрепляет их.")
    if (reading.unknownWords.isNotEmpty()) {
        Muted("Новых слов: ${reading.unknownWords.size} (${reading.unknownWords.sorted().joinToString()}) — попробуйте понять их по контексту.")
    }
    val source = reading.text.source
    Text(
        buildAnnotatedString {
            append("Источник: ${source.work} — ${source.author}. ${source.name}, ${source.license}. ${source.changes}. ")
            withLink(LinkAnnotation.Url(source.url, TextLinkStyles(SpanStyle(color = Neon.Cyan, textDecoration = TextDecoration.Underline)))) {
                append(source.url)
            }
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    NeonButton(controller::finishReading, Modifier.fillMaxWidth()) { Text("ПРОЧИТАЛ(А)") }
}

/** The text with the learner's course words in neon. */
@Composable
private fun highlighted(text: String, words: Set<String>) = buildAnnotatedString {
    val style = SpanStyle(color = Neon.Cyan, fontWeight = FontWeight.Bold)
    var at = 0
    Regex("[A-Za-z]+(?:'[A-Za-z]+)?").findAll(text).forEach { match ->
        append(text.substring(at, match.range.first))
        if (match.value.lowercase() in words) withStyle(style) { append(match.value) } else append(match.value)
        at = match.range.last + 1
    }
    append(text.substring(at))
}

/** State of the on-device model and the accuracy test it must pass before checking answers (М21). */
@Composable
private fun AiSection(ai: AiStatus, controller: StudyController) {
    Label("Проверка свободных ответов", modifier = Modifier.padding(top = 8.dp))
    val test = ai.lastTest
    val testedDone = ai.testedDone
    when {
        ai.model == ModelState.Unavailable ->
            Muted("На этом устройстве нет встроенной модели. Свободные ответы вы сверяете с образцом сами.")
        ai.model == ModelState.Downloadable -> {
            Muted("Можно скачать Gemini Nano — модель Google, которая работает на телефоне без интернета.")
            NeonOutlinedButton(controller::downloadModel) { Text("СКАЧАТЬ МОДЕЛЬ") }
        }
        ai.model == ModelState.Downloading ->
            Muted("Модель скачивается" + (ai.downloadedBytes?.let { ": ${it / 1_000_000} МБ" } ?: "…"))
        testedDone != null -> {
            Muted("Проверяю точность модели: $testedDone из ${ai.testTotal}")
            NeonBar(testedDone.toFloat() / ai.testTotal, height = 4.dp)
        }
        test == null -> {
            Muted("Модель готова. Прежде чем доверить ей ответы, проверим её на ${ai.testTotal} фразах с известным ответом.")
            NeonOutlinedButton(controller::testModel) { Text("ПРОВЕРИТЬ ТОЧНОСТЬ") }
        }
        else -> {
            Muted(
                "Совпала с эталоном: ${(test.agreement * 100).roundToInt()}% из ${test.total}. " +
                    "Пропустила ошибок: ${test.wrongAccepted}, придралась к верным: ${test.rightRejected}, " +
                    "ответила не по формату: ${test.unusable}.",
            )
            Label(
                if (test.enabled) "Модель проверяет свободные ответы" else "Ниже порога 90% — сверяете сами",
                color = if (test.enabled) Neon.Cyan else Neon.Magenta,
            )
            NeonOutlinedButton(controller::testModel) { Text("ПРОВЕРИТЬ ЕЩЁ РАЗ") }
        }
    }
}

@Composable
private fun PresentScreen(present: Screen.Present, controller: StudyController) =
    StudyPage(present.steps, present.banner, controller::stopSession) {
        Label("Новое · ${present.topicTitle}", color = Neon.Magenta)
        present.topicIntro?.let { Note(it) }
        val item = present.item
        val isWord = item.kind != KindFile.Rule
        if (isWord) SpeakOnShow(item.id, item.en)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(item.en, Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium, color = Neon.Cyan)
            if (isWord) ListenButton(item.en, "▶ СЛУШАТЬ")
        }
        Text(item.ru, style = MaterialTheme.typography.titleMedium)
        if (item.note.isNotBlank()) Muted(item.note)
        Panel(Modifier.fillMaxWidth()) {
            item.examples.forEach { example ->
                Row {
                    Column(Modifier.weight(1f)) {
                        Text(example.en, style = MaterialTheme.typography.bodyLarge)
                        Muted(example.ru)
                    }
                    ListenButton(example.en)
                }
            }
        }
        NeonButton(controller::presented, Modifier.fillMaxWidth()) { Text("ПОНЯТНО") }
    }

@Composable
private fun QuestionScreen(question: Screen.Question, controller: StudyController) =
    StudyPage(question.steps, question.banner, controller::stopSession) {
        Label(rungLabel(question.exercise.rung) + " // " + question.exercise.instruction, color = Neon.Magenta)
        question.hint?.let { hint ->
            // М13: a fast wrong answer was a confident mistake; pointing it out helps it get corrected.
            val lead = if (question.confidentError) "Ответ быстрый, но неверный — запомните внимательно." else "Не совсем."
            Note("$lead Подсказка: $hint")
        }
        when (val exercise = question.exercise) {
            is Exercise.Choice -> {
                // An English word to recognise is played at once; a sentence with a gap would give the answer away.
                val spoken = exercise.question == exercise.listen
                if (spoken) SpeakOnShow(question.id, exercise.listen)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(exercise.question, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                    if (spoken) ListenButton(exercise.listen)
                }
                exercise.translation?.let { Muted(it) }
                exercise.options.forEachIndexed { index, option ->
                    NeonOutlinedButton({ controller.choose(index) }, Modifier.fillMaxWidth()) { Text(option) }
                }
            }
            is Exercise.Typed -> {
                Text(exercise.question, style = MaterialTheme.typography.headlineSmall)
                exercise.translation?.let { Muted(it) }
                exercise.cue?.let { Label("($it)", color = Neon.Acid) }
                AnswerField(question.id, singleLine = true, onSubmit = controller::submitText)
            }
            is Exercise.Free -> {
                Text(exercise.task, style = MaterialTheme.typography.titleLarge)
                AnswerField(question.id, singleLine = false, onSubmit = controller::submitText)
            }
            is Exercise.Speak -> {
                Text(exercise.prompt, style = MaterialTheme.typography.headlineSmall)
                NeonButton(controller::showSpokenAnswer, Modifier.fillMaxWidth()) { Text("СКАЗАЛ(А), ПОКАЗАТЬ ОТВЕТ") }
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
        textStyle = MaterialTheme.typography.bodyLarge,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Neon.Cyan,
            unfocusedBorderColor = Neon.Line,
            cursorColor = Neon.Magenta,
            focusedContainerColor = Neon.Panel,
            unfocusedContainerColor = Neon.Panel,
        ),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            imeAction = if (singleLine) ImeAction.Done else ImeAction.Default,
        ),
        keyboardActions = KeyboardActions(onDone = { submit() }),
    )
    NeonButton(submit, Modifier.fillMaxWidth(), enabled = text.isNotBlank()) { Text("ПРОВЕРИТЬ") }
}

@Composable
private fun CheckingScreen(checking: Screen.Checking, controller: StudyController) =
    StudyPage(checking.steps, banner = null, onStop = controller::stopSession) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(color = Neon.Magenta)
            Text("Модель на телефоне проверяет ответ…", style = MaterialTheme.typography.bodyLarge)
        }
    }

@Composable
private fun JudgedScreen(judged: Screen.Judged, controller: StudyController) =
    StudyPage(judged.steps, banner = null, onStop = controller::stopSession) {
        Label("Модель нашла ошибку", color = Neon.Danger)
        Muted("Ваш вариант")
        Text(diffText(judged.diff, shown = DiffKind.Removed), style = MaterialTheme.typography.bodyLarge)
        Muted("Исправлено")
        Text(diffText(judged.diff, shown = DiffKind.Added), style = MaterialTheme.typography.bodyLarge)
        ListenButton(judged.diff.filter { it.kind != DiffKind.Removed }.joinToString(" ") { it.text }, "▶ СЛУШАТЬ ИСПРАВЛЕННОЕ")
        Note(judged.note)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NeonButton(controller::acceptCorrection, Modifier.weight(1f)) { Text("ПОНЯТНО") }
            NeonOutlinedButton(controller::disputeCorrection, Modifier.weight(1f)) { Text("МОДЕЛЬ ОШИБЛАСЬ") }
        }
    }

/** One side of the correction: unchanged words plus either the removed or the added ones, highlighted. */
@Composable
private fun diffText(parts: List<DiffPart>, shown: DiffKind) = buildAnnotatedString {
    val highlight = if (shown == DiffKind.Removed) {
        SpanStyle(color = Neon.Danger, textDecoration = TextDecoration.LineThrough)
    } else {
        SpanStyle(color = Neon.Cyan, fontWeight = FontWeight.Bold)
    }
    parts.filter { it.kind == DiffKind.Same || it.kind == shown }.forEachIndexed { index, part ->
        if (index > 0) append(' ')
        if (part.kind == DiffKind.Same) append(part.text) else withStyle(highlight) { append(part.text) }
    }
}

@Composable
private fun SelfCheckScreen(check: Screen.SelfCheck, controller: StudyController) =
    StudyPage(check.steps, banner = null, onStop = controller::stopSession) {
        Label("Сравните с образцом", color = Neon.Magenta)
        check.response?.let {
            Muted("Ваш ответ")
            Text(it, style = MaterialTheme.typography.bodyLarge)
        }
        Muted("Образец")
        val references = (check.exercise as? Exercise.Free)?.references ?: listOf(check.exercise.answer)
        Panel(Modifier.fillMaxWidth()) {
            references.forEach { Text(it, style = MaterialTheme.typography.bodyLarge, color = Neon.Cyan) }
        }
        ListenButton(check.exercise.listen, "▶ СЛУШАТЬ ОБРАЗЕЦ")
        Muted("Смысл и грамматика верны — значит верно, даже если слова другие.")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NeonButton({ controller.judgeSelf(true) }, Modifier.weight(1f)) { Text("ВЕРНО") }
            NeonOutlinedButton({ controller.judgeSelf(false) }, Modifier.weight(1f)) { Text("ЕСТЬ ОШИБКА") }
        }
    }

@Composable
private fun RevealScreen(reveal: Screen.Reveal, controller: StudyController) =
    StudyPage(reveal.steps, banner = null, onStop = controller::stopSession) {
        Label("Правильный ответ", color = Neon.Danger)
        SpeakOnShow(reveal, reveal.exercise.listen)
        Text(reveal.exercise.answer, style = MaterialTheme.typography.headlineSmall, color = Neon.Cyan)
        ListenButton(reveal.exercise.listen, if (reveal.exercise.listen == reveal.exercise.answer) "▶ СЛУШАТЬ" else "▶ ${reveal.exercise.listen}")
        Note(reveal.note)
        Muted("Вернёмся к этому в конце занятия.")
        NeonButton(controller::continueAfterReveal, Modifier.fillMaxWidth()) { Text("ДАЛЬШЕ") }
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
    Label("Отдых", color = Neon.Magenta)
    Text(title, style = MaterialTheme.typography.headlineSmall)
    Text(text, style = MaterialTheme.typography.bodyLarge)
    val total = rest.minutes * 60
    val left = ((rest.untilMs - now).coerceAtLeast(0) / 1_000).toInt()
    Column(Modifier.align(Alignment.CenterHorizontally), horizontalAlignment = Alignment.CenterHorizontally) {
        NeonNumber("${left / 60}:${(left % 60).toString().padStart(2, '0')}", style = MaterialTheme.typography.displayMedium)
    }
    NeonBar(1f - left.toFloat() / total, height = 6.dp)
    NeonButton(onFinish, Modifier.fillMaxWidth()) { Text(if (left > 0) "ПРОДОЛЖИТЬ РАНЬШЕ" else "ПРОДОЛЖИТЬ") }
}

@Composable
private fun DoneScreen(done: Screen.Done, onHome: () -> Unit) = Page {
    Label(if (done.endedEarly) "Стоп" else "Готово", color = Neon.Acid)
    Text(if (done.endedEarly) "На сегодня хватит" else "Занятие окончено", style = MaterialTheme.typography.headlineMedium)
    if (done.endedEarly) Muted("Усталость не прошла после перерыва. Остальное перенесено, прогресс сохранён.")
    Panel(Modifier.fillMaxWidth(), accent = Neon.Acid.copy(alpha = 0.45f)) {
        Label("С первого раза")
        NeonNumber("${done.rightFirstTime}/${done.answered}", color = Neon.Acid)
        NeonBar(if (done.answered == 0) 0f else done.rightFirstTime.toFloat() / done.answered, segments = done.answered.coerceIn(1, 24), height = 8.dp)
    }
    NeonButton(onHome, Modifier.fillMaxWidth()) { Text("НА ГЛАВНУЮ") }
}

/** Session screens: the step counter and a neon bar of how far the session has got. */
@Composable
private fun StudyPage(steps: Steps, banner: Banner?, onStop: () -> Unit, content: @Composable ColumnScope.() -> Unit) = Page {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Label("Шаг ${steps.done + 1}/${steps.done + steps.remaining}", color = Neon.Cyan, modifier = Modifier.weight(1f))
        TextButton(onStop) { Text("ЗАКОНЧИТЬ") }
    }
    NeonBar(steps.fraction, height = 6.dp)
    when (banner) {
        Banner.Correct -> Label("✓ Верно", color = Neon.Acid)
        Banner.CheckedByAi -> Label("✓ Верно — проверила модель на телефоне", color = Neon.Acid)
        Banner.CorrectWithHint -> Label("Верно с подсказкой — вернёмся к этому в конце", color = Neon.Acid)
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

/** Plays English aloud; nothing is drawn where the platform has no speech. */
@Composable
private fun ListenButton(text: String, label: String = "▶") {
    val speaker = LocalSpeaker.current ?: return
    TextButton({ speaker.speak(text) }) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = Neon.Cyan)
    }
}

/** Plays [text] once when a screen with this [key] appears. */
@Composable
private fun SpeakOnShow(key: Any, text: String) {
    val speaker = LocalSpeaker.current
    LaunchedEffect(key) { speaker?.speak(text) }
}

@Composable
private fun Note(text: String) {
    Panel(Modifier.fillMaxWidth(), accent = Neon.Magenta.copy(alpha = 0.55f)) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
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
