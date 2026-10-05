package com.pirxhio.affirmity.ui.affirmations

/**
 * True when any line wraps between two letters/digits of the same word. [lineEnds] are the
 * exclusive end offsets of every line; the last one is the end of the text, never a wrap.
 */
fun breaksInsideWord(text: CharSequence, lineEnds: List<Int>): Boolean =
    lineEnds.dropLast(1).any { end ->
        end in 1 until text.length &&
            text[end - 1].isLetterOrDigit() &&
            text[end].isLetterOrDigit()
    }
