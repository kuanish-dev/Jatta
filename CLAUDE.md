# lang-engine

ИИ-учебник английского на Kotlin Multiplatform (Android + iOS), без сервера.
Научная основа и механики М1–М22: `docs/mechanics.html` (опубликовано: https://claude.ai/artifact/1rr8HKEgen5H7N3BP9XS3j).

## Правила
- `engine` — чистый Kotlin в `commonMain`: без часов, базы, сети и платформенных API. Время приходит параметром (учебный день, epoch ms).
- Что, когда и сколько учить, решает только код движка. ИИ (Apple Foundation Models на iOS, Gemini Nano на Android) только проверяет свободные ответы и пишет тексты; он спрятан за интерфейсом, движок о нём не знает.
- Каждое число живёт в `EngineConfig` с меткой механики (М1–М22) и уровнем доказательности. В коде чисел нет.
- У каждой механики свой тест. Новая механика: правило в docs, параметр в `EngineConfig`, код, тест.
- FSRS — порт py-fsrs 6.3.2. При изменении формул перегенерировать векторы: `scripts/fsrs_vectors.py` (Python 3.10+, `pip install fsrs==6.3.2`).
- Простой код: без DI-фреймворков, данные и функции вместо иерархий классов.

## Команды
```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew :engine:jvmTest
./gradlew :engine:compileKotlinIosSimulatorArm64
```

## Окружение
- JDK 17 из Homebrew, системной Java нет.
- Xcode не установлен: iOS-код компилируется, но для симулятора, сборки приложения и Foundation Models нужен Xcode.
- Gemini Nano работает только на поддерживаемых телефонах (список в документации ML Kit GenAI).
