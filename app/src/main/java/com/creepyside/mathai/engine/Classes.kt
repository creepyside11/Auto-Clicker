package com.creepyside.mathai.engine

/** Порядок классов обязан совпадать с обучением (model/classes.txt). */
object Classes {
    val LIST = listOf(
        "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
        "+", "-", "×", "÷", "=",
        "(", ")", "^", ".", ",",
        "/", "%", "!", "x", "y",
        "π", "√", "∛",
    )

    /** символ глифа -> символ в строке выражения */
    fun token(cls: String): String = if (cls == ",") "." else cls
}
