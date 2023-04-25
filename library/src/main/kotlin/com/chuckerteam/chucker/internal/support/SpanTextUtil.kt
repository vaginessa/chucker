package com.chuckerteam.chucker.internal.support

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.core.content.ContextCompat
import androidx.core.text.isDigitsOnly
import com.chuckerteam.chucker.R
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException

public class SpanTextUtil(context: Context) {
    private val jsonKeyColor: Int
    private val jsonValueColor: Int
    private val jsonDigitsAndNullValueColor: Int
    private val jsonSignElementsColor: Int

    init {
        jsonKeyColor = ContextCompat.getColor(context, R.color.chucker_json_key_color)
        jsonValueColor = ContextCompat.getColor(context, R.color.chucker_json_value_color)
        jsonDigitsAndNullValueColor =
            ContextCompat.getColor(context, R.color.chucker_json_digit_and_null_value_color)
        jsonSignElementsColor = ContextCompat.getColor(context, R.color.chucker_json_elements_color)
    }

    private enum class TokenType {
        STRING,
        ARRAY,
        OBJECT,
        KEY_SEPARATOR,
        VALUE_SEPARATOR,
        NONE
    }

    private inline fun CharSequence.indexOfNextToken(
        startIndex: Int = 0,
        predicate: (Char) -> TokenType
    ): Pair<Int, TokenType> {
        for (index in startIndex until length) {
            predicate(this[index])?.let {
                return index to it
            }
        }
        return -1 to TokenType.NONE
    }

    private inline fun CharSequence.indexOfNextUnescapedQuote(startIndex: Int = 0): Int {
        var index = indexOf('"', startIndex)
        while (index < length) {
            if (this[index] == '"' && (index == 0 || this[index - 1] != '\\')) {
                return index
            }
            index = indexOf('"', index + 1)
        }
        return -1
    }
    public fun simpleSpanJson(input: CharSequence): SpannableStringBuilder {
        // first handle pretty printing via gson
        val prettyPrintedInput = FormatUtils.formatJson(input.toString())
        val arrayTokens = listOf('[', ']')
        val objectTokens = listOf('{', '}')
        val keySeparatorTokens = listOf(':')
        val valueSeparatorTokens = listOf(',')
        val stringTokens = listOf('"')

        var lastTokenType: TokenType? = null
        var index = 0

        val sb = SpannableStringBuilder(prettyPrintedInput)
        while (index < prettyPrintedInput.length) {
            val (tokenIndex, tokenType) = prettyPrintedInput.indexOfNextToken(startIndex = index) { char ->
                when (char) {
                    in arrayTokens -> TokenType.ARRAY
                    in objectTokens -> TokenType.OBJECT
                    in keySeparatorTokens -> TokenType.KEY_SEPARATOR
                    in valueSeparatorTokens -> TokenType.VALUE_SEPARATOR
                    in stringTokens -> TokenType.STRING
                    else -> TokenType.NONE
                }
            }
            when (tokenType) {
                TokenType.ARRAY,
                TokenType.OBJECT,
                TokenType.KEY_SEPARATOR,
                TokenType.VALUE_SEPARATOR -> {
                    sb.setColor(
                        start = tokenIndex,
                        end = tokenIndex,
                        color = jsonSignElementsColor,
                    )
                    index = tokenIndex + 1
                }
                TokenType.STRING -> {
                    val color = when (lastTokenType) {
                        TokenType.ARRAY,
                        TokenType.OBJECT,
                        TokenType.VALUE_SEPARATOR,
                        TokenType.NONE,
                        null -> {
                            jsonKeyColor
                        }
                        else -> {
                            jsonValueColor
                        }
                    }
                    val endIndex = prettyPrintedInput.indexOfNextUnescapedQuote(tokenIndex + 1)
                    // if we somehow get an incomplete string, we lose the ability to parse any other
                    // tokens, so just return now
                    if (endIndex < tokenIndex) {
                        return sb
                    }
                    sb.setColor(start = tokenIndex, end = endIndex, color)
                    index = endIndex + 1
                }
                TokenType.NONE -> return sb
            }
            lastTokenType = tokenType
        }
        return sb
    }

    public fun spanJson(input: CharSequence): SpannableStringBuilder {
        val jsonElement = try {
            JsonParser.parseString(input.toString())
        } catch (e: JsonSyntaxException) {
            Logger.warn("Json structure is invalid so it can not be formatted", e)
            return SpannableStringBuilder.valueOf(input)
        }
        return SpannableStringBuilder().also {
            printifyRecursive(it, StringBuilder(""), jsonElement)
        }
    }
    private fun printifyRecursive(
        sb: SpannableStringBuilder,
        currentIndent: StringBuilder,
        transformedJson: JsonElement
    ) {
        val indent = StringBuilder(currentIndent)
        if (transformedJson.isJsonArray) {
            printifyJsonArray(sb, indent, transformedJson)
        }
        if (transformedJson.isJsonObject) {
            printifyJsonObject(sb, indent, transformedJson)
        }
    }

    private fun printifyJsonArray(
        sb: SpannableStringBuilder,
        indent: StringBuilder,
        transformedJson: JsonElement
    ) {
        if (transformedJson.asJsonArray.isEmpty) {
            sb.appendWithColor(
                "[]",
                jsonSignElementsColor
            )
            return
        }
        sb.appendWithColor("$indent[\n", jsonSignElementsColor)
        indent.append("  ")
        for (index in 0 until transformedJson.asJsonArray.size()) {
            val item = transformedJson.asJsonArray[index]
            if (item.isJsonObject || item.isJsonArray) {
                printifyRecursive(sb, indent, item)
            } else {
                sb.append(indent)
                sb.appendJsonValue(item)
            }
            if (index != transformedJson.asJsonArray.size() - 1) {
                sb.appendWithColor(",", jsonSignElementsColor).append("\n")
            }
        }
        val finalIndent = StringBuilder(indent.dropLast(2))
        sb.appendWithColor("\n$finalIndent]", jsonSignElementsColor)
    }

    private fun printifyJsonObject(
        sb: SpannableStringBuilder,
        indentBuilder: StringBuilder,
        transformedJson: JsonElement
    ) {
        if (transformedJson.asJsonObject.size() == 0) {
            sb.appendWithColor(
                "{}",
                jsonSignElementsColor
            )
            return
        }
        sb.appendWithColor("$indentBuilder{\n", jsonSignElementsColor)
        indentBuilder.append("  ")
        var index = 0
        for (item in transformedJson.asJsonObject.entrySet()) {
            sb.append(indentBuilder)
            index++
            sb.appendWithColor("\"${item.key}\"", jsonKeyColor)
                .appendWithColor(":", jsonSignElementsColor)
            if (item.value.isJsonObject || item.value.isJsonArray) {
                sb.append(" ")
                printifyRecursive(sb, indentBuilder, item.value)
            } else {
                sb.appendJsonValue(item.value)
            }
            if (index != transformedJson.asJsonObject.size()) {
                sb.appendWithColor(",", jsonSignElementsColor).append("\n")
            }
        }
        sb.appendWithColor("\n${indentBuilder.dropLast(2)}}", jsonSignElementsColor)
    }

    private fun SpannableStringBuilder.appendWithColor(text: CharSequence, color: Int): SpannableStringBuilder {
        this.append(
            text,
            ChuckerForegroundColorSpan(color),
            Spanned.SPAN_INCLUSIVE_INCLUSIVE
        )
        return this
    }

    private fun SpannableStringBuilder.setColor(start: Int, end: Int, color: Int): SpannableStringBuilder {
        this.setSpan(
            ChuckerForegroundColorSpan(color),
            start,
            end,
            Spanned.SPAN_INCLUSIVE_INCLUSIVE,
        )
        return this
    }

    private fun SpannableStringBuilder.appendJsonValue(jsonValue: JsonElement): SpannableStringBuilder {
        val isDigit = jsonValue.isJsonNull.not() &&
            jsonValue.asString.isNotEmpty() &&
            jsonValue.isJsonPrimitive &&
            jsonValue.asString.isDigitsOnly()
        val value = if (isDigit) jsonValue.asString else jsonValue.toString()
        val color = if (isDigit || jsonValue.isJsonNull) {
            jsonDigitsAndNullValueColor
        } else {
            jsonValueColor
        }
        return this.appendWithColor(
            " $value",
            color
        )
    }

    public class ChuckerForegroundColorSpan(color: Int) : ForegroundColorSpan(color)
}
