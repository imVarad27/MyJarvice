package com.example.myjarvice.data

import java.math.BigDecimal
import java.math.MathContext

/** Small arithmetic grammar, never executes scripts or model-generated code. */
object LocalCalculator {
    fun evaluate(expression: String): String {
        require(expression.length <= 200) { "Expression is too long (200 characters maximum)." }
        val parser = Parser(expression)
        val value = parser.sum()
        parser.spaces()
        require(parser.pos == expression.length) { "Use numbers, +, -, *, / and parentheses only." }
        return value.stripTrailingZeros().toPlainString()
    }

    private class Parser(val input: String) {
        var pos = 0
        fun spaces() { while (pos < input.length && input[pos].isWhitespace()) pos++ }
        fun take(c: Char): Boolean {
            spaces()
            if (pos < input.length && input[pos] == c) { pos++; return true }
            return false
        }
        fun sum(): BigDecimal {
            var result = product()
            while (true) result = when {
                take('+') -> result.add(product(), MathContext.DECIMAL64)
                take('-') -> result.subtract(product(), MathContext.DECIMAL64)
                else -> return result
            }
        }
        fun product(): BigDecimal {
            var result = atom()
            while (true) result = when {
                take('*') -> result.multiply(atom(), MathContext.DECIMAL64)
                take('/') -> {
                    val divisor = atom()
                    require(divisor.signum() != 0) { "Cannot divide by zero." }
                    result.divide(divisor, MathContext.DECIMAL64)
                }
                else -> return result
            }
        }
        fun atom(): BigDecimal {
            if (take('+')) return atom()
            if (take('-')) return atom().negate()
            if (take('(')) {
                val value = sum()
                require(take(')')) { "Missing closing parenthesis." }
                return value
            }
            spaces()
            val start = pos
            while (pos < input.length && (input[pos] in '0'..'9' || input[pos] == '.')) pos++
            return input.substring(start, pos).toBigDecimalOrNull()
                ?: throw IllegalArgumentException("Expected a number.")
        }
    }
}
