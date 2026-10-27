package com.jatta.shared

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jatta.study.Screen
import com.jatta.study.StudyController

@Composable
internal fun TopicScreen(screen: Screen.Topic, controller: StudyController) = Page {
    val topic = screen.topic
    BackBar("Тема", Neon.Cyan, controller::openHome)
    Text(topic.title, style = MaterialTheme.typography.headlineMedium)
    if (screen.intro.isNotBlank()) Muted(screen.intro)
    Panel(Modifier.fillMaxWidth().appear(1)) {
        Text("Держится в памяти: ${countUp(topic.held)} из ${topic.total}", style = MaterialTheme.typography.bodyLarge)
        if (screen.reviews > 0) {
            val early = if (screen.early > 0) ", из них раньше срока: ${screen.early}" else ""
            Text("Повторить: ${screen.reviews}$early", style = MaterialTheme.typography.bodyLarge)
        }
        if (screen.newToday > 0) Text("Новых сегодня: ${screen.newToday}", style = MaterialTheme.typography.bodyLarge)
        if (screen.newLater > 0) Muted("Ещё ${screen.newLater} новых — в следующие дни: новое идёт порциями, чтобы память успевала.")
    }
    if (screen.missing.isNotEmpty()) {
        Note("Тема ещё закрыта: она опирается на «${screen.missing.joinToString("», «")}». Пройти её можно и сейчас.", Modifier.appear(2))
    }
    if (screen.early > 0) {
        Muted("Повтор раньше срока укрепляет память слабее, чем вовремя, а следующий повтор пересчитается сам и придёт немного позже.")
    }
    if (screen.reviews + screen.newToday > 0) {
        NeonButton(controller::startTopic, Modifier.fillMaxWidth().appear(3).pulse(scaleBy = 0.025f)) { Text("ПРОЙТИ ТЕМУ") }
    } else {
        Muted("Сегодня здесь нечего проходить: новое на сегодня закончилось.")
    }
}
