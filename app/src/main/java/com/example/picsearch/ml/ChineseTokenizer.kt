package com.example.picsearch.ml

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

class ChineseTokenizer(context: Context) {
    private val vocab: Map<String, Int> = loadVocab(context)

    fun encode(text: String, maxLen: Int = 52): IntArray {
        val out = IntArray(maxLen) { PAD }
        out[0] = CLS

        val tokens = wordpiece(basicTokenize(text))
        var p = 1
        for (t in tokens) {
            if (p >= maxLen - 1) break
            out[p++] = vocab[t] ?: UNK
        }
        out[p] = SEP
        return out
    }

    private fun basicTokenize(text: String): List<String> {
        val out = ArrayList<String>(text.length)
        val buf = StringBuilder()

        fun flush() {
            if (buf.isNotEmpty()) {
                out.add(buf.toString())
                buf.setLength(0)
            }
        }

        for (ch in text.trim()) {
            when {
                ch.isWhitespace() -> flush()
                isCjk(ch) -> {
                    flush()
                    out.add(ch.toString())
                }
                isPunc(ch) -> {
                    flush()
                    out.add(ch.toString())
                }
                else -> buf.append(ch)
            }
        }
        flush()

        for (i in out.indices) {
            val t = out[i]
            if (t.length > 1 && t.all { it.code < 128 }) {
                out[i] = t.lowercase(Locale.ROOT)
            }
        }
        return out
    }

    private fun wordpiece(tokens: List<String>): List<String> {
        val out = ArrayList<String>(tokens.size * 2)
        for (token in tokens) {
            if (vocab.containsKey(token)) {
                out.add(token)
                continue
            }
            if (token.length == 1) {
                out.add(UNK_TOKEN)
                continue
            }
            var start = 0
            val pieces = ArrayList<String>(4)
            var ok = true
            while (start < token.length) {
                var end = token.length
                var cur: String? = null
                while (start < end) {
                    val sub = token.substring(start, end)
                    val cand = if (start == 0) sub else "##$sub"
                    if (vocab.containsKey(cand)) {
                        cur = cand
                        break
                    }
                    end--
                }
                if (cur == null) {
                    ok = false
                    break
                }
                pieces.add(cur)
                start = end
            }
            if (!ok) out.add(UNK_TOKEN) else out.addAll(pieces)
        }
        return out
    }

    private fun isCjk(ch: Char): Boolean {
        val block = Character.UnicodeBlock.of(ch)
        return block === Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
            block === Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
            block === Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
            block === Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
            block === Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION ||
            block === Character.UnicodeBlock.HIRAGANA ||
            block === Character.UnicodeBlock.KATAKANA ||
            block === Character.UnicodeBlock.HANGUL_SYLLABLES
    }

    private fun isPunc(ch: Char): Boolean {
        val t = Character.getType(ch)
        return t == Character.CONNECTOR_PUNCTUATION.toInt() ||
            t == Character.DASH_PUNCTUATION.toInt() ||
            t == Character.START_PUNCTUATION.toInt() ||
            t == Character.END_PUNCTUATION.toInt() ||
            t == Character.OTHER_PUNCTUATION.toInt() ||
            t == Character.INITIAL_QUOTE_PUNCTUATION.toInt() ||
            t == Character.FINAL_QUOTE_PUNCTUATION.toInt()
    }

    private fun loadVocab(context: Context): Map<String, Int> {
        val map = HashMap<String, Int>(22000)
        context.assets.open("vocab.txt").use { ins ->
            BufferedReader(InputStreamReader(ins, Charsets.UTF_8)).useLines { lines ->
                var idx = 0
                for (line in lines) {
                    map[line.trim()] = idx++
                }
            }
        }
        return map
    }

    private companion object {
        const val PAD = 0
        const val UNK = 100
        const val CLS = 101
        const val SEP = 102
        const val UNK_TOKEN = "[UNK]"
    }
}

