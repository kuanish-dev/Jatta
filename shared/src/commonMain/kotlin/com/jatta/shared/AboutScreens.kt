package com.jatta.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp

val LocalAppVersion = staticCompositionLocalOf<String?> { null }

private const val REPO = "https://github.com/kuanish-dev/Jatta"

@Composable
internal fun AboutScreen(onBack: () -> Unit) = Page {
    BackBar("О приложении", Neon.Cyan, onBack)
    Text("Jatta", style = MaterialTheme.typography.headlineMedium)
    val version = LocalAppVersion.current
    Muted(if (version == null) "ИИ-учебник английского без сервера" else "Версия $version · ИИ-учебник английского без сервера")
    Text(
        "Jatta (каз. «заучивай») учит английскому от B1 к B2: повторы ставит модель памяти FSRS и подгоняет её под вас, " +
            "свободные ответы и разговор проверяет модель на самом телефоне. Аккаунта, подписки и сервера нет.",
        style = MaterialTheme.typography.bodyLarge,
    )

    Section("Код и лицензия", 1) {
        Line("Свободная программа: GNU GPL версии 3 или новее, с разрешением связывать её с Google ML Kit.")
        Linked("Исходный код и темы курса на GitHub", REPO)
        Linked("Текст лицензии", "$REPO/blob/main/LICENSE")
        Linked("Все сторонние лицензии", "$REPO/blob/main/THIRD_PARTY_NOTICES.md")
    }

    Section("Откуда слова, фразы и тексты", 2) {
        Credit("New General Service List 1.2", "Browne, Culligan и Phillips, CC BY-SA 4.0: ранги слов, слова теста уровня и формы первой тысячи для разбора своего текста.", "https://www.newgeneralservicelist.com/")
        Credit("Tatoeba", "фразы на слух и предложения для перевода с русскими переводами, CC BY 2.0 FR. Записи носителей — CC BY 4.0 или CC BY-SA 4.0, автор указан у каждой фразы.", "https://tatoeba.org")
        Credit("Simple English Wikipedia", "отрывки для чтения, CC BY-SA 4.0; источник показывается у каждого текста.", "https://simple.wikipedia.org")
        Credit("Project Gutenberg", "отрывки из книг, перешедших в общественное достояние.", "https://www.gutenberg.org")
        Line("Грамматика курса сверена с English Grammar Profile (Cambridge, уровни B1–B2).")
    }

    Section("Чем построено", 3) {
        Credit("FSRS-6", "модель памяти; порт py-fsrs 6.3.2 от Open Spaced Repetition, MIT.", "https://github.com/open-spaced-repetition/py-fsrs")
        Credit("Gemma 4 E2B", "модель Google под Apache 2.0, работает на телефоне через LiteRT-LM (Apache 2.0). Скачивается только по вашему запросу.", "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm")
        Line("Gemini Nano через Google ML Kit на поддерживаемых телефонах: проприетарная библиотека, не под GPL; она отправляет Google служебную телеметрию.")
        Line("Kotlin, Compose Multiplatform, AndroidX — Apache 2.0. Шрифты Exo 2 и JetBrains Mono — SIL Open Font License 1.1.")
    }

    Section("Приватность", 4) {
        Line("Ответы, голос и разговор с ИИ не покидают телефон. Интернет нужен один раз — скачать модель. Прогресс и журнал ответов — файлы на вашем устройстве; их можно сохранить и перенести.")
    }
}

@Composable
private fun Section(title: String, index: Int, content: @Composable () -> Unit) {
    Label(title, modifier = Modifier.padding(top = 8.dp))
    Panel(Modifier.fillMaxWidth().appear(index)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun Line(text: String) = Text(text, style = MaterialTheme.typography.bodyMedium)

@Composable
private fun Credit(name: String, text: String, url: String) {
    Text(
        buildAnnotatedString {
            withLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = Neon.Cyan, textDecoration = TextDecoration.Underline)))) {
                append(name)
            }
            append(" — ")
            append(text)
        },
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun Linked(label: String, url: String) {
    Text(
        buildAnnotatedString {
            withLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = Neon.Cyan, textDecoration = TextDecoration.Underline)))) {
                append("$label →")
            }
        },
        style = MaterialTheme.typography.bodyMedium,
    )
}
