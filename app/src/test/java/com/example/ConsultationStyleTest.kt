package com.example
import com.example.viewmodel.ProgressAiContext
import org.junit.Assert.*
import org.junit.Test
class ConsultationStyleTest {
 @Test fun consultationPrioritizesDecisionsAndConsent() {
   val prompt = ProgressAiContext.SYSTEM
   assertTrue(prompt.contains("czy strategia wspiera cel"))
   assertTrue(prompt.contains("350 słów"))
   assertTrue(prompt.contains("Nigdy nie stosuj zmian automatycznie"))
   assertTrue(prompt.contains("Cel kalorii nie jest TDEE"))
   assertTrue(prompt.contains("sport_7"))
 }
}
