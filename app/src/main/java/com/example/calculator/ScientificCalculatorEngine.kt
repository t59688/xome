package com.example.calculator

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Full-featured recursive-descent Scientific Calculator engine supporting:
 * - +, -, ×, ÷, ^, %, !, √, parentheses ()
 * - Trigonometric & logarithmic functions: sin, cos, tan, ln, log (with DEG / RAD mode)
 * - Constants: π, e
 * - Real-time preview evaluation & exact formatting
 */
object ScientificCalculatorEngine {

    data class CalcHistoryEntry(
        val id: Long = System.nanoTime(),
        val expression: String,
        val result: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    fun evaluateExpression(rawExpression: String, isDegreeMode: Boolean = true): Result<Double> {
        val cleaned = rawExpression
            .replace("×", "*")
            .replace("÷", "/")
            .replace("−", "-")
            .replace(" ", "")
            .trim()

        if (cleaned.isEmpty()) {
            return Result.failure(IllegalArgumentException("Empty expression"))
        }

        return try {
            val balanced = autoCloseParentheses(cleaned)
            val parser = ExpressionParser(balanced, isDegreeMode)
            val value = parser.parse()
            if (value.isNaN() || value.isInfinite()) {
                Result.failure(ArithmeticException("Math domain / division by zero"))
            } else {
                Result.success(value)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun formatNumber(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return "Error"
        val absVal = abs(value)
        if (absVal > 0.0 && (absVal >= 1e12 || absVal < 1e-7)) {
            return String.format(java.util.Locale.US, "%.6E", value)
                .replace(Regex("0+E"), "E")
                .replace(".E", "E")
        }
        return try {
            val bd = BigDecimal.valueOf(value).setScale(10, RoundingMode.HALF_UP).stripTrailingZeros()
            bd.toPlainString()
        } catch (_: Exception) {
            value.toString()
        }
    }

    private fun autoCloseParentheses(expr: String): String {
        var openCount = 0
        for (c in expr) {
            if (c == '(') openCount++
            else if (c == ')' && openCount > 0) openCount--
        }
        return if (openCount > 0) expr + ")".repeat(openCount) else expr
    }

    private class ExpressionParser(
        private val input: String,
        private val isDegrees: Boolean
    ) {
        private var pos = -1
        private var ch = 0

        private fun nextChar() {
            pos++
            ch = if (pos < input.length) input[pos].code else -1
        }

        private fun eat(charToEat: Int): Boolean {
            while (ch == ' '.code) nextChar()
            if (ch == charToEat) {
                nextChar()
                return true
            }
            return false
        }

        fun parse(): Double {
            nextChar()
            val x = parseExpression()
            if (pos < input.length) {
                throw IllegalArgumentException("Unexpected character: ${ch.toChar()}")
            }
            return x
        }

        // Grammar:
        // expression = term | expression `+` term | expression `-` term
        // term = power | term `*` power | term `/` power | implicit multiplication
        private fun parseExpression(): Double {
            var x = parseTerm()
            while (true) {
                when {
                    eat('+'.code) -> x += parseTerm()
                    eat('-'.code) -> x -= parseTerm()
                    else -> return x
                }
            }
        }

        private fun parseTerm(): Double {
            var x = parsePower()
            while (true) {
                when {
                    eat('*'.code) -> x *= parsePower()
                    eat('/'.code) -> {
                        val divisor = parsePower()
                        if (abs(divisor) < 1e-15) throw ArithmeticException("Division by zero")
                        x /= divisor
                    }
                    // Implicit multiplication: e.g. 2(3+4), 2π, 5sin(30)
                    ch == '('.code || ch == 'π'.code || ch == 'e'.code || ch == '√'.code ||
                        (ch in 'a'.code..'z'.code) -> {
                        x *= parsePower()
                    }
                    else -> return x
                }
            }
        }

        private fun parsePower(): Double {
            var x = parsePostfix()
            if (eat('^'.code)) {
                x = x.pow(parsePower())
            }
            return x
        }

        private fun parsePostfix(): Double {
            var x = parseFactor()
            while (true) {
                when {
                    eat('%'.code) -> x /= 100.0
                    eat('!'.code) -> x = factorial(x)
                    else -> return x
                }
            }
        }

        private fun parseFactor(): Double {
            if (eat('+'.code)) return parseFactor()
            if (eat('-'.code)) return -parseFactor()
            if (eat('√'.code)) return sqrt(parseFactor())

            var x: Double
            val startPos = pos
            when {
                eat('('.code) -> {
                    x = parseExpression()
                    eat(')'.code)
                }
                eat('π'.code) -> {
                    x = PI
                }
                ch == 'e'.code && (pos + 1 >= input.length || input[pos + 1] !in 'a'..'z') -> {
                    nextChar()
                    x = Math.E
                }
                (ch in '0'.code..'9'.code) || ch == '.'.code -> {
                    while ((ch in '0'.code..'9'.code) || ch == '.'.code) nextChar()
                    x = input.substring(startPos, pos).toDouble()
                }
                ch in 'a'.code..'z'.code -> {
                    while (ch in 'a'.code..'z'.code) nextChar()
                    val func = input.substring(startPos, pos)
                    x = parseFactor()
                    x = when (func) {
                        "sqrt" -> sqrt(x)
                        "sin" -> sin(if (isDegrees) Math.toRadians(x) else x)
                        "cos" -> cos(if (isDegrees) Math.toRadians(x) else x)
                        "tan" -> tan(if (isDegrees) Math.toRadians(x) else x)
                        "ln" -> ln(x)
                        "log" -> log10(x)
                        "abs" -> abs(x)
                        else -> throw IllegalArgumentException("Unknown function: $func")
                    }
                }
                else -> throw IllegalArgumentException("Unexpected character: ${ch.toChar()}")
            }
            return x
        }

        private fun factorial(n: Double): Double {
            if (n < 0 || n != kotlin.math.floor(n) || n > 170) return Double.NaN
            var acc = 1.0
            for (i in 2..n.toInt()) {
                acc *= i.toDouble()
            }
            return acc
        }
    }
}
