package com.creepyside.liquidclick.names

import kotlin.random.Random

data class Generated(
    val first: String,
    val last: String,
    val nick: String,
    val region: String
) {
    val full: String get() = first + " " + last
}

object Names {

    data class Pool(
        val male: List<String>,
        val female: List<String>,
        val surnames: List<String>
    )

    val regions: List<String> = listOf(
        "СНГ", "Европа", "Азия", "Ближний Восток", "Африка",
        "Латинская Америка", "США", "Индия", "Океания"
    )

    private val pools: Map<String, Pool> = mapOf(
        "СНГ" to Pool(
            male = listOf(
                "Родион", "Демид", "Всеволод", "Елисей", "Ратмир", "Мирон",
                "Савелий", "Игнат", "Прохор", "Добрыня", "Ярополк", "Святогор",
                "Тихон", "Богдан", "Емельян"
            ),
            female = listOf(
                "Агата", "Есения", "Мирослава", "Злата", "Аглая", "Устинья",
                "Прасковья", "Василиса", "Стефания", "Любава", "Ярослава",
                "Аксинья", "Нонна", "Марфа", "Пелагея"
            ),
            surnames = listOf(
                "Ковальчук", "Шевченко", "Гриценко", "Дорошенко", "Кравченко",
                "Литвиненко", "Ткачук", "Бондаренко", "Мельниченко", "Захарченко",
                "Звягинцев", "Ростоцкий", "Милославский", "Верещагин", "Измайлов",
                "Ковригин", "Овсянников", "Черкасов"
            )
        ),
        "Европа" to Pool(
            male = listOf(
                "Зигфрид", "Ласло", "Драган", "Маттиас", "Ференц", "Алоис",
                "Гуннар", "Жером", "Тибо", "Каспар", "Октавиан", "Игнацио",
                "Бартоломео", "Людвиг", "Эмиль"
            ),
            female = listOf(
                "Ингрид", "Астрид", "Бригитта", "Зофия", "Мариетта", "Луция",
                "Гизелла", "Аннелиза", "Фьоренца", "Кларисса", "Рената",
                "Хельга", "Джема", "Мирелла", "Сольвейг"
            ),
            surnames = listOf(
                "Ван дер Берг", "Хауптман", "Ковач", "Надь", "Попеску", "Янссен",
                "Линдквист", "Феррара", "Делла Роббиа", "Винтерфельд", "Монтень",
                "Лёвенштайн", "Штраусберг", "Монтеверди", "Ринальди", "Дюбуа"
            )
        ),
        "Азия" to Pool(
            male = listOf(
                "Такэси", "Хироси", "Кэндзи", "Мин-джун", "Хён-у", "Вэй", "Цзян",
                "Бао", "Дзиро", "Раттана", "Сомчай", "Ён-су", "Харуто", "Лян"
            ),
            female = listOf(
                "Сакура", "Юки", "Мэй", "Хэ-ин", "Лань", "Су-джин", "Аяка",
                "Тхин", "Кхамла", "Нозоми", "Ён-хи", "Сяоюй", "Маюми", "Лин"
            ),
            surnames = listOf(
                "Танака", "Ямамото", "Накамура", "Ким", "Пак", "Ли", "Ван",
                "Чжан", "Нгуен", "Чан", "Сомбат", "Ваттана", "Окада", "Фудзимото",
                "Кобаяси", "Сон"
            )
        ),
        "Ближний Восток" to Pool(
            male = listOf(
                "Амир", "Фархад", "Резо", "Карим", "Тарик", "Самир", "Ясир",
                "Халид", "Джамаль", "Фейсал", "Марван", "Зияд", "Аднан", "Бахтияр"
            ),
            female = listOf(
                "Лейла", "Зара", "Назрин", "Ширин", "Амина", "Сафия", "Джамиля",
                "Ясмин", "Роксана", "Гюльнара", "Сара", "Дина", "Малика", "Аиша"
            ),
            surnames = listOf(
                "Хаддад", "Аль-Мансур", "Азизов", "Насири", "Фархади", "Керимов",
                "Шарифи", "Аль-Рашид", "Бахтияров", "Тагиев", "Зейналов", "Аль-Халили"
            )
        ),
        "Африка" to Pool(
            male = listOf(
                "Кваме", "Чинеду", "Олусегун", "Тендаи", "Мбарек", "Секу", "Адама",
                "Барака", "Джибриль", "Тафара", "Ннамиди", "Боладжи", "Кефа"
            ),
            female = listOf(
                "Амара", "Ньянза", "Зулайха", "Фараи", "Тханджи", "Адеола",
                "Ифеома", "Келечи", "Саран", "Мвана", "Ннека", "Амината"
            ),
            surnames = listOf(
                "Окафор", "Менса", "Нкоси", "Дламини", "Адебайо", "Камара",
                "Мбата", "Ндиайе", "Туре", "Мутаи", "Овусу", "Бангура", "Мванги"
            )
        ),
        "Латинская Америка" to Pool(
            male = listOf(
                "Сантьяго", "Эмилиано", "Хоакин", "Рамиро", "Лусиано", "Факундо",
                "Балтасар", "Каэтано", "Игнасио", "Тьяго", "Матео", "Альваро"
            ),
            female = listOf(
                "Исадора", "Ксимена", "Амаранта", "Соледад", "Лусиана", "Марите",
                "Росарио", "Инес", "Долорес", "Кандела", "Валентина", "Эстрелья"
            ),
            surnames = listOf(
                "Кирога", "Бальмаседа", "Фуэнтес", "Саласар", "Ириарте",
                "Альварадо", "Сеспедес", "Окампо", "Вильягра", "Барриос",
                "Карденас", "Монтеро", "Саэнс"
            )
        ),
        "США" to Pool(
            male = listOf(
                "Деклан", "Грейсон", "Эмметт", "Холден", "Уайатт", "Брейди",
                "Колтон", "Роуэн", "Тобиас", "Сайлас", "Джэспер", "Лэндон"
            ),
            female = listOf(
                "Харпер", "Пейсли", "Бринли", "Слоун", "Элоиза", "Аделаида",
                "Джун", "Марлоу", "Скарлетт", "Хейзл", "Уиллоу", "Сэди"
            ),
            surnames = listOf(
                "Монтгомери", "Фитцджеральд", "Харрингтон", "Уэстбрук", "Каллахан",
                "Донован", "Маккалистер", "Торнтон", "Эшфорд", "Стерлинг",
                "Блэквуд", "Кендрик"
            )
        ),
        "Индия" to Pool(
            male = listOf(
                "Викрам", "Адитья", "Дхармеш", "Харш", "Нишант", "Рудра",
                "Варун", "Яш", "Арнав", "Кайрат", "Дэв", "Риши"
            ),
            female = listOf(
                "Прия", "Ниша", "Анушка", "Дивья", "Тара", "Мира", "Кавья",
                "Шрея", "Амрита", "Ишани", "Рия", "Санья"
            ),
            surnames = listOf(
                "Чаухан", "Раджпут", "Верма", "Наир", "Менон", "Редди", "Сен",
                "Бхаттачарья", "Рао", "Дешмукх", "Капур", "Малхотра"
            )
        ),
        "Океания" to Pool(
            male = listOf(
                "Тане", "Манаиа", "Рауири", "Калео", "Хеми", "Арики", "Джарра", "Вира"
            ),
            female = listOf(
                "Ароха", "Мелеани", "Кайани", "Талуа", "Марама", "Анахи", "Леилани", "Вайра"
            ),
            surnames = listOf(
                "Туилаги", "Вакатава", "Тауфа", "Кепа", "Матаи", "Рима",
                "Таумата", "Вака", "Нгата", "Тере"
            )
        )
    )

    private val translit = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e",
        'ё' to "e", 'ж' to "zh", 'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k",
        'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r",
        'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f", 'х' to "h", 'ц' to "ts",
        'ч' to "ch", 'ш' to "sh", 'щ' to "sch", 'ъ' to "", 'ы' to "y", 'ь' to "",
        'э' to "e", 'ю' to "yu", 'я' to "ya", ' ' to "", '-' to "", '’' to ""
    )

    /** Латиница с заглавной буквы: «Агата» → «Agata». */
    fun toEnglish(input: String): String =
        input.split(' ', '-')
            .filter { it.isNotBlank() }
            .joinToString(" ") { part ->
                toLatin(part).replace("_", "").replaceFirstChar { it.uppercaseChar() }
            }

    fun toLatin(input: String): String {
        val sb = StringBuilder()
        for (ch in input.lowercase()) {
            val mapped = translit[ch]
            when {
                mapped != null -> sb.append(mapped)
                ch in 'a'..'z' || ch in '0'..'9' -> sb.append(ch)
                ch == ' ' || ch == '-' -> sb.append('_')
            }
        }
        return sb.toString()
    }

    /** Ник без цифр: только латиница имени и фамилии. */
    fun nickname(first: String, last: String): String =
        (toLatin(first) + toLatin(last)).replace("_", "").replace(".", "")

    /** Своё слово или цифры, которые пользователь хочет добавить к нику. */
    fun customSuffix(raw: String): String {
        val cleaned = raw.trim().lowercase().filter { it.isLetterOrDigit() || it == '_' || it == '.' }
        return if (cleaned.isEmpty()) "" else cleaned
    }

    fun generate(
        selected: List<String>,
        gender: String,
        count: Int,
        mixRegions: Boolean = true,
        custom: String = "",
        appendCustom: Boolean = false
    ): List<Generated> {
        val active = if (selected.isEmpty()) regions else selected.filter { pools.containsKey(it) }
        if (active.isEmpty()) return emptyList()
        val result = LinkedHashSet<Generated>()
        var guard = 0
        while (result.size < count && guard < count * 60) {
            guard++
            val nameRegion = active.random()
            val surnameRegion = if (mixRegions && Random.nextInt(100) < 45) active.random() else nameRegion
            val namePool = pools.getValue(nameRegion)
            val surnamePool = pools.getValue(surnameRegion)
            val first = when (gender) {
                "m" -> namePool.male.random()
                "f" -> namePool.female.random()
                else -> if (Random.nextBoolean()) namePool.male.random() else namePool.female.random()
            }
            val last = surnamePool.surnames.random()
            val firstEn = toEnglish(first)
            val lastEn = toEnglish(last)
            val nick = nickname(firstEn, lastEn)
            val extra = if (appendCustom) customSuffix(custom) else ""
            result.add(Generated(firstEn, lastEn, nick + extra, surnameRegion))
        }
        return result.toList()
    }

    // ---------- Подстановки в сценариях ----------

    /** Все поддерживаемые токены подстановки. */
    val tokens: List<Pair<String, String>> = listOf(
        "{имя}" to "имя",
        "{фамилия}" to "фамилия",
        "{ник}" to "ник",
        "{name}" to "имя",
        "{surname}" to "фамилия",
        "{nick}" to "ник",
        "{полное}" to "имя и фамилия"
    )

    fun hasTokens(template: String): Boolean =
        tokens.any { (token, _) -> template.contains(token, ignoreCase = true) }

    /** Одна персона на шаг: все токены в тексте относятся к одному человеку. */
    fun onePerson(
        gender: String = "any",
        regions: List<String> = emptyList(),
        custom: String = "",
        appendCustom: Boolean = false
    ): Generated? = generate(
        selected = regions,
        gender = gender,
        count = 1,
        mixRegions = true,
        custom = custom,
        appendCustom = appendCustom
    ).firstOrNull()

    fun applyTokens(template: String, person: Generated): String {
        var result = template
        tokens.forEach { (token, kind) ->
            val value = when (kind) {
                "имя" -> person.first
                "фамилия" -> person.last
                "ник" -> person.nick
                else -> person.full
            }
            result = result.replace(token, value, ignoreCase = true)
        }
        return result
    }

    /** Ручной текст: строки, разделённые переводом строки, точкой с запятой или запятой. */
    fun manualLines(raw: String): List<String> =
        raw.split('\n', ';', ',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    /** Ручной текст с опциональной нумерацией. */
    fun manualResults(raw: String, numbering: Boolean, startAt: Int = 1): List<String> {
        val lines = manualLines(raw)
        return lines.mapIndexed { index, line ->
            if (numbering) (startAt + index).toString() + ". " + line else line
        }
    }

    fun allNamesCount(): Int = pools.values.sumOf { it.male.size + it.female.size + it.surnames.size }
}
