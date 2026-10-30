package com.jatta.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jatta.study.Screen
import com.jatta.study.StudyController
import com.jatta.study.content.ItemFile
import com.jatta.study.content.KindFile
import com.jatta.study.dictionary.Dictionary
import com.jatta.study.dictionary.DictionaryEntry
import com.jatta.study.dictionary.ItemState
import com.jatta.study.dictionary.ItemStatus
import kotlin.math.roundToInt

@Composable
internal fun DictionaryScreen(screen: Screen.Dictionary, controller: StudyController) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxSize().align(Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            BackBar("Словарь", Neon.Cyan, controller::openHome)
            OutlinedTextField(
                value = screen.query,
                onValueChange = controller::searchDictionary,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("По-английски или по-русски") },
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Neon.Cyan,
                    unfocusedBorderColor = Neon.Line,
                    cursorColor = Neon.Magenta,
                    focusedContainerColor = Neon.Panel,
                    unfocusedContainerColor = Neon.Panel,
                ),
            )
            val found = remember(screen.entries, screen.query) { Dictionary.search(screen.entries, screen.query) }
            val held = screen.entries.count { it.status.state == ItemState.Held }
            Muted(
                if (screen.query.isBlank()) "Весь курс: ${screen.entries.size} · держится в памяти: $held"
                else "Найдено: ${found.size} из ${screen.entries.size}",
            )
            if (found.isEmpty()) Muted("Такого слова в курсе нет.")
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(found, key = { it.id }) { entry -> EntryRow(entry) { controller.openItem(entry.id) } }
            }
        }
    }
}

@Composable
internal fun EntryRow(entry: DictionaryEntry, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    Row(modifier.fillMaxWidth().pressable(onOpen), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(entry.en, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Muted(entry.ru, maxLines = 1)
        }
        val (text, color) = stateLabel(entry.status.state)
        Label(text, color = color, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun stateLabel(state: ItemState) = when (state) {
    ItemState.New -> "новое" to Neon.Muted
    ItemState.Learning -> "учится" to Neon.Magenta
    ItemState.Held -> "✓ держится" to Neon.Acid
}

@Composable
internal fun ItemCardScreen(screen: Screen.ItemCard, controller: StudyController) = Page {
    val item = screen.item
    val (kind, color) = kindOf(item)
    BackBar(kind, color, controller::closeItem)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(item.en, Modifier.weight(1f).pop(origin = LeftCenter), style = MaterialTheme.typography.headlineSmall)
        ListenButton(item.en, "▶", item.audio?.file)
    }
    if (item.also.isNotEmpty()) Muted("Также: " + item.also.joinToString())
    Text(item.ru, Modifier.appear(1), style = MaterialTheme.typography.titleMedium, color = Neon.Cyan)
    if (item.note.isNotBlank()) {
        Panel(Modifier.fillMaxWidth().appear(2)) { Text(item.note, style = MaterialTheme.typography.bodyMedium) }
    }
    item.rank?.let { Muted("Частотность: №$it среди 2 800 самых частых слов (NGSL)") }
    if (item.examples.isNotEmpty()) {
        Label("Примеры", modifier = Modifier.padding(top = 8.dp))
        item.examples.forEachIndexed { index, example ->
            Column(Modifier.fillMaxWidth().appear(index + 3), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(example.en, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    ListenButton(example.en)
                }
                Muted(example.ru)
            }
        }
    }
    Panel(Modifier.fillMaxWidth().appear(4), accent = statusAccent(screen.status.state)) {
        Label("Память", color = statusAccent(screen.status.state))
        Text(memoryLine(screen.status), style = MaterialTheme.typography.bodyMedium)
    }
    NeonTextButton({ controller.openTopic(screen.topicId) }) { Label("Тема: ${screen.topicTitle} →", color = Neon.Cyan) }
    item.audio?.let { Muted("Запись: ${it.by} · ${it.license}") }
}

@Composable
private fun kindOf(item: ItemFile) = when {
    item.audio != null -> "Фраза на слух" to Neon.Magenta
    item.kind == KindFile.Rule -> "Правило" to Neon.Magenta
    item.kind == KindFile.Phrase -> "Сочетание" to Neon.Cyan
    else -> "Слово" to Neon.Cyan
}

@Composable
private fun statusAccent(state: ItemState) = when (state) {
    ItemState.New -> Neon.Muted
    ItemState.Learning -> Neon.Magenta
    ItemState.Held -> Neon.Acid
}

private fun memoryLine(status: ItemStatus): String {
    val recall = status.recallTomorrow?.let { "вспомнится завтра с вероятностью ${(it * 100).roundToInt()}%" }
    val due = status.dueInDays?.let { days ->
        when {
            days <= 0 -> "повтор — сегодня"
            days == 1 -> "повтор — завтра"
            else -> "повтор через $days дн."
        }
    }
    return when (status.state) {
        ItemState.New -> "Ещё не встречалось: придёт как новое по порядку курса или из темы."
        ItemState.Learning -> "Учится · $recall · $due"
        ItemState.Held -> "Держится в памяти · $recall · $due"
    }
}
