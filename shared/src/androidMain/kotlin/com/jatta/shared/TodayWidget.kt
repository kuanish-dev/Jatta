package com.jatta.shared

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jatta.engine.Engine
import com.jatta.engine.EngineConfig
import com.jatta.engine.learningDay
import com.jatta.shared.resources.Res
import com.jatta.study.content.ContentLoader
import com.jatta.study.store.LearnerStore
import com.jatta.study.store.toLearner
import java.util.TimeZone

private const val WIDGET_WORK = "jatta-widget"

class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context)

    override fun onEnabled(context: Context) = refresh(context)

    companion object {
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            if (manager.getAppWidgetIds(ComponentName(context, TodayWidget::class.java)).isEmpty()) return
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WIDGET_WORK, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<TodayWidgetWorker>().build())
        }
    }
}

class TodayWidgetWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidget::class.java))
        if (ids.isEmpty()) return Result.success()
        val plan = runCatching { todayPlan(context) }.getOrNull()
        val views = RemoteViews(context.packageName, R.layout.widget_today)
        views.setTextViewText(R.id.widget_numbers, plan?.let { "${it.first} · ${it.second}" } ?: "—")
        views.setTextViewText(
            R.id.widget_hint,
            when {
                plan == null -> "откройте приложение"
                plan.first + plan.second == 0 -> "на сегодня всё"
                else -> "повторить · новое"
            },
        )
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { open ->
            views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE))
        }
        ids.forEach { manager.updateAppWidget(it, views) }
        return Result.success()
    }

    private suspend fun todayPlan(context: Context): Pair<Int, Int> {
        val courseFile = ContentLoader.readCourseFile(Res.readBytes("$CONTENT_DIR/en/course.json").decodeToString())
        val topics = courseFile.topics.associateWith { Res.readBytes("$CONTENT_DIR/en/topics/$it.json").decodeToString() }
        val content = ContentLoader.load(courseFile, topics)
        val saved = LearnerStore(context.filesDir.absolutePath).load()
        val config = EngineConfig()
        val engine = Engine(content.course, saved.fsrsParameters?.let { config.copy(fsrsParameters = it) } ?: config)
        val now = System.currentTimeMillis()
        val today = learningDay(now, TimeZone.getDefault().getOffset(now) / 60_000, config.dayStartHour)
        val plan = engine.planDay(saved.toLearner(), today)
        return plan.reviews.size to plan.newItems.size
    }
}
