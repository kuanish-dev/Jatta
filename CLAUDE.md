# lang-engine

ИИ-учебник английского на Kotlin Multiplatform (Android + iOS), без сервера.
Научная основа и механики М1–М22: `docs/mechanics.html` (опубликовано: https://claude.ai/artifact/1rr8HKEgen5H7N3BP9XS3j).

## Модули
- `engine` — чистое ядро: FSRS, план дня, очередь занятия, лестница, отдых. Без часов, базы, сети и платформенных API.
- `study` — всё вокруг движка без UI: формат и загрузка контента, упражнения, проверка ответов, хранение прогресса (`LearnerStore`, JSON + журнал `answers.jsonl`), ход занятия (`StudyController` → `Screen`).
- `shared` — интерфейс на Compose Multiplatform. Только рисует `Screen` и вызывает `StudyController`.
- `androidApp` — `MainActivity`. iOS: `MainViewController()` в `shared/iosMain`, Xcode-проект появится после установки Xcode.

## Контент
- Источник — `content/en/`: `course.json` (темы по порядку) и `topics/<id>.json`. В приложение он копируется задачей `syncContent` модуля `shared`.
- Слово: `en`, `ru`, `note`, `examples` с `gap`. Правило: `ru` — правило одной строкой, `note` — объяснение, у примеров есть `gap`, `cue` и `wrong` (неверные варианты для выбора).
- `rank` (М8) ставим только из настоящего частотного списка, не на глаз.
- Загрузчик отказывается грузить битый контент; `ContentTest` проверяет настоящий курс и строит упражнение на каждой ступени.

## Правила
- Что, когда и сколько учить, решает только код движка. ИИ (Apple Foundation Models на iOS, Gemini Nano на Android) только проверяет свободные ответы; пока его нет, свободный ответ ученик сверяет с образцом сам.
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
