package com.pirxhio.affirmity.ui.affirmations

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WordBreakTest {
    @Test
    fun breakAtWhitespaceIsNotInsideAWord() {
        // "La desorientación" wrapped after "La " -> first line ends at offset 3.
        assertFalse(breaksInsideWord("La desorientación es temporal", listOf(3, 17, 29)))
    }

    @Test
    fun breakBetweenTwoLettersIsInsideAWord() {
        // "La / desorientació / n es temporal": the second line ends between 'ó' and 'n'.
        assertTrue(breaksInsideWord("La desorientación es temporal", listOf(3, 16, 29)))
    }

    @Test
    fun finalLineEndIsNeverABreak() {
        assertFalse(breaksInsideWord("Estoy en calma", listOf(14)))
    }

    @Test
    fun breakAfterPunctuationIsNotInsideAWord() {
        assertFalse(breaksInsideWord("Soy fuerte, soy capaz", listOf(12, 21)))
    }

    @Test
    fun emptyTextHasNoBreak() {
        assertFalse(breaksInsideWord("", emptyList()))
    }
}
