package com.example
import com.example.viewmodel.SportRecoveryAssessment
import org.junit.Assert.*
import org.junit.Test
class SportRecoveryAssessmentTest {
    @Test fun missingSleepDoesNotProducePositiveReadiness() {
        assertTrue(SportRecoveryAssessment.evaluate(5, 1, null, 0).message.contains("Niepełne"))
    }
    @Test fun fatigueTakesPriority() {
        assertTrue(SportRecoveryAssessment.evaluate(5, 5, 480, 0).message.contains("odpoczynek"))
    }
    @Test fun consecutiveTrainingPromptsReview() {
        assertTrue(SportRecoveryAssessment.evaluate(5, 1, 480, 3).message.contains("Trzy dni"))
    }
}
