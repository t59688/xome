package com.example.calculator.graph

import kotlin.math.*

/**
 * Compile once, evaluate many times. Pure expression tree, no allocations per sample.
 * Radians are used in trigonometric functions, as in conventional graphing calculators.
 * Input is deliberately bounded to keep a malformed plot from tying up the UI.
 */
class GraphExpression private constructor(val source: String, private val tree: Node) {
    fun evaluate(variables: Variables): Double = tree.eval(variables)

    class Variables(
        var x: Double = 0.0,
        var y: Double = 0.0,
        var u: Double = 0.0,
        var t: Double = 0.0,
        var a: Double = 1.0
    )

    companion object {
        fun compile(source: String): Result<GraphExpression> = runCatching {
            val input = source.trim().replace('−', '-').replace('×', '*').replace('÷', '/')
            require(input.isNotEmpty()) { "请输入表达式" }
            require(input.length <= 192) { "表达式过长（最多 192 字符）" }
            val parser = Parser(input)
            GraphExpression(source, parser.parse())
        }
    }

    private fun interface Node { fun eval(v: Variables): Double }

    private class Parser(private val input: String) {
        private var pos = 0
        private var depth = 0

        fun parse(): Node {
            val node = expr()
            skip()
            if (pos != input.length) fail("无法识别的字符")
            return node
        }

        private fun skip() { while (pos < input.length && input[pos].isWhitespace()) pos++ }
        private fun peek(): Char { skip(); return raw() }
        private fun raw(): Char = input.getOrNull(pos) ?: '\u0000'
        private fun eat(c: Char): Boolean {
            if (peek() == c) { pos++; return true }
            return false
        }
        private fun fail(message: String): Nothing =
            throw IllegalArgumentException(message + " · 第 " + (pos + 1) + " 位")

        private inline fun guarded(block: () -> Node): Node {
            if (++depth > 40) fail("表达式嵌套过深")
            try { return block() } finally { depth-- }
        }

        private fun expr(): Node = guarded {
            var left = product()
            while (true) {
                left = when {
                    eat('+') -> {
                        val l = left; val r = product()
                        Node { v -> l.eval(v) + r.eval(v) }
                    }
                    eat('-') -> {
                        val l = left; val r = product()
                        Node { v -> l.eval(v) - r.eval(v) }
                    }
                    else -> return@guarded left
                }
            }
            left
        }

        private fun product(): Node = guarded {
            var left = unary()
            while (true) {
                val op = peek()
                left = when {
                    eat('*') -> {
                        val l = left; val r = unary()
                        Node { v -> l.eval(v) * r.eval(v) }
                    }
                    eat('/') -> {
                        val l = left; val r = unary()
                        Node { v -> l.eval(v) / r.eval(v) }
                    }
                    // Implicit multiplication: 2x, 2(x+1), 2sin(x), pi x.
                    op == '(' || op == 'π' || op.isLetter() -> {
                        val l = left; val r = unary()
                        Node { v -> l.eval(v) * r.eval(v) }
                    }
                    else -> return@guarded left
                }
            }
            left
        }

        private fun unary(): Node = guarded {
            when {
                eat('+') -> unary()
                eat('-') -> {
                    val operand = unary()
                    Node { v -> -operand.eval(v) }
                }
                else -> power()
            }
        }

        private fun power(): Node = guarded {
            val base = atom()
            if (eat('^')) {
                val exponent = unary() // right associative, x^-2 and x^2^3
                Node { v -> base.eval(v).pow(exponent.eval(v)) }
            } else base
        }

        private fun atom(): Node = guarded {
            if (eat('(')) {
                val node = expr()
                if (!eat(')')) fail("缺少右括号")
                return@guarded node
            }
            if (eat('√')) {
                val operand = unary()
                return@guarded Node { v -> sqrt(operand.eval(v)) }
            }
            if (eat('π')) return@guarded Node { Math.PI }

            val start = pos
            val c = peek()
            if (c.isDigit() || c == '.') {
                var digits = 0
                while (raw().isDigit()) { pos++; digits++ }
                if (raw() == '.') {
                    pos++
                    while (raw().isDigit()) { pos++; digits++ }
                }
                if (digits == 0) fail("无效数字")
                val expStart = pos
                if (raw() == 'e' || raw() == 'E') {
                    pos++
                    if (raw() == '+' || raw() == '-') pos++
                    var expDigits = 0
                    while (raw().isDigit()) { pos++; expDigits++ }
                    if (expDigits == 0) pos = expStart
                }
                val number = input.substring(start, pos).toDoubleOrNull()
                    ?: fail("无效数字")
                return@guarded Node { number }
            }

            if (c.isLetter()) {
                while (raw().isLetter()) pos++
                val name = input.substring(start, pos).lowercase()
                if (!eat('(')) {
                    return@guarded when (name) {
                        "pi" -> Node { Math.PI }
                        "e" -> Node { Math.E }
                        "x" -> Node { it.x }
                        "y" -> Node { it.y }
                        "t" -> Node { it.t }
                        "u", "theta" -> Node { it.u }
                        "a" -> Node { it.a }
                        else -> fail("未知变量：" + name)
                    }
                }
                val first = expr()
                val second = if (eat(',')) expr() else null
                if (!eat(')')) fail("缺少右括号")
                return@guarded when (name) {
                    "sin" -> Node { sin(first.eval(it)) }
                    "cos" -> Node { cos(first.eval(it)) }
                    "tan" -> Node { tan(first.eval(it)) }
                    "sinh" -> Node { sinh(first.eval(it)) }
                    "cosh" -> Node { cosh(first.eval(it)) }
                    "tanh" -> Node { tanh(first.eval(it)) }
                    "sinc" -> Node {
                        val v = first.eval(it)
                        if (abs(v) < 1e-9) 1.0 else sin(v) / v
                    }
                    "sech" -> Node {
                        val c = cosh(first.eval(it))
                        if (abs(c) < 1e-12) 1e12 else 1.0 / c
                    }
                    "asin" -> Node { asin(first.eval(it)) }
                    "acos" -> Node { acos(first.eval(it)) }
                    "atan" -> Node { atan(first.eval(it)) }
                    "sqrt" -> Node { sqrt(first.eval(it)) }
                    "abs" -> Node { abs(first.eval(it)) }
                    "exp" -> Node { exp(first.eval(it)) }
                    "ln" -> Node { ln(first.eval(it)) }
                    "log" -> Node { log10(first.eval(it)) }
                    "floor" -> Node { floor(first.eval(it)) }
                    "ceil" -> Node { ceil(first.eval(it)) }
                    "sign" -> Node { sign(first.eval(it)) }
                    "min" -> {
                        if (second == null) fail("min 需要两个参数")
                        Node { min(first.eval(it), second.eval(it)) }
                    }
                    "max" -> {
                        if (second == null) fail("max 需要两个参数")
                        Node { max(first.eval(it), second.eval(it)) }
                    }
                    "pow" -> {
                        if (second == null) fail("pow 需要两个参数")
                        Node { first.eval(it).pow(second.eval(it)) }
                    }
                    else -> fail("未知函数：" + name)
                }
            }
            fail(if (c == '\u0000') "表达式不完整" else "不支持的字符")
        }
    }
}
