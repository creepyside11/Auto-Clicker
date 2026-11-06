package com.creepyside.liquidclick.names

import kotlin.random.Random

data class Generated(val full: String, val nick: String, val region: String)

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

    fun nickname(full: String): String {
        val parts = full.split(" ").filter { it.isNotBlank() }
        val base = parts.joinToString(".") { toLatin(it) }.replace("..", ".")
        val tail = if (Random.nextBoolean()) {
            (Random.nextInt(10, 99)).toString()
        } else {
            listOf("x", "pro", "ace", "rus", "gg", "off").random()
        }
        return base + tail
    }

    fun generate(
        selected: List<String>,
        gender: String,
        count: Int,
        mixRegions: Boolean = true
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
            val full = "$first $last"
            result.add(Generated(full, nickname(full), surnameRegion))
        }
        return result.toList()
    }

    fun allNamesCount(): Int = pools.values.sumOf { it.male.size + it.female.size + it.surnames.size }
}
