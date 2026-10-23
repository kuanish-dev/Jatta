# Jatta

Jatta (каз. «заучивай») — ИИ-учебник английского на Kotlin Multiplatform (Android + iOS), без сервера.
Научная основа и механики М1–М22: `docs/mechanics.html` (опубликовано: https://claude.ai/artifact/1rr8HKEgen5H7N3BP9XS3j).

Лицензия: GPL-3.0-or-later (`LICENSE`) с дополнительным разрешением по пункту 7 GPL на связывание с Google ML Kit. Текст разрешения и чужой код/данные — в `THIRD_PARTY_NOTICES.md`: при заимствовании кода или данных добавлять туда источник и лицензию. Новую закрытую библиотеку без отдельного разрешения не подключать.

## Модули
- `engine` — чистое ядро: FSRS, план дня, очередь занятия, лестница, отдых. Без часов, базы, сети и платформенных API.
- `study` — всё вокруг движка без UI: формат и загрузка контента, упражнения, проверка ответов, хранение прогресса (`LearnerStore`, JSON + журнал `answers.jsonl`), ход занятия (`StudyController` → `Screen`), проверка свободных ответов моделью (`judge/`).
- `shared` — интерфейс на Compose Multiplatform. Только рисует `Screen` и вызывает `StudyController`.
- `androidApp` — `MainActivity`. iOS: `MainViewController()` в `shared/iosMain`, Xcode-проект появится после установки Xcode.

## Контент
- Источник — `content/en/`: `course.json` (темы по порядку) и `topics/<id>.json`. В приложение он копируется задачей `syncContent` модуля `shared`.
- Слово: `en`, `ru`, `note`, `examples` с `gap`. Правило: `ru` — правило одной строкой, `note` — объяснение, у примеров есть `gap`, `cue` и `wrong` (неверные варианты для выбора).
- `rank` (М8) — ранг слова в NGSL 1.2 (`sources/ngsl/`, CC BY-SA 4.0, в приложение не попадает). Слова нет в NGSL — `rank` не ставим. `ContentTest.wordRanksComeFromNgsl` это проверяет.
- Курс (9 тем): слова — работа, проекты и сроки, общение и встречи; грамматика — Past Simple, Present Perfect, формы будущего, условные 1-го и 2-го типа, пассив.
- Загрузчик отказывается грузить битый контент; `ContentTest` проверяет настоящий курс и строит упражнение на каждой ступени.

## ИИ на устройстве
- Платформа реализует только `OnDeviceModel` (состояние, скачивание, `generate`): Android — `GeminiNanoModel` (ML Kit Prompt API) в `shared/androidMain`; iOS — Apple Foundation Models на Swift, передаётся в `MainViewController(model)` (ещё не написано, нужен Xcode).
- Промпт и разбор ответа общие (`FreeAnswerJudge`): модель говорит только CORRECT/WRONG и даёт исправление с минимальными правками. Ответ не по формату или переписанная фраза (больше 40% слов) отбрасываются → самопроверка. Объяснение берётся из `note` курса, не от модели.
- Сначала тест, потом доверие (М21): модель проверяет ответы на занятиях только после того, как на `content/eval/free-answers.json` совпала с эталоном ≥ 90%. Результат хранится в `SavedState.aiCheck`. В журнале ответов поле `checkedBy`: code / self / ai / ai-disputed.
- Gemini Nano нет в эмуляторе — там видно только состояние «недоступна»; проверять на поддерживаемом телефоне.

## Правила
- Что, когда и сколько учить, решает только код движка. ИИ только проверяет свободные ответы; без модели свободный ответ ученик сверяет с образцом сам.
- Числа механик живут в `EngineConfig`, числа хода занятия — в `StudyConfig`. У каждого числа метка механики и уровень доказательности.
- У каждой механики свой тест. Новая механика: правило в docs, параметр в конфиге, код, тест.
- FSRS — порт py-fsrs 6.3.2. При изменении формул перегенерировать векторы: `scripts/fsrs_vectors.py` (Python 3.10+, `pip install fsrs==6.3.2`).
- Простой код: без DI-фреймворков, данные и функции вместо иерархий классов.

## Команды
```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew :engine:jvmTest :study:jvmTest
./gradlew :androidApp:assembleDebug
./gradlew :shared:compileKotlinIosSimulatorArm64
```
Эмулятор: `$SDK/emulator/emulator -avd droptuner -no-audio` (SDK в `/opt/homebrew/share/android-commandlinetools`), затем `adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk`.

## Окружение
- JDK 17 из Homebrew, системной Java нет. `local.properties` указывает на Android SDK.
- Версии: Kotlin 2.4.20, Gradle 9.7.1, AGP 9.1.1, Compose Multiplatform 1.12.1, compileSdk 37.
- Xcode не установлен: iOS-код компилируется, но для симулятора, сборки приложения и Foundation Models нужен Xcode.
- Gemini Nano работает только на поддерживаемых телефонах, не в эмуляторе.
