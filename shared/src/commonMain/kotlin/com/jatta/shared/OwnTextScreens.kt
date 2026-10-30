package com.jatta.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jatta.study.Screen
import com.jatta.study.StudyController
import kotlin.math.roundToInt

@Composable
internal fun OwnTextScreen(controller: StudyController) = Page {
    BackBar("Свой текст", Neon.Magenta, controller::openHome)
    Text("Вставьте английский текст", style = MaterialTheme.typography.headlineSmall)
    Muted("Статья, письмо, субтитры — что угодно. Приложение найдёт в нём слова курса и предложит выучить те, что вы ещё не держите в памяти.")
    var text by remember { mutableStateOf("") }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
        minLines = 6,
        placeholder = { Text("Paste your text here…") },
    )
    NeonButton({ controller.analyzeText(text) }, Modifier.fillMaxWidth(), enabled = text.isNotBlank()) { Text("РАЗОБРАТЬ") }
    Muted("Текст никуда не отправляется и не сохраняется.")
}

@Composable
internal fun TextStudyScreen(screen: Screen.TextStudy, controller: StudyController) = Page {
    val report = screen.report
    BackBar("Свой текст", Neon.Magenta, controller::openOwnText)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Panel(Modifier.weight(1f).appear(1), accent = Neon.Cyan.copy(alpha = 0.45f)) {
            NeonNumber("${(report.knownShare * 100).roundToInt()}%", color = Neon.Cyan)
            Label("Знакомых слов")
        }
        Panel(Modifier.weight(1f).appear(2), accent = Neon.Magenta.copy(alpha = 0.45f)) {
            NeonNumber("${report.metItems.size + report.newItems.size}", color = Neon.Magenta)
            Label("Слов курса")
        }
    }
    val toStudy = report.metItems.size + report.newItems.size
    if (toStudy > 0) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Muted(
                "Повторить из текста: ${report.metItems.size}, новых слов курса: ${report.newItems.size}" +
                    if (report.newItems.size > screen.newToday) " (сегодня в лимит нового войдут ${screen.newToday})" else "",
            )
            NeonButton(controller::studyText, Modifier.fillMaxWidth().pulse(scaleBy = 0.025f)) { Text("УЧИТЬ СЛОВА ТЕКСТА") }
        }
    } else {
        Muted("Все слова курса из этого текста уже держатся в памяти.")
    }
    Panel(Modifier.fillMaxWidth().appear(3)) {
        Text(highlighted(report.text, report.courseWords), style = MaterialTheme.typography.bodyLarge)
    }
    if (report.courseWords.isNotEmpty()) Muted("Выделены слова курса.")
    if (report.otherWords.isNotEmpty()) {
        Muted("Вне курса и первой тысячи частых слов: ${report.otherWords.joinToString()}. Их приложение пока не учит — попробуйте понять по контексту.")
    }
}
