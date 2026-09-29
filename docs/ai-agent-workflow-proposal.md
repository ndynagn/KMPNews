# KMPNews: выжимка практик AI-assisted разработки

Дата: 2026-09-29. Проверенный checkout: `735400a`.

> Update: sections 1-9 describe the original research baseline. See section 10 and [implementation results](native-macos-implementation.md) for the accepted decisions and actual implementation status.


Статус: предложение правил и последовательности работ. Документ не означает, что миграции выполнены, платформы добавлены или проверки сборки пройдены. Исходный код и конфигурация приложения в этой задаче не менялись.

## 1. Решения пользователя и текущее состояние

Задано пользователем: news reader; общий Kotlin без shared UI; нативные ViewModel; iOS, Android, JVM Desktop и отдельный native macOS клиент; обязательный переход внутренней конфигурации iOS-проекта с `project.pbxproj` на JSON `project.xcproj`. Внешний контейнер `.xcodeproj` сохраняется. Синхронизируемые папки уже используются и не заменяют эту миграцию.

| Клиент | Целевая ответственность | Сейчас в checkout |
| --- | --- | --- |
| Android | Собственный Compose UI, Android ViewModel | `androidApp` зависит от `sharedUI` |
| iOS | SwiftUI, собственная Swift ViewModel | SwiftUI starter, импортирует `SharedLogic`; используется `project.pbxproj` |
| Desktop JVM | Собственный Compose Desktop UI и Kotlin/JVM ViewModel с lifecycle окна | `desktopApp` зависит от `sharedUI` |
| Native macOS | Отдельный SwiftUI/AppKit UI и Swift ViewModel с lifecycle окна | Приложение и `macosArm64` target отсутствуют |

`sharedLogic` содержит Android, JVM, `iosArm64`, `iosSimulatorArm64` и стартовые Greeting-примеры. iOS framework называется `SharedLogic`, статический. Интеграция уже использует `:sharedLogic:embedAndSignAppleFrameworkForXcode`.

Desktop JVM, запущенный на Mac или упакованный в DMG, не заменяет native macOS клиент. Под «native ViewModel» для JVM понимается собственная модель представления этого клиента, а не Android ViewModel внутри общего модуля.

Проверенные файлы: [sharedLogic](../sharedLogic/build.gradle.kts), [sharedUI](../sharedUI/build.gradle.kts), [Android](../androidApp/build.gradle.kts), [Desktop](../desktopApp/build.gradle.kts), [Xcode project](../iosApp/iosApp.xcodeproj/project.xcproj).

## 2. Что переносим из материалов

| Источник | Практика | Применение к KMPNews и ограничения |
| --- | --- | --- |
| [Touchlab: Using AI to Check Your KMP Readiness](https://touchlab.co/using-ai-to-check-your-kmp-readiness) | Проверять факты скриптами, поручать агенту исследование неизвестного, структурировать результат | Для каждой зависимости проверять конкретную версию и нужные targets. Наличие `common` variant само по себе не доказывает поддержку macOS. JSON Schema проверяет форму отчёта, а достоверность подтверждают metadata, документация и сборка. |
| [reachable-kmp: CLAUDE.md](https://github.com/happycodelucky/reachable-kmp/blob/main/CLAUDE.md) | Явно описать headless shared-модуль, platform boundaries и Swift interop | Берём конкретность правил. Не переносим shared ViewModel, обязательность SKIE, ARM-only политику или весь список библиотек. Это пример инструкций репозитория, не доказательство оптимального workflow. |
| [Nami: How I Built My First iOS App with AI](https://tectontide.com/en/blog/building-nami-ios-app-with-ai/) | Дать агенту цикл build → test → run → screenshot → correction; хранить проектные знания рядом с кодом | Для каждого клиента нужны воспроизводимые команды и runtime/UI-проверки. MCP — один из способов подключения инструментов. Разделение Claude для спецификации и Codex для реализации — опыт одного автора, не обязательное распределение ролей. Кейс про SwiftUI, без KMP. |
| [Aetherius: AI-Assisted Compose Multiplatform Migration](https://www.aetherius-solutions.com/blog-posts/ai-assisted-kmp-compose-multiplatform-migration) | Ограничивать задачу, проверять platform boundaries и native dependencies, отличать гипотезу от установленной причины | Миграция формата Xcode, добавление macOS и функциональная фича — отдельные изменения. Переносить UI в shared, как в статье, у нас не требуется. |
| [Andrea Della Porta: авторский анонс Coding With an AI Pair](https://www.linkedin.com/posts/andrea-della-porta-01_coding-with-an-ai-pair-a-mobile-developer-activity-7469449878633574400-uG5-) | На конкретных задачах агенты сходятся; неоднозначность оставляет архитектуру на их усмотрение | Перед реализацией фиксировать контракт и после неё проверять наиболее сомнительные допущения. Полный Medium-текст не был доступен; вывод ограничен анонсом автора. |
| [Gábor AUTH: Locking Down Claude Code](https://enaplo.hu/2026/07/12/locking-down-claude-code-sandbox/) | Подготовить рабочее окружение для Gradle и агента | Ошибки доступа к cache/сети отделять от дефектов кода. Не копировать исключение Gradle из sandbox как настройку по умолчанию. |
| [STMN: добавление iPad-клиента к KMP](https://tech.stmn.co.jp/entry/2026/06/26/152302) | Отдельный SwiftUI над общим Kotlin, явная интеграция и Apple CI | Берём практику отдельных клиентов. Shared ViewModel из кейса противоречит нашему решению и не переносится. Это архитектурный кейс, не отчёт об эффективности агентов. |
| [Touchlab: Consuming SKIE Flows in SwiftUI](https://touchlab.co/skie-swiftui) | Явно организовать наблюдение за Kotlin Flow на Swift-стороне | У нас поток потребляет нативная ViewModel/адаптер. Статья не обязывает ставить SKIE; preview SwiftUI API не вводить без отдельной проверки. |

## 3. Предлагаемые правила архитектуры

Эти правила — адаптация под решения пользователя, а не буквальные требования статей.

1. **Shared содержит domain и data.** Модели новостей, repository-контракты, use cases, получение и нормализация данных, persistence и правила обновления принадлежат `sharedLogic`. Конкретные фичи добавляются только по принятому продуктовому контракту.
2. **UI и ViewModel принадлежат клиентам.** В `sharedLogic` не добавлять Compose/SwiftUI, ViewModel базовые классы, screen state, навигацию, оконный lifecycle и presentation-coordinator под другим названием. Нативные ViewModel преобразуют domain-данные в UI state.
3. **Бизнес-правила не дублировать в ViewModel.** Условия дедупликации, стабильная идентичность новости, порядок данных и правила persistence должны иметь одну реализацию. Состояние выделения, раскрытого меню, размера окна и навигационного стека остаётся локальным.
4. **UI не обращается к сети или БД напрямую.** Направление: View → native ViewModel → shared use case/repository. DTO и типы драйвера хранилища не входят в публичный контракт экранов.
5. **Платформенные API изолировать.** Platform-specific код размещать в соответствующих source sets или внедрять через интерфейсы. `commonMain` не импортирует JVM/Android/UIKit/AppKit API. `expect/actual` не используется как контейнер всей платформенной реализации.
6. **Interop считать отдельным контрактом.** До массовой реализации проверить из Swift реальные экспортируемые типы, nullable-значения, ошибки, async-вызовы, подписки и отмену. Kotlin unit test не заменяет Swift consumer test.
7. **Владелец lifecycle указан явно.** Нативная ViewModel владеет своими задачами/подписками и завершает их в соответствующий момент. Сервисы с app scope имеют отдельного владельца; закрытие окна не должно уничтожать общий сервис для других окон. UI-state изменяется на соответствующем UI executor/thread.
8. **Один поддерживаемый способ interop.** SKIE, другой coroutine bridge или Swift export выбираются после минимального проверяемого примера и проверки совместимости с Kotlin проекта. Не смешивать механизмы между фичами. Библиотека для shared ViewModel нам не нужна.
9. **Платформенная согласованность означает одинаковую бизнес-семантику.** SwiftUI sidebar, Android navigation и desktop shortcuts могут отличаться. Результат refresh, состояние сохранённой статьи и трактовка ошибки должны соответствовать одному контракту.
10. **Зависимости проверять по полной матрице.** Общая библиотека должна поддерживать Android/JVM/iOS/macOS в используемой версии. Platform-only библиотеку не тянуть в common dependencies. Не обновлять весь toolchain ради небольшой фичи.

Целевое направление зависимостей:

```text
androidApp : Android UI + Android ViewModel ───────┐
iosApp     : SwiftUI + Swift ViewModel ───────────┤
desktopApp : Desktop UI + JVM ViewModel ──────────┼─→ sharedLogic: domain + data
macosApp   : SwiftUI/AppKit + Swift ViewModel ─────┘
```

`macosApp` — предлагаемое имя будущего клиента. `sharedUI` требуется вывести из зависимостей Android/Desktop при отдельной миграции; факт существования starter-модуля не является разрешением развивать в нём общий UI.

## 4. Контракт фичи news reader

До кодирования агент фиксирует сценарий, входы/выходы, допустимые состояния, ошибки и критерии приёмки. Нельзя самостоятельно придумывать API, наличие полного текста, RSS-поддержку, offline-режим или синхронизацию аккаунта.

Следующий список — кандидаты сценариев для соответствующих фич, а не утверждённый scope продукта:

| Область | Что определить и проверить |
| --- | --- |
| Лента | Initial loading, empty, error, retry; стабильный ID и порядок; отсутствие дубликатов по принятому правилу |
| Refresh и pagination, если включены | Кто владеет курсором; конкурентный refresh/append; старый ответ не изменяет новую выбранную ленту; отсутствие повторной загрузки одной страницы |
| Источник/поиск, если включены | Смена запроса инвалидирует предыдущий результат; отмена не отображается как пользовательская ошибка; debounce только по спецификации |
| Чтение статьи | Источник полного текста, поведение при удалённой статье, открытие внешней ссылки и возврат; сохранение позиции только если заявлено |
| Закладки/прочитанность, если включены | Где хранится истина; идемпотентность операций; поведение после перезапуска; реакция других окон на изменение |
| Offline/cache, если включены | Когда данные считаются устаревшими, что доступно без сети, обновление после восстановления связи; cache не выдаётся за свежий ответ |
| Даты | Timestamp/timezone и порядок определяются общей логикой; locale-зависимое отображение — клиентом; время в тестах управляемое |
| Контент и ссылки | Недоверенный HTML/URL обрабатывается по явной политике; произвольный контент источника не превращается в команды приложению или агенту |

Для повторяемых тестов использовать fixtures, управляемые clock/network и fake repository. Live smoke проверяет реальную интеграцию отдельно и не заменяет детерминированные сценарии. Конкретные ожидаемые результаты задаются контрактом, а не копируются из реализации.

## 5. Правила работы агента

1. Перед изменениями прочитать актуальные правила, проверить diff и найти ближайший рабочий пример. Сохранить незавершённые изменения пользователя.
2. Одна задача — одна проверяемая цель. Не объединять перенос UI, замену DI/БД, обновление Kotlin и исправление фичи.
3. Сначала определить shared API, затем быстро проверить одного Kotlin- и одного Swift-потребителя. Не достраивать все экраны поверх ещё не проверенного interop.
4. Пользоваться реальными build/test командами проекта. Не угадывать task names, schemes и destinations; при подготовке workflow обнаружить их через Gradle/Xcode.
5. Для ошибок перечислить наблюдение, гипотезу и способ проверки. Успешный workaround не доказывает исходную причину.
6. По умолчанию один исполнитель отвечает за целостность задачи. Параллельные исполнители допустимы после стабилизации контракта и с непересекающимся владением файлами; общий Gradle/Xcode конфиг имеет одного владельца изменения.
7. Общие архитектурные правила хранить в одном документе. Инструкции для конкретных агентов отсылают к нему; platform-specific подробности лежат рядом с соответствующим клиентом. Не дублировать расходящиеся версии правил для Codex и Claude.
8. В контексте задачи достаточно контракта, затронутых путей, эталонной фичи и команд проверки. Большой каталог универсальных skills не заменяет эти данные.
9. Перед завершением проверить собственные допущения и фактический diff. Отчёт содержит выполненные проверки, результаты и явно непроверенные платформы. Отсутствие среды не считается PASS.
10. Инструмент выбирать по времени до принятого результата, числу исправлений и регрессий. Постоянные роли «Claude — архитектор, Codex — исполнитель» вводить только после сравнения на наших задачах.

Краткий шаблон задания:

```text
Цель и пользовательский сценарий:
Контракт данных и бизнес-правила:
Затронутые клиенты и допустимые пути:
Эталон существующей реализации:
Изменения shared API и проверка Swift-потребителя:
Состояния, ошибки, lifecycle/cancellation:
Проверки и наблюдаемый критерий готовности:
Открытые вопросы, которые нельзя заменять догадкой:
```

## 6. Проверка результата

| Изменение | Минимальное доказательство |
| --- | --- |
| Shared domain/data | Релевантные поведенческие тесты; компиляция затронутых targets; native runtime tests для платформозависимого поведения |
| Публичный shared API | Компиляция всех потребителей, включая iOS и native macOS; при изменении async/error semantics — интеграционный сценарий |
| Native ViewModel | Состояния, ошибки, отмена и освобождение подписок с fake shared API |
| UI одного клиента | Build и запуск клиента; проверка сценария и визуального результата на релевантных размерах/режимах |
| macOS/Desktop lifecycle | Открытие/закрытие окон, keyboard/focus, отсутствие лишних задач после закрытия; multi-window сценарии, если поддерживаются |
| Build/Xcode-конфигурация | До/после сравнение settings, clean build затронутого приложения, launch; signing/archive отдельно от simulator build |

Сейчас README перечисляет `:sharedLogic:testAndroidHostTest`, `:sharedLogic:jvmTest`, `:sharedLogic:iosSimulatorArm64Test` и `:androidApp:assembleDebug`. Здесь они только прочитаны, не выполнены. macOS-команды добавляются после появления target. Точные задачи проверяются на используемой версии Gradle plugin.

## 7. Переход iOS-проекта на JSON project.xcproj

### Существующая организация папок

По [официальной документации Apple](https://developer.apple.com/documentation/xcode/managing-files-and-folders-in-your-xcode-project), преобразование выполняется через Project navigator → выбор группы → контекстное меню → Convert to Folder. Структура группы должна соответствовать файловой системе; расхождения Xcode показывает через Show Details. Папки уменьшают изменения project file при добавлении и удалении исходников.

В нашем проекте папки `iosApp` и `Configuration` уже представлены `PBXFileSystemSynchronizedRootGroup`; `iosApp` включена в `fileSystemSynchronizedGroups` app target. Для `Info.plist` задано исключение membership. Поэтому повторная конвертация исходников не нужна. Оставшиеся обычные группы корня/Products сами по себе не означают незавершённую миграцию.

При последующем изменении структуры нужно проверять target membership новых исходников, исключения Info.plist/ресурсов и сборку. Сейчас подтверждена конфигурация на диске, но build/launch не выполнялись.

Требуемая миграция — смена внутреннего формата конфигурации на JSON. Существующие синхронизируемые папки сохраняются.

### Что подтверждено Apple

[Официальная инструкция Apple](https://developer.apple.com/documentation/xcode/updating-your-xcode-project-configuration-file-format) различает внешний `.xcodeproj` и внутреннюю конфигурацию: прежний `project.pbxproj` заменяется JSON-файлом `.xcproj`. Документация указывает совместимость JSON с Xcode 27+, а [release notes Xcode 27.2 beta](https://developer.apple.com/documentation/xcode-release-notes/xcode-27_2-release-notes) описывают возможность переключения.

Официальный путь: выбрать проект в Project navigator → File inspector → Project Document → Project Format → JSON → Continue, если появится диалог. Внешний `.xcodeproj` остаётся. Это конвертация, не переименование расширения. Apple описывает откат через отмену удаления `.pbxproj` и добавления `.xcproj` в source control.

### Что установлено локально

- Активный Xcode: 27.0, build `27A266a`, `/Applications/Xcode.app`.
- `/Applications/Xcode-beta.app`: также 27.0, build `27A5237l`.
- `iosApp/iosApp.xcodeproj/project.pbxproj` имеет `objectVersion = 77` и синхронизируемые папки. Это ещё не JSON-формат.
- Конвертация локально не выполнялась; наличие пункта JSON в установленном UI не проверялось. Способность Xcode 27 читать формат не доказывает наличие конвертера в 27.0.

### План обязательной миграции

1. Снять исходные schemes/build settings и получить baseline iOS build/launch.
2. Использовать Xcode, где доступен официальный переключатель; при его отсутствии подготовить отдельную совместимую установку. Не менять глобальный selected Xcode автоматически.
3. Выполнить конвертацию отдельно от добавления macOS и изменений приложения.
4. Проверить сохранность targets, configurations, signing, bundle ID, deployment target, ресурсов, Info.plist и Kotlin build phase. Проверить schemes, Package.resolved (если присутствует), workspace и отсутствие ненужных изменений user data; не включать пользовательские файлы Xcode в коммит миграции.
5. Повторить build/launch в средах разработчика и CI. Проверить внешние инструменты, которые могли читать `.pbxproj` напрямую. Подтвердить, что внутри контейнера используется `project.xcproj`, а прежний `project.pbxproj` удалён конвертером.

Критерий: семантика проекта сохранена, формат изменён, интеграция Kotlin работает. Сама смена формата не требует перехода Swift 5 → Swift 6, смены deployment target или менеджера зависимостей.

### Сохранение KMP-интеграции

Существующий путь соответствует [официальной direct integration Kotlin](https://kotlinlang.org/docs/multiplatform/multiplatform-direct-integration.html): framework объявлен в Gradle, Run Script вызывает `embedAndSignAppleFrameworkForXcode` перед Compile Sources и учитывает `OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED`. При конвертации эти отношения нужно сохранить. Не заменять direct integration на CocoaPods/SPM только ради формата конфигурации.

## 8. Добавление native macOS

Предлагается начать с `macosArm64` в `sharedLogic` и небольшого отдельного SwiftUI macOS-приложения, которое импортирует общий framework и использует собственную Swift ViewModel. Наличие самого Gradle target без собирающегося Swift-клиента недостаточно.

По [актуальной таблице Kotlin/Native](https://kotlinlang.org/docs/native-target-support.html), `macosArm64` относится к Tier 1, а `macosX64` deprecated с Kotlin 2.3.20. Поддержку Intel следует решить отдельно исходя из аудитории; ARM-only пока является предложением, а не требованием пользователя.

Перед добавлением проверить:

- Все общие зависимости и framework export доступны для macOS.
- iOS-specific реализации не переиспользуются автоматически на macOS. Общий Apple-код может жить в `appleMain`, а UIKit-код остаётся в `iosMain`.
- Storage paths, lifecycle, network configuration и открытие ссылок имеют корректную macOS-реализацию.
- Shared runtime и нативный клиент проверяются отдельно от существующего JVM Desktop.
- Собственный app target, scheme и packaging/signing определены явно; Mac Catalyst не подменяет требуемый native macOS клиент.

## 9. Рекомендуемая последовательность внедрения

1. Зафиксировать короткие правила и один эталон native ViewModel → shared use case. Не превращать выжимку целиком в обязательный огромный prompt.
2. Подтвердить baseline iOS build/launch, выполнить официальную конвертацию `project.pbxproj` → `project.xcproj` и проверить сохранность конфигурации и KMP-интеграции. Синхронизируемые папки сохранить.
3. Разнести starter UI Android/Desktop, убрать зависимость обоих клиентов от `sharedUI`, сохранить работоспособность.
4. Добавить минимальный native macOS клиент и общую сборочную матрицу четырёх клиентов.
5. Реализовать одну вертикальную news-reader фичу по согласованному источнику данных: shared logic → native ViewModel/UI каждого клиента → тесты и runtime-проверка.

Независимые пункты 2 и 3 не обязаны блокировать друг друга. До реализации остаются продуктовые решения: источник новостей/API и лицензия контента, MVP-функции, полный текст или внешняя ссылка, offline/закладки, поддерживаемые Desktop OS, минимальная macOS и Intel. Эти решения нельзя выводить из примеров статей.

## 10. Accepted follow-up decisions and implementation status

The preceding inventory describes the baseline before implementation.
Native macOS is now implemented for Apple Silicon, macOS 14+, with a native
SwiftUI screen and Swift ViewModel. Android/JVM sharedUI separation remains a
separate task. See [actual implementation results](native-macos-implementation.md).

Keep a short root `AGENTS.md` for architecture invariants, shared domain/data,
native UI/ViewModel ownership, Git rules, and validation commands. Add scoped
`AGENTS.md` files at platform boundaries (`sharedLogic/`, `iosApp/`, `macosApp/`,
`androidApp/`, `desktopApp/`) and sufficiently complex feature directories only
where there are distinct contracts. Do not create a file in every feature by
default or duplicate root rules. Local files describe lifecycle, interop,
feature contracts and focused checks; link detailed architecture documents.
This decision is retained for later implementation: no AGENTS files were created
or replaced by this task.

JSON migration is complete: Xcode 27.2 beta (27B5028f) converted the working
project using File Inspector > Project Format > JSON. `project.xcproj` is now
active and `project.pbxproj` is removed. Both targets' resolved Debug build
settings are identical before/after conversion, including iOS signing team.
Global selected Xcode remains 27.0. See the implementation report for validation.


## 11. Stage closure

Native macOS integration and Xcode JSON migration are completed and accepted
for delivery on `dev` (2026-09-29). See the implementation report for the
validation matrix and explicit limits. The AGENTS hierarchy remains an
approved follow-up decision; creating those instruction files and separating
Android/JVM sharedUI are outside this completed stage.
