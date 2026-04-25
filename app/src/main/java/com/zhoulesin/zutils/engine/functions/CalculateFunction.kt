package com.zhoulesin.zutils.engine.functions

import com.zhoulesin.zutils.engine.core.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

class CalculateFunction : ZFunction {
    override val info = FunctionInfo(
        name = "calculate",
        description = "Evaluate a mathematical expression and return the result",
        parameters = listOf(
            Parameter(
                name = "expression",
                description = "Mathematical expression to evaluate (e.g. \"2 + 2 * 3\")",
                type = ParameterType.STRING,
                required = true,
            )
        ),
        outputType = OutputType.NUMBER,
    )

    override suspend fun execute(context: ExecutionContext, args: JsonObject): ZResult {
        val expression = args["expression"]?.jsonPrimitive?.content
            ?: return ZResult.fail("Missing required argument: expression", "MISSING_ARG")

        return try {
            val result = evaluate(expression)
            ZResult.ok(result)
        } catch (e: Exception) {
            ZResult.fail("Failed to evaluate expression: ${e.message}", "EVAL_ERROR")
        }
    }

    private fun evaluate(expr: String): Number {
        val sanitized = expr.replace(" ", "")
        if (!sanitized.matches(Regex("^[0-9+\\-*/().]+$"))) {
            throw IllegalArgumentException("Invalid characters in expression")
        }
        val tokens = tokenize(sanitized)
        val postfix = infixToPostfix(tokens)
        return evaluatePostfix(postfix)
    }

    private fun tokenize(expr: String): List<String> {
        val tokens = mutableListOf<String>()
        var i = 0
        while (i < expr.length) {
            when {
                expr[i].isDigit() || expr[i] == '.' -> {
                    var num = ""
                    while (i < expr.length && (expr[i].isDigit() || expr[i] == '.')) {
                        num += expr[i]
                        i++
                    }
                    tokens.add(num)
                }
                else -> {
                    tokens.add(expr[i].toString())
                    i++
                }
            }
        }
        return tokens
    }

    private fun infixToPostfix(tokens: List<String>): List<String> {
        val precedence = mapOf("+" to 1, "-" to 1, "*" to 2, "/" to 2)
        val output = mutableListOf<String>()
        val operators = ArrayDeque<String>()

        for (token in tokens) {
            when {
                token.toDoubleOrNull() != null -> output.add(token)
                token == "(" -> operators.addLast(token)
                token == ")" -> {
                    while (operators.isNotEmpty() && operators.last() != "(") {
                        output.add(operators.removeLast())
                    }
                    operators.removeLastOrNull()
                }
                else -> {
                    while (operators.isNotEmpty() && operators.last() != "(" &&
                        (precedence[operators.last()] ?: 0) >= (precedence[token] ?: 0)
                    ) {
                        output.add(operators.removeLast())
                    }
                    operators.addLast(token)
                }
            }
        }
        while (operators.isNotEmpty()) output.add(operators.removeLast())
        return output
    }

    private fun evaluatePostfix(postfix: List<String>): Double {
        val stack = ArrayDeque<Double>()
        for (token in postfix) {
            val num = token.toDoubleOrNull()
            if (num != null) {
                stack.addLast(num)
            } else {
                val b = stack.removeLastOrNull() ?: 0.0
                val a = stack.removeLastOrNull() ?: 0.0
                stack.addLast(
                    when (token) {
                        "+" -> a + b
                        "-" -> a - b
                        "*" -> a * b
                        "/" -> a / b
                        else -> throw IllegalArgumentException("Unknown operator: $token")
                    }
                )
            }
        }
        return stack.last()
    }
}
