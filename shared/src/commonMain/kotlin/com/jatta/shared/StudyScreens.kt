package com.jatta.shared

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.jatta.study.CalibrationSummary
import com.jatta.study.CourseProgress
import com.jatta.study.FitStatus
import com.jatta.study.Look
import com.jatta.study.RestKind
import com.jatta.study.Screen
import com.jatta.study.StudyController
import com.jatta.study.ThemeChoice
import com.jatta.study.TopicState
import com.jatta.study.TopicStatus
import com.jatta.study.calibration.CalibrationReport
import com.jatta.study.calibration.CalibrationVerdict
import com.jatta.study.content.AudioFile
import com.jatta.study.content.KindFile
import com.jatta.study.exercise.Exercise
import com.jatta.study.judge.DiffKind
import com.jatta.study.judge.DiffPart
import com.jatta.study.judge.ModelState
import com.jatta.study.reading.Reading
import com.jatta.study.store.AiCheck
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
internal fun ScreenContent(screen: Screen, controller: StudyController, nowMs: () -> Long, onReload: () -> Unit) {
    when (val s = screen) {
        is Screen.Home -> HomeScreen(s, controller, onReload)
        is Screen.Read -> ReadScreen(s, controller)
        is Screen.OwnText -> OwnTextScreen(controller)
        is Screen.TextStudy -> TextStudyScreen(s, controller)
        is Screen.DailySummary -> DailySummaryScreen(s, controller::closeSummary)
        is Screen.Progress -> ProgressScreen(s.report, controller::openHome)
        is Screen.Topic -> TopicScreen(s, controller)
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
        is Screen.ChatTopics -> ChatTopicsScreen(s, controller)
        is Screen.Chat -> ChatScreen(s, controller)
    }
}

@Composable
private fun HomeScreen(home: Screen.Home, controller: StudyController, onReload: () -> Unit) = Page {
    val theme by controller.theme.collectAsState()
    val look by controller.look.collectAsState()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Label("Jatta // заучивай", color = Neon.Cyan, modifier = Modifier.weight(1f))
        NeonTextButton(controller::cycleLook) {
            RollingText(if (look == Look.Neon) "НЕОН" else "СПОКОЙНО", MaterialTheme.typography.labelMedium, Neon.Muted)
        }
        NeonTextButton(controller::cycleTheme) {
            val name = when (theme) {
                ThemeChoice.System -> "система"
                ThemeChoice.Light -> "светлая"
                ThemeChoice.Dark -> "тёмная"
            }
            Label("Тема: ")
            RollingText(name.uppercase(), MaterialTheme.typography.labelMedium, Neon.Muted)
        }
    }
    Text(home.courseTitle, style = MaterialTheme.typography.headlineMedium)
    StreakChip(home.streak, controller::openProgress)
    home.vocabulary?.let { Muted("Словарь при старте: ≈ ${it.words} из ${it.total} самых частых слов (${levelOf(it.words)})") }
    ProgressPanel(home.progress)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Stat("Повторить", home.reviews, Neon.Acid, Modifier.weight(1f).appear(1))
        Stat("Новое", home.newItems, Neon.Magenta, Modifier.weight(1f).appear(2))
    }
    if (home.postponed > 0) {
        Muted("Ещё ${home.postponed} повт. перенесено на следующие дни, чтобы не перегружать сегодняшний.")
    }
    val nothingToDo = home.reviews + home.newItems == 0
    val call = if (nothingToDo) Modifier else Modifier.pulse(scaleBy = 0.025f)
    NeonButton(controller::startSession, Modifier.fillMaxWidth().appear(3).then(call), enabled = !nothingToDo) {
        Text(if (nothingToDo) "НА СЕГОДНЯ ВСЁ" else "НАЧАТЬ ЗАНЯТИЕ")
    }
    if (nothingToDo) Muted("Новые повторы появятся, когда память начнёт ослабевать.")
    home.notice?.let { Label(it, color = Neon.Acid, modifier = Modifier.pop(origin = LeftCenter)) }
    if (home.placementOffered) PlacementCard(controller::openPlacement, controller::skipPlacement)
    home.reading?.let { ReadingCard(it, controller::openReading) }
    val studied = home.progress.met > 0
    if (studied && home.ai.model != ModelState.Unavailable) ChatCard(home.chatReady, home.chatReviewTrusted, controller::openChat)
    if (studied) OwnTextCard(controller::openOwnText)
    Label("Темы · в памяти / всего", modifier = Modifier.padding(top = 8.dp))
    home.topics.forEachIndexed { index, topic -> TopicRow(topic, index) { controller.openTopic(topic.id) } }
    Muted("Любую тему можно пройти отдельно: нажмите на неё. Закрытые темы сами откроются, когда темы до них будут держаться в памяти.")
    NeonTextButton(controller::openProgress) { Label("Мой прогресс →", color = Neon.Cyan) }
    NeonTextButton(controller::openCalibration) { Label(calibrationLine(home.calibration), color = Neon.Cyan) }
    if (studied) AiSection(home.ai, controller)
    DataSection(controller, onReload)
}

@Composable
private fun OwnTextCard(onOpen: () -> Unit) {
    Panel(Modifier.fillMaxWidth().appear(6), accent = Neon.Magenta.copy(alpha = 0.5f)) {
        Label("Свой текст", color = Neon.Magenta)
        Text("Учить слова из своей статьи или письма", style = MaterialTheme.typography.titleMedium)
        Muted("Вставьте любой английский текст: приложение найдёт в нём слова курса и соберёт из них занятие.")
        NeonOutlinedButton(onOpen) { Text("ВСТАВИТЬ ТЕКСТ") }
    }
}

@Composable
private fun DataSection(controller: StudyController, onReload: () -> Unit) {
    val files = LocalFiles.current ?: return
    val scope = rememberCoroutineScope()
    Label("Данные", modifier = Modifier.padding(top = 8.dp))
    Muted("Прогресс и журнал ответов — файл на вашем телефоне. Его можно сохранить и перенести на другой телефон.")
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NeonOutlinedButton({
            scope.launch {
                if (files.save("jatta-progress.json", controller.exportProgress())) controller.showNotice("Прогресс сохранён в файл.")
            }
        }) { Text("СОХРАНИТЬ") }
        NeonOutlinedButton({ scope.launch { files.open()?.let(controller::importProgress) } }) { Text("ЗАГРУЗИТЬ") }
    }
    Muted("Свою тему (JSON по формату из docs/course-format.md) можно добавить в конец курса.")
    NeonOutlinedButton({
        scope.launch {
            val text = files.open() ?: return@launch
            val problem = controller.addTopic(text)
            if (problem == null) onReload() else controller.showNotice("Тема не добавлена: " + problem.lineSequence().first())
        }
    }) { Text("ТЕМА ИЗ ФАЙЛА") }
}

private fun calibrationLine(summary: CalibrationSummary?) = when {
    summary == null -> "Точность прогноза памяти →"
    summary.verdict == CalibrationVerdict.TooFewReviews -> "Точность прогноза памяти: пока ${summary.reviews} повт. →"
    else -> "Прогноз памяти: обещала ${percent(summary.predicted)}, вы вспомнили ${percent(summary.actual)} →"
}

@Composable
private fun PlacementCard(onStart: () -> Unit, onSkip: () -> Unit) {
    Panel(Modifier.fillMaxWidth().appear(4), accent = Neon.Acid.copy(alpha = 0.5f)) {
        Label("Уже знаете английский?", color = Neon.Acid)
        Muted("Тест уровня за 5 минут отметит знакомые слова и грамматику: они не будут учиться заново, а придут на проверку через пару дней.")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NeonOutlinedButton(onStart) { Text("ПРОЙТИ ТЕСТ") }
            NeonTextButton(onSkip) { Label("Начну с начала") }
        }
    }
}

@Composable
private fun PlacementIntroScreen(intro: Screen.PlacementIntro, controller: StudyController) = Page {
    BackBar("Уровень", Neon.Cyan, controller::openHome)
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
private fun PlacementWordScreen(probe: Screen.PlacementWord, controller: StudyController) = Page {
    Label("Слова // знаете, что значит?", color = Neon.Magenta)
    Text(
        probe.word,
        Modifier.fillMaxWidth().padding(vertical = 24.dp).pop(),
        style = MaterialTheme.typography.headlineLarge,
        color = Neon.Cyan,
        textAlign = TextAlign.Center,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NeonButton({ controller.answerPlacementWord(true) }, Modifier.weight(1f).appear(1)) { Text("ЗНАЮ") }
        NeonOutlinedButton({ controller.answerPlacementWord(false) }, Modifier.weight(1f).appear(2)) { Text("НЕ ЗНАЮ") }
    }
    Muted("«Знаю» — можете сказать, что оно значит. Сомневаетесь — «не знаю».")
}

@Composable
private fun PlacementGrammarScreen(probe: Screen.PlacementGrammar, controller: StudyController) = Page {
    Label("Грамматика // " + probe.exercise.instruction, color = Neon.Magenta)
    Text(probe.exercise.question, style = MaterialTheme.typography.headlineSmall)
    probe.exercise.translation?.let { Muted(it) }
    probe.exercise.options.forEachIndexed { index, option ->
        NeonOutlinedButton({ controller.answerPlacementGrammar(index) }, Modifier.fillMaxWidth().appear(index + 1)) { Text(option) }
    }
}

@Composable
private fun PlacementDoneScreen(done: Screen.PlacementDone, controller: StudyController) = Page {
    val result = done.result
    Label("Результат", color = Neon.Acid)
    Panel(Modifier.fillMaxWidth().appear(1), accent = Neon.Cyan.copy(alpha = 0.5f)) {
        Label("Словарь")
        NeonNumber("≈ ${countUp(result.estimatedWords, durationMs = 1400)}")
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

internal fun plural(n: Int, one: String, few: String, many: String) = when {
    n % 10 == 1 && n % 100 != 11 -> one
    n % 10 in 2..4 && n % 100 !in 12..14 -> few
    else -> many
}

private fun levelOf(words: Int) = when {
    words < 1500 -> "около A1"
    words < 2750 -> "около A2"
    else -> "B1 или выше"
}

@Composable
private fun ProgressPanel(progress: CourseProgress) {
    val fraction = if (progress.total == 0) 0f else progress.held.toFloat() / progress.total
    Panel(Modifier.fillMaxWidth().appear(), accent = Neon.Cyan.copy(alpha = 0.5f)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Label("Память")
                NeonNumber("${countUp(progress.held)}/${progress.total}")
            }
            Text(
                "${countUp((fraction * 100).roundToInt())}%",
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
        NeonNumber(countUp(value).toString(), color = color, style = MaterialTheme.typography.headlineLarge.copy(fontFamily = MaterialTheme.typography.displaySmall.fontFamily))
        Label(label)
    }
}

@Composable
private fun TopicRow(topic: TopicStatus, index: Int, onOpen: () -> Unit) {
    val fraction = if (topic.total == 0) 0f else topic.held.toFloat() / topic.total
    val (status, color) = when (topic.state) {
        TopicState.Locked -> "закрыта" to Neon.Muted
        TopicState.Open -> "${topic.held}/${topic.total}" to Neon.Cyan
        TopicState.Mastered -> "✓ ${topic.held}/${topic.total}" to Neon.Acid
    }.let { (text, color) -> (if (topic.custom) "своя · $text" else text) to color }
    Column(Modifier.fillMaxWidth().appear(index).pressable(onOpen), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(topic.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Label(status, color = color)
        }
        NeonBar(fraction, segments = topic.total.coerceIn(1, 24), height = 5.dp, dim = topic.state == TopicState.Locked)
    }
}

@Composable
private fun ReadingCard(reading: Reading, onOpen: () -> Unit) {
    Panel(Modifier.fillMaxWidth().appear(4), accent = Neon.Magenta.copy(alpha = 0.5f)) {
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
private fun ChatCard(ready: Boolean, trusted: Boolean, onOpen: () -> Unit) {
    Panel(Modifier.fillMaxWidth().appear(5), accent = Neon.Cyan.copy(alpha = 0.5f)) {
        Label("Разговор с ИИ", color = Neon.Cyan)
        Text("Поболтать по-английски", style = MaterialTheme.typography.titleMedium)
        when {
            ready && trusted -> Muted("Модель на телефоне ведёт беседу и сразу разбирает ошибки в каждой вашей фразе.")
            ready -> Muted("Модель на телефоне ведёт беседу и разбирает ошибки. Пока она не прошла проверку точности, разбор приблизительный.")
            else -> Muted("Откроется, когда модель на телефоне будет готова.")
        }
        if (ready) NeonOutlinedButton(onOpen) { Text("ПОГОВОРИТЬ") }
    }
}

@Composable
private fun ReadScreen(read: Screen.Read, controller: StudyController) = Page {
    val reading = read.reading
    BackBar("Чтение", Neon.Magenta, controller::openHome)
    Text(reading.text.title, style = MaterialTheme.typography.headlineSmall)
    Panel(Modifier.fillMaxWidth().appear(1)) {
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

@Composable
internal fun highlighted(text: String, words: Set<String>) = buildAnnotatedString {
    val style = SpanStyle(color = Neon.Cyan, fontWeight = FontWeight.Bold)
    var at = 0
    Regex("[A-Za-z]+(?:'[A-Za-z]+)?").findAll(text).forEach { match ->
        append(text.substring(at, match.range.first))
        if (match.value.lowercase() in words) withStyle(style) { append(match.value) } else append(match.value)
        at = match.range.last + 1
    }
    append(text.substring(at))
}

private fun megabytes(bytes: Long) = "${bytes / 1_000_000} МБ"

private enum class AiPhase { Unavailable, Downloadable, Downloading, Testing, Untested, Tested }

private fun AiStatus.phase() = when {
    model == ModelState.Unavailable -> AiPhase.Unavailable
    model == ModelState.Downloadable -> AiPhase.Downloadable
    model == ModelState.Downloading -> AiPhase.Downloading
    testedDone != null -> AiPhase.Testing
    lastTest == null -> AiPhase.Untested
    else -> AiPhase.Tested
}

@Composable
private fun AiSection(ai: AiStatus, controller: StudyController) {
    Label("Проверка свободных ответов", modifier = Modifier.padding(top = 8.dp))
    AnimatedContent(
        ai,
        contentKey = { it.phase() },
        transitionSpec = { (fadeIn() + slideInVertically { it / 4 }) togetherWith fadeOut() },
    ) { status ->
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { AiBody(status, controller) }
    }
}

@Composable
private fun AiBody(ai: AiStatus, controller: StudyController) {
    when (ai.phase()) {
        AiPhase.Unavailable ->
            Muted("На этом устройстве модель для проверки не работает: ей не хватает памяти или она здесь не запускается. Свободные ответы вы сверяете с образцом сами.")
        AiPhase.Downloadable -> DownloadOffer(ai, controller::downloadModel)
        AiPhase.Downloading -> DownloadProgress(ai)
        AiPhase.Testing -> {
            val done = ai.testedDone ?: 0
            Muted("Проверяю точность модели: $done из ${ai.testTotal}")
            NeonBar(done.toFloat() / ai.testTotal, height = 4.dp)
        }
        AiPhase.Untested -> {
            Muted("${ai.modelName} готова. Прежде чем доверить ей ответы, проверим её на ${ai.testTotal} фразах с известным ответом.")
            NeonOutlinedButton(controller::testModel) { Text("ПРОВЕРИТЬ ТОЧНОСТЬ") }
        }
        AiPhase.Tested -> ai.lastTest?.let { TestVerdict(it, controller::testModel) }
    }
}

@Composable
private fun DownloadOffer(ai: AiStatus, onDownload: () -> Unit) {
    val size = ai.downloadTotalBytes?.let { " (${megabytes(it)}, лучше по Wi-Fi)" }.orEmpty()
    Muted("Можно скачать ${ai.modelName}$size — модель Google, которая проверяет ответы на телефоне без интернета.")
    if (ai.downloadFailed) {
        Label("Не скачалось: проверьте интернет и свободное место. Продолжит с того же места.", color = Neon.Danger, modifier = Modifier.shake())
    }
    NeonOutlinedButton(onDownload) { Text(if (ai.downloadFailed) "СКАЧАТЬ ДАЛЬШЕ" else "СКАЧАТЬ МОДЕЛЬ") }
}

@Composable
private fun DownloadProgress(ai: AiStatus) {
    val done = ai.downloadedBytes
    val total = ai.downloadTotalBytes
    Muted("${ai.modelName} скачивается" + (done?.let { ": ${megabytes(it)}" + (total?.let { " из ${megabytes(it)}" }.orEmpty()) } ?: "…"))
    if (done != null && total != null) NeonBar(done.toFloat() / total, height = 4.dp)
}

@Composable
private fun TestVerdict(test: AiCheck, onRetest: () -> Unit) {
    Muted(
        "Совпала с эталоном: ${countUp((test.agreement * 100).roundToInt())}% из ${test.total}. " +
            "Пропустила ошибок: ${test.wrongAccepted}, придралась к верным: ${test.rightRejected}, " +
            "ответила не по формату: ${test.unusable}.",
    )
    Label(
        if (test.enabled) "Модель проверяет свободные ответы" else "Ниже порога 90% — сверяете сами",
        color = if (test.enabled) Neon.Cyan else Neon.Magenta,
        modifier = Modifier.pop(origin = LeftCenter),
    )
    test.translationAgreement?.let { agreement ->
        Muted("Переводы фраз: совпала ${(agreement * 100).roundToInt()}%.")
        Label(
            if (test.translationsEnabled) "Модель проверяет и переводы" else "Переводы ниже порога — сверяете сами",
            color = if (test.translationsEnabled) Neon.Cyan else Neon.Magenta,
        )
    }
    NeonOutlinedButton(onRetest) { Text("ПРОВЕРИТЬ ЕЩЁ РАЗ") }
}

@Composable
private fun PresentScreen(present: Screen.Present, controller: StudyController) =
    StudyPage(present.banner) {
        Label("Новое · ${present.topicTitle}", color = Neon.Magenta)
        present.topicIntro?.let { Note(it) }
        val item = present.item
        val isWord = item.kind != KindFile.Rule
        val audio = item.audio
        if (isWord) SpeakOnShow(item.id, item.en, audio?.file)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(item.en, Modifier.weight(1f).pop(origin = LeftCenter), style = MaterialTheme.typography.headlineMedium, color = Neon.Cyan)
            if (isWord) ListenButton(item.en, "▶ СЛУШАТЬ", audio?.file)
        }
        Text(item.ru, Modifier.appear(1), style = MaterialTheme.typography.titleMedium)
        if (item.note.isNotBlank()) Muted(item.note)
        if (audio != null) {
            Note(SHADOWING)
            Credit(audio)
        } else {
            Panel(Modifier.fillMaxWidth().appear(2)) {
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
    StudyPage(question.banner) {
        Label(rungLabel(question.exercise.rung) + " // " + question.exercise.instruction, color = Neon.Magenta)
        question.hint?.let { hint ->
            val lead = if (question.confidentError) "Ответ быстрый, но неверный — запомните внимательно." else "Не совсем."
            Note("$lead Подсказка: $hint", Modifier.shake(question.id, strength = if (question.confidentError) 1.8f else 1f))
        }
        val exercise = question.exercise
        if (exercise.byEar) {
            SpeakOnShow(question.id, exercise.listen, exercise.recording)
            ListenButton(exercise.listen, "▶ ПОСЛУШАТЬ ЕЩЁ", exercise.recording)
        }
        when (exercise) {
            is Exercise.Choice -> {
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
                    NeonOutlinedButton({ controller.choose(index) }, Modifier.fillMaxWidth().appear(index + 1)) { Text(option) }
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
                SpeakAnswer(question.id, controller::submitSpoken, controller::showSpokenAnswer)
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
    Page {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(color = Neon.Magenta)
            Text("Модель на телефоне проверяет ответ…", Modifier.pulse(fadeBy = 0.5f), style = MaterialTheme.typography.bodyLarge)
        }
    }

@Composable
private fun JudgedScreen(judged: Screen.Judged, controller: StudyController) =
    Page {
        Label("Модель нашла ошибку", color = Neon.Danger, modifier = Modifier.shake())
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
    Page {
        Label("Сравните с образцом", color = Neon.Magenta)
        val heard = check.heard
        if (heard != null) HeardComparison(heard) else WrittenComparison(check)
        ListenButton(check.exercise.listen, "▶ СЛУШАТЬ ОБРАЗЕЦ", check.exercise.recording)
        if (heard != null) Muted("Телефон мог расслышать неточно: судите по тому, что сказали.")
        Muted("Смысл и грамматика верны — значит верно, даже если слова другие.")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NeonButton({ controller.judgeSelf(true) }, Modifier.weight(1f)) { Text("ВЕРНО") }
            NeonOutlinedButton({ controller.judgeSelf(false) }, Modifier.weight(1f)) { Text("ЕСТЬ ОШИБКА") }
        }
    }

@Composable
private fun WrittenComparison(check: Screen.SelfCheck) {
    check.response?.let {
        Muted("Ваш ответ")
        Text(it, style = MaterialTheme.typography.bodyLarge)
    }
    Muted("Образец")
    val references = (check.exercise as? Exercise.Free)?.references ?: listOf(check.exercise.answer)
    Panel(Modifier.fillMaxWidth().appear(1)) {
        references.forEach { Text(it, style = MaterialTheme.typography.bodyLarge, color = Neon.Cyan) }
    }
}

@Composable
private fun HeardScreen(heard: Screen.Heard, controller: StudyController) =
    StudyPage(heard.banner) {
        Label("Что звучало", color = Neon.Acid)
        Text(heard.exercise.answer, Modifier.pop(origin = LeftCenter), style = MaterialTheme.typography.headlineSmall, color = Neon.Cyan)
        Muted(heard.translation)
        ListenButton(heard.exercise.listen, "▶ ПОСЛУШАТЬ ЕЩЁ", heard.exercise.recording)
        Note(SHADOWING)
        NeonButton(controller::continueAfterHeard, Modifier.fillMaxWidth()) { Text("ДАЛЬШЕ") }
    }

private const val SHADOWING = "Повторите вслух за диктором 2–3 раза: тот же темп, ударения и паузы."

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
    Page {
        Label("Правильный ответ", color = Neon.Danger, modifier = Modifier.shake())
        SpeakOnShow(reveal, reveal.exercise.listen, reveal.exercise.recording)
        Text(reveal.exercise.answer, Modifier.appear(1), style = MaterialTheme.typography.headlineSmall, color = Neon.Cyan)
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
        val time = "${left / 60}:${(left % 60).toString().padStart(2, '0')}"
        RollingText(time, glowing(MaterialTheme.typography.displayMedium, Neon.Cyan), Neon.Cyan)
    }
    NeonBar(1f - left.toFloat() / total, height = 6.dp)
    NeonButton(onFinish, Modifier.fillMaxWidth()) { Text(if (left > 0) "ПРОДОЛЖИТЬ РАНЬШЕ" else "ПРОДОЛЖИТЬ") }
}

@Composable
private fun DoneScreen(done: Screen.Done, onHome: () -> Unit) = Page {
    Label(if (done.endedEarly) "Стоп" else "Готово", color = Neon.Acid, modifier = Modifier.pop(origin = LeftCenter))
    Text(if (done.endedEarly) "На сегодня хватит" else "Занятие окончено", style = MaterialTheme.typography.headlineMedium)
    if (done.endedEarly) Muted("Усталость не прошла после перерыва. Остальное перенесено, прогресс сохранён.")
    Panel(Modifier.fillMaxWidth().appear(1), accent = Neon.Acid.copy(alpha = 0.45f)) {
        Label("С первого раза")
        NeonNumber("${countUp(done.rightFirstTime)}/${done.answered}", color = Neon.Acid)
        NeonBar(if (done.answered == 0) 0f else done.rightFirstTime.toFloat() / done.answered, segments = done.answered.coerceIn(1, 24), height = 8.dp)
    }
    if (done.translated > 0) {
        Panel(Modifier.fillMaxWidth().appear(2), accent = Neon.Cyan.copy(alpha = 0.45f)) {
            Label("Перевод фраз")
            NeonNumber("${countUp(done.translatedRight)}/${done.translated}", color = Neon.Cyan)
        }
    }
    if (done.streakGrew) {
        Label("Серия продлена", color = Neon.Magenta, modifier = Modifier.align(Alignment.CenterHorizontally))
        StreakBurst(done.streak, grew = true, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
    NeonButton(onHome, Modifier.fillMaxWidth()) { Text("НА ГЛАВНУЮ") }
}

@Composable
private fun CalibrationScreen(screen: Screen.Calibration, onHome: () -> Unit) = Page {
    val report = screen.report
    BackBar("Точность прогноза", Neon.Cyan, onHome)
    Text(headlineOf(report), style = MaterialTheme.typography.headlineMedium)
    Text(plainVerdict(report), style = MaterialTheme.typography.bodyLarge)
    if (report.reviews > 0) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Panel(Modifier.weight(1f).appear(1), accent = Neon.Cyan.copy(alpha = 0.45f)) {
                NeonNumber(countPercent(report.predicted), color = Neon.Cyan)
                Label("Обещала модель")
            }
            Panel(Modifier.weight(1f).appear(2), accent = Neon.Acid.copy(alpha = 0.45f)) {
                NeonNumber(countPercent(report.actual), color = Neon.Acid)
                Label("Вы вспомнили")
            }
        }
        val reviews = plural(report.reviews, "повтору", "повторам", "повторам")
        Muted("По ${report.reviews} $reviews. С учётом случайности факт лежит между ${percent(report.low)} и ${percent(report.high)}.")
    }
    if (report.bins.size > 1) {
        Label("По уровню прогноза · вспомнили · повторов", modifier = Modifier.padding(top = 8.dp))
        report.bins.forEachIndexed { index, bin ->
            Column(Modifier.fillMaxWidth().appear(index + 3), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
}

@Composable
private fun FitPanel(fit: FitStatus) {
    Panel(Modifier.fillMaxWidth().appear(4), accent = Neon.Magenta.copy(alpha = 0.5f)) {
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

private fun headlineOf(report: CalibrationReport) = when (report.verdict) {
    CalibrationVerdict.TooFewReviews -> "Пока рано судить"
    CalibrationVerdict.Accurate -> "Модель знает вашу память"
    CalibrationVerdict.Overestimates -> "Модель переоценивает вашу память"
    CalibrationVerdict.Underestimates -> "Вы помните лучше, чем думает модель"
}

private fun plainVerdict(report: CalibrationReport): String {
    val promised = percent(report.predicted)
    val recalled = percent(report.actual)
    return when (report.verdict) {
        CalibrationVerdict.TooFewReviews ->
            "Модель проверяется на плановых повторах: прошло ${report.reviews} из ${report.minReviews}, нужных для вывода. Каждое занятие добавляет."
        CalibrationVerdict.Accurate ->
            "Она обещала, что вы вспомните $promised повторов, и вы вспомнили $recalled. Разница в пределах случайности: интервалы между повторами подобраны верно."
        CalibrationVerdict.Overestimates ->
            "Она обещала $promised, а вы вспомнили $recalled: интервалы для вас длинноваты. Подгонка параметров под ваш журнал это исправит."
        CalibrationVerdict.Underestimates ->
            "Она обещала $promised, а вы вспомнили $recalled: повторы можно ставить реже, и подгонка параметров это учтёт."
    }
}

@Composable
private fun countPercent(share: Double) = "${countUp((share * 100).roundToInt())}%"

private fun decimals(value: Double): String {
    val thousandths = (value * 1000).roundToInt()
    return "${thousandths / 1000}.${(thousandths % 1000).toString().padStart(3, '0')}"
}

@Composable
internal fun BackBar(section: String, color: Color, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        NeonTextButton(onBack) { Label("← Назад", color = Neon.Cyan) }
        Spacer(Modifier.weight(1f))
        Label(section, color = color)
    }
}

@Composable
private fun StudyPage(banner: Banner?, content: @Composable ColumnScope.() -> Unit) = Page {
    BannerLabel(banner)
    content()
}

@Composable
private fun BannerLabel(banner: Banner?) {
    val text = when (banner) {
        Banner.Correct -> "✓ Верно"
        Banner.CheckedByAi -> "✓ Верно — проверила модель на телефоне"
        Banner.CorrectWithHint -> "Верно с подсказкой — вернёмся к этому в конце"
        null -> return
    }
    Label(text, color = Neon.Acid, modifier = Modifier.pop(origin = LeftCenter))
}

@Composable
internal fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.widthIn(max = 640.dp).align(Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            content()
        }
    }
}

@Composable
internal fun ListenButton(text: String, label: String = "▶", recording: String? = null) {
    val speaker = LocalSpeaker.current ?: return
    val scope = rememberCoroutineScope()
    NeonTextButton({ scope.launch { speaker.say(text, recording) } }) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = Neon.Cyan)
    }
}

@Composable
private fun SpeakOnShow(key: Any, text: String, recording: String? = null) {
    val speaker = LocalSpeaker.current
    LaunchedEffect(key) { speaker?.say(text, recording) }
}

@Composable
internal fun Note(text: String, modifier: Modifier = Modifier) {
    Panel(modifier.fillMaxWidth(), accent = Neon.Magenta.copy(alpha = 0.55f)) {
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
