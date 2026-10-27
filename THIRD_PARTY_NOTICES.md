# Licenses

Jatta is free software: you can redistribute it and/or modify it under the terms of the
GNU General Public License as published by the Free Software Foundation, either version 3 of the
License, or (at your option) any later version (SPDX: `GPL-3.0-or-later`). See [LICENSE](LICENSE).

Copyright (C) 2026 the Jatta authors.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

### Additional permission under GNU GPL version 3 section 7

If you modify this Program, or any covered work, by linking or combining it with Google ML Kit
(the `com.google.mlkit` libraries, including the GenAI Prompt API, and the Google libraries they
depend on) (or a modified version of that library), containing parts covered by the terms of the
ML Kit Terms of Service, the licensors of this Program grant you additional permission to convey
the resulting work.

The wording follows the GNU GPL FAQ, "What legal issues come up if I use GPL-incompatible libraries
with GPL software?" (https://www.gnu.org/licenses/gpl-faq.html#GPLIncompatibleLibs). The sentence
about including the library's source is left out because ML Kit's source is not available.

## Third-party material in this repository

### py-fsrs 6.3.2 — MIT

`engine/src/commonMain/kotlin/com/jatta/engine/fsrs/Fsrs.kt` is a Kotlin port of the FSRS-6
scheduler and its default parameters from [py-fsrs](https://github.com/open-spaced-repetition/py-fsrs).
`scripts/fsrs_vectors.py` uses py-fsrs to produce the test vectors.
`engine/src/commonMain/kotlin/com/jatta/engine/fsrs/FsrsOptimizer.kt` follows the py-fsrs optimizer,
and `scripts/fsrs_optimizer_vectors.py` uses it to produce the optimizer test vectors.

```
MIT License

Copyright (c) 2022 Open Spaced Repetition

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

### New General Service List 1.2 — CC BY-SA 4.0

`sources/ngsl/` holds the NGSL 1.2 by Browne, C., Culligan, B., and Phillips, J., licensed under
[CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/). These files keep their own license
(details in `sources/ngsl/README.md`); they are used only by tests and are not shipped in the app.

The placement test word list `content/en/placement.json` (48 words sampled from NGSL 1.2 frequency
bands) is shipped in the app and remains under CC BY-SA 4.0.

### Reading texts — CC BY-SA 4.0 and public domain

`content/en/texts.json` (collected by `scripts/collect_texts.py`) is shipped in the app:

- Passages from [Simple English Wikipedia](https://simple.wikipedia.org), by Simple English Wikipedia
  contributors, licensed under [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/). Each
  passage records the exact page revision it comes from; markup and footnote marks were removed. These
  passages remain under CC BY-SA 4.0, and the app shows the source and license with every text.
- Passages from public-domain books via [Project Gutenberg](https://www.gutenberg.org): Lewis Carroll,
  *Alice's Adventures in Wonderland* (1865); *Three Hundred Aesop's Fables*, tr. George Fyler Townsend
  (1867); L. Frank Baum, *The Wonderful Wizard of Oz* (1900).

### Listening phrases — Tatoeba, CC BY 2.0 FR, CC BY 4.0 and CC BY-SA 4.0

`content/en/topics/phrases-*.json` and `content/en/audio/` (built by `scripts/collect_phrases.py` from the
hand-picked list in `sources/tatoeba/phrases.json`) are shipped in the app:

- English sentences and their Russian translations from [Tatoeba](https://tatoeba.org), licensed under
  [CC BY 2.0 FR](https://creativecommons.org/licenses/by/2.0/fr/). Every phrase links to its sentence page
  (`audio.source`), which names the authors and translators. Some Russian translations were edited
  (the list is in `sources/tatoeba/phrases.json`, field `translations`).
- Recordings by Tatoeba members who are native English speakers, each under the license its author chose:
  [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) or [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/).
  Every phrase records the author (`audio.by`) and the license (`audio.license`), and the app shows both
  with the phrase. The recordings were trimmed of silence at both ends, brought to one loudness and
  converted to AAC; the CC BY-SA recordings remain under CC BY-SA 4.0.

### Translation sentences — Tatoeba, CC BY 2.0 FR

`content/en/sentences.json` (built by `scripts/collect_sentences.py`; pairs left out after review are listed
in `sources/tatoeba/sentences.json`) is shipped in the app. It holds English sentences and their Russian
translations from [Tatoeba](https://tatoeba.org), licensed under
[CC BY 2.0 FR](https://creativecommons.org/licenses/by/2.0/fr/). The app links every sentence to its page
on Tatoeba, which names its authors and translators. `content/eval/translations.json` uses some of the same
sentences with answers written for testing.

### Fonts — SIL Open Font License 1.1

`shared/src/commonMain/composeResources/font/` holds Exo 2 (Copyright 2013 The Exo 2 Project Authors,
https://github.com/googlefonts/Exo-2.0) and JetBrains Mono (Copyright 2020 The JetBrains Mono Project
Authors, https://github.com/JetBrains/JetBrainsMono), both licensed under the
[SIL Open Font License 1.1](https://openfontlicense.org). The license texts ship with the app in
`shared/src/commonMain/composeResources/files/licenses/`. The fonts are bundled as separate works and
keep their own license; they are not covered by the GPL.

## Libraries the app is built with (not in this repository)

- Kotlin, kotlinx.coroutines, kotlinx.serialization, kotlinx-io, Compose Multiplatform, AndroidX (including WorkManager):
  Apache License 2.0, compatible with GPL-3.0.
- LiteRT-LM (`com.google.ai.edge.litertlm:litertlm-android`, runs Gemma 4 on phones without Gemini Nano):
  Apache License 2.0, compatible with GPL-3.0. Its license and third-party notices ship with the app in
  `shared/src/commonMain/composeResources/files/licenses/litert-lm/`. It brings Gson and kotlin-reflect
  (Apache License 2.0). `litertlm-jvm` is used only by tests on a computer.
- Gemma 4 E2B (`litert-community/gemma-4-E2B-it-litert-lm` on Hugging Face, by Google): Apache License 2.0.
  The model is not in this repository or the APK; the app downloads it on the learner's request.
- ML Kit GenAI Prompt API (`com.google.mlkit:genai-prompt`, Gemini Nano on Android): proprietary,
  [ML Kit Terms of Service](https://developers.google.com/ml-kit/terms). Not covered by the GPL;
  the additional permission above allows conveying Android builds that include it.
