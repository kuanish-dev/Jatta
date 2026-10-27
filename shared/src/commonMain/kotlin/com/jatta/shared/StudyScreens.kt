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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.jatta.study.FitStatus
import com.jatta.study.RestKind
import com.jatta.study.Screen
import com.jatta.study.Steps
import com.jatta.study.StudyController
import com.jatta.study.ThemeChoice
import com.jatta.study.TopicState
import com.jatta.study.TopicStatus
import com.jatta.study.calibration.CalibrationVerdict
import com.jatta.study.content.AudioFile
import com.jatta.study.content.KindFile
import com.jatta.study.exercise.Exercise
import com.jatta.study.judge.DiffKind
import com.jatta.study.judge.DiffPart
import com.jatta.study.judge.ModelState
import com.jatta.study.reading.Reading
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
        is Screen.Heard -> HeardScreen(s, controller)
        is Screen.Reveal -> RevealScreen(s, controller)
        is Screen.Translate -> TranslateScreen(s, controller)
        is Screen.Translated -> TranslatedScreen(s, controller)
        is Screen.Rest -> RestScreen(s, nowMs, controller::finishRest)
        is Screen.Done -> DoneScreen(s, controller::openHome)
        is Screen.Calibration -> CalibrationScreen(s, controller::openHome)
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
    TextButton(controller::openCalibration) { Label("Точность прогноза памяти →", color = Neon.Cyan) }
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
            // М25: translations are judged leniently and have their own test.
            test.translationAgreement?.let { agreement ->
                Muted("Переводы фраз: совпала ${(agreement * 100).roundToInt()}%.")
                Label(
                    if (test.translationsEnabled) "Модель проверяет и переводы" else "Переводы ниже порога — сверяете сами",
                    color = if (test.translationsEnabled) Neon.Cyan else Neon.Magenta,
                )
            }
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
        val audio = item.audio
        if (isWord) SpeakOnShow(item.id, item.en, audio?.file)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(item.en, Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium, color = Neon.Cyan)
            if (isWord) ListenButton(item.en, "▶ СЛУШАТЬ", audio?.file)
        }
        Text(item.ru, style = MaterialTheme.typography.titleMedium)
        if (item.note.isNotBlank()) Muted(item.note)
        if (audio != null) {
            Note(SHADOWING)
            Credit(audio)
        } else {
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
        val exercise = question.exercise
        if (exercise.byEar) {
            // М24: the recording is the question itself.
            SpeakOnShow(question.id, exercise.listen, exercise.recording)
            ListenButton(exercise.listen, "▶ ПОСЛУШАТЬ ЕЩЁ", exercise.recording)
        }
        when (exercise) {
            is Exercise.Choice -> {
                // An English word to recognise is played at once; a sentence with a gap would give the answer away.
                val spoken = exercise.question == exercise.listen
                if (spoken) SpeakOnShow(question.id, exercise.listen)
                if (exercise.question.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(exercise.question, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                        if (spoken) ListenButton(exercise.listen)
                    }
                }
                exercise.translation?.let { Muted(it) }
                exercise.options.forEachIndexed { index, option ->
                    NeonOutlinedButton({ controller.choose(index) }, Modifier.fillMaxWidth()) { Text(option) }
                }
            }
            is Exercise.Typed -> {
                if (exercise.question.isNotEmpty()) Text(exercise.question, style = MaterialTheme.typography.headlineSmall)
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
internal fun AnswerField(id: Int, singleLine: Boolean, onSubmit: (String) -> Unit) {
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

/** One side of the correction: unchanged words, as the learner wrote them or as corrected, plus the removed or the added ones highlighted. */
@Composable
internal fun diffText(parts: List<DiffPart>, shown: DiffKind) = buildAnnotatedString {
    val highlight = if (shown == DiffKind.Removed) {
        SpanStyle(color = Neon.Danger, textDecoration = TextDecoration.LineThrough)
    } else {
        SpanStyle(color = Neon.Cyan, fontWeight = FontWeight.Bold)
    }
    parts.filter { it.kind == DiffKind.Same || it.kind == shown }.forEachIndexed { index, part ->
        if (index > 0) append(' ')
        when {
            part.kind != DiffKind.Same -> withStyle(highlight) { append(part.text) }
            shown == DiffKind.Removed -> append(part.written)
            else -> append(part.text)
        }
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
        ListenButton(check.exercise.listen, "▶ СЛУШАТЬ ОБРАЗЕЦ", check.exercise.recording)
        Muted("Смысл и грамматика верны — значит верно, даже если слова другие.")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NeonButton({ controller.judgeSelf(true) }, Modifier.weight(1f)) { Text("ВЕРНО") }
            NeonOutlinedButton({ controller.judgeSelf(false) }, Modifier.weight(1f)) { Text("ЕСТЬ ОШИБКА") }
        }
    }

/** М24: the phrase just heard right, to listen to again and repeat aloud. */
@Composable
private fun HeardScreen(heard: Screen.Heard, controller: StudyController) =
    StudyPage(heard.steps, heard.banner, controller::stopSession) {
        Label("Что звучало", color = Neon.Acid)
        Text(heard.exercise.answer, style = MaterialTheme.typography.headlineSmall, color = Neon.Cyan)
        Muted(heard.translation)
        ListenButton(heard.exercise.listen, "▶ ПОСЛУШАТЬ ЕЩЁ", heard.exercise.recording)
        Note(SHADOWING)
        NeonButton(controller::continueAfterHeard, Modifier.fillMaxWidth()) { Text("ДАЛЬШЕ") }
    }

private const val SHADOWING = "Повторите вслух за диктором 2–3 раза: тот же темп, ударения и паузы."

/** Who recorded a phrase and under which license, as the license asks. */
@Composable
private fun Credit(audio: AudioFile) {
    Text(
        buildAnnotatedString {
            withLink(LinkAnnotation.Url(audio.source, TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline)))) {
                append("Голос: ${audio.by} · ${audio.license} · Tatoeba")
            }
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun RevealScreen(reveal: Screen.Reveal, controller: StudyController) =
    StudyPage(reveal.steps, banner = null, onStop = controller::stopSession) {
        Label("Правильный ответ", color = Neon.Danger)
        SpeakOnShow(reveal, reveal.exercise.listen, reveal.exercise.recording)
        Text(reveal.exercise.answer, style = MaterialTheme.typography.headlineSmall, color = Neon.Cyan)
        ListenButton(
            reveal.exercise.listen,
            if (reveal.exercise.listen == reveal.exercise.answer) "▶ СЛУШАТЬ" else "▶ ${reveal.exercise.listen}",
            reveal.exercise.recording,
        )
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
    if (done.translated > 0) {
        Panel(Modifier.fillMaxWidth(), accent = Neon.Cyan.copy(alpha = 0.45f)) {
            Label("Перевод фраз")
            NeonNumber("${done.translatedRight}/${done.translated}", color = Neon.Cyan)
        }
    }
    NeonButton(onHome, Modifier.fillMaxWidth()) { Text("НА ГЛАВНУЮ") }
}

/** М2: what the memory model promised against what the learner recalled, from the answer log. */
@Composable
private fun CalibrationScreen(screen: Screen.Calibration, onHome: () -> Unit) = Page {
    val report = screen.report
    Label("Точность прогноза", color = Neon.Cyan)
    Text("Прогноз против факта", style = MaterialTheme.typography.headlineMedium)
    if (report.reviews > 0) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Panel(Modifier.weight(1f), accent = Neon.Cyan.copy(alpha = 0.45f)) {
                NeonNumber(percent(report.predicted), color = Neon.Cyan)
                Label("Обещала модель")
            }
            Panel(Modifier.weight(1f), accent = Neon.Acid.copy(alpha = 0.45f)) {
                NeonNumber(percent(report.actual), color = Neon.Acid)
                Label("Вы вспомнили")
            }
        }
        val reviews = plural(report.reviews, "повтору", "повторам", "повторам")
        Muted("По ${report.reviews} $reviews. С учётом случайности факт лежит между ${percent(report.low)} и ${percent(report.high)}.")
    }
    Note(
        when (report.verdict) {
            CalibrationVerdict.TooFewReviews ->
                "Повторов пока ${report.reviews} из ${report.minReviews}: на таком числе разница может быть случайной, вывода нет."
            CalibrationVerdict.Accurate -> "Модель предсказывает вашу память точно: прогноз попадает в погрешность."
            CalibrationVerdict.Overestimates -> "Вы вспоминаете реже, чем обещает модель: для вас интервалы между повторами длинноваты."
            CalibrationVerdict.Underestimates -> "Вы вспоминаете чаще, чем обещает модель: для вас повторы можно ставить реже."
        },
    )
    if (report.bins.size > 1) {
        Label("По уровню прогноза · вспомнили · повторов", modifier = Modifier.padding(top = 8.dp))
        report.bins.forEach { bin ->
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("прогноз ${percent(bin.from)}–${percent(bin.to)}", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Label("${percent(bin.actual)} · ${bin.count}", color = Neon.Acid)
                }
                NeonBar(bin.actual.toFloat(), segments = 20, height = 5.dp)
            }
        }
    }
    Muted(
        "Каждый повтор — проверка прогноза: перед ним модель знает, с какой вероятностью вы вспомните, после — вспомнили ли. " +
            "Отдельные контрольные не нужны. Первые встречи со словом и знакомое по тесту уровня не считаются.",
    )
    FitPanel(screen.fit)
    NeonButton(onHome, Modifier.fillMaxWidth()) { Text("НА ГЛАВНУЮ") }
}

/** М2: whose parameters the memory model runs on, and how the last fit did on reviews it had not seen. */
@Composable
private fun FitPanel(fit: FitStatus) {
    Panel(Modifier.fillMaxWidth(), accent = Neon.Magenta.copy(alpha = 0.5f)) {
        Label("Параметры памяти", color = Neon.Magenta)
        Text(if (fit.personal) "Подогнаны под вас" else "Стандартные FSRS", style = MaterialTheme.typography.titleMedium)
        val last = fit.last
        if (last == null) {
            Muted("Подобраны на большом наборе повторов Anki. Под вашу память их подгонят, когда наберётся ${fit.nextFitAt} повторов: сейчас ${fit.reviews}.")
            NeonBar((fit.reviews.toFloat() / fit.nextFitAt).coerceAtMost(1f), segments = 20, height = 6.dp)
        } else {
            Muted(
                "Последняя подгонка — по ${last.reviews} повторам. Последние ${last.heldOut} из них она не видела, " +
                    "и на них ошибка прогноза: прежние параметры ${decimals(last.currentLoss)}, подогнанные ${decimals(last.fittedLoss)} " +
                    "(log loss, меньше — лучше).",
            )
            Muted(if (last.adopted) "Подогнанные предсказали лучше и приняты." else "Подогнанные не предсказали лучше, поэтому оставлены прежние.")
            Muted("Следующая подгонка — на ${fit.nextFitAt} повторах.")
        }
        if (fit.personal) {
            Muted("Сравнение прогноза и факта выше включает повторы, на которых параметры подгоняли; честная проверка — на отложенных.")
        }
    }
}

private fun percent(share: Double) = "${(share * 100).roundToInt()}%"

/** Three decimals without platform formatting: 0.432. */
private fun decimals(value: Double): String {
    val thousandths = (value * 1000).roundToInt()
    return "${thousandths / 1000}.${(thousandths % 1000).toString().padStart(3, '0')}"
}

/** Session screens: the step counter and a neon bar of how far the session has got. */
@Composable
internal fun StudyPage(
    steps: Steps,
    banner: Banner?,
    onStop: () -> Unit,
    counter: String = "Шаг",
    content: @Composable ColumnScope.() -> Unit,
) = Page {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Label("$counter ${steps.done + 1}/${steps.done + steps.remaining}", color = Neon.Cyan, modifier = Modifier.weight(1f))
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
internal fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.widthIn(max = 640.dp).align(Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            content()
        }
    }
}

/** Plays English aloud, in the real voice of [recording] when there is one; nothing is drawn without a speaker. */
@Composable
internal fun ListenButton(text: String, label: String = "▶", recording: String? = null) {
    val speaker = LocalSpeaker.current ?: return
    val scope = rememberCoroutineScope()
    TextButton({ scope.launch { speaker.say(text, recording) } }) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = Neon.Cyan)
    }
}

/** Plays [text] once when a screen with this [key] appears. */
@Composable
private fun SpeakOnShow(key: Any, text: String, recording: String? = null) {
    val speaker = LocalSpeaker.current
    LaunchedEffect(key) { speaker?.say(text, recording) }
}

@Composable
internal fun Note(text: String) {
    Panel(Modifier.fillMaxWidth(), accent = Neon.Magenta.copy(alpha = 0.55f)) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
internal fun Muted(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun rungLabel(rung: Rung) = when (rung) {
    Rung.Recognize -> "Узнавание"
    Rung.FillGap -> "Пропуск"
    Rung.Translate -> "Перевод"
    Rung.Compose -> "Своя фраза"
    Rung.Speak -> "Вслух"
}
