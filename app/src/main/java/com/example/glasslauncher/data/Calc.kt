package com.example.glasslauncher.data

import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.pow

/** Tiny calculator for the search box: + − × ÷ ^ % and parentheses. */
object Calc {

    private val allowed = Regex("^[0-9.,+\\-*/×÷x^%() ]+$")
    private val hasOperator = Regex("[+\\-*/×÷x^%]")

    fun evaluate(input: String): String? {
        val text = input.trim()
        if (text.length < 3 || !allowed.matches(text) || !hasOperator.containsMatchIn(text)) return null
        if (!text.any { it.isDigit() }) return null
        val normalized = text.replace(',', '.').replace('×', '*').replace('x', '*').replace('÷', '/')
        val value = runCatching { Parser(normalized).parse() }.getOrNull() ?: return null
        if (value.isNaN() || value.isInfinite()) return null
        return format(value)
    }

    private fun format(v: Double): String {
        if (v == Math.rint(v) && kotlin.math.abs(v) < 1e15) return v.toLong().toString()
        return BigDecimal(v).round(MathContext(10)).stripTrailingZeros().toPlainString()
    }

    private class Parser(val s: String) {
        var i = 0

        fun parse(): Double {
            val v = expr()
            skip()
            if (i != s.length) error("unexpected")
            return v
        }

        private fun skip() {
            while (i < s.length && s[i] == ' ') i++
        }

        private fun expr(): Double {
            var v = term()
            while (true) {
                skip()
                if (i >= s.length) return v
                when (s[i]) {
                    '+' -> { i++; v += term() }
                    '-' -> { i++; v -= term() }
                    else -> return v
                }
            }
        }

        private fun term(): Double {
            var v = power()
            while (true) {
                skip()
                if (i >= s.length) return v
                when (s[i]) {
                    '*' -> { i++; v *= power() }
                    '/' -> { i++; v /= power() }
                    '%' -> {
                        i++
                        skip()
                        // "50%" alone means 0.5; "10 % 3" is a remainder
                        if (i < s.length && (s[i].isDigit() || s[i] == '(' || s[i] == '.')) v %= power() else v /= 100.0
                    }
                    else -> return v
                }
            }
        }

        private fun power(): Double {
            val base = unary()
            skip()
            if (i < s.length && s[i] == '^') {
                i++
                return base.pow(power())
            }
            return base
        }

        private fun unary(): Double {
            skip()
            if (i < s.length && s[i] == '-') { i++; return -unary() }
            if (i < s.length && s[i] == '+') { i++; return unary() }
            return atom()
        }

        private fun atom(): Double {
            skip()
            if (i < s.length && s[i] == '(') {
                i++
                val v = expr()
                skip()
                if (i >= s.length || s[i] != ')') error("missing )")
                i++
                return v
            }
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            if (start == i) error("number expected")
            return s.substring(start, i).toDouble()
        }
    }
}
