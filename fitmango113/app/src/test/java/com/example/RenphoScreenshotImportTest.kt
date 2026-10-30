package com.example

import com.example.viewmodel.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RenphoScreenshotImportTest {
    private fun tile(column: Int, row: Int, label: String, value: String) = listOf(
        RenphoOcrLine(label, column * 300 + 30, row * 260 + 20, column * 300 + 270, row * 260 + 45),
        RenphoOcrLine(value, column * 300 + 50, row * 260 + 80, column * 300 + 250, row * 260 + 125),
        RenphoOcrLine("−0.20", column * 300 + 80, row * 260 + 160, column * 300 + 220, row * 260 + 180))
    @Test fun threeColumnReportReadsAllThirteenMainValuesWithoutTakingComparisonDeltas() {
        val labels = listOf("Waga", "BMI", "Body Fat Percentage", "Tłuszcz podskórny", "Tłuszcz trzewny", "Masa mięśniowa",
            "Skeletal Muscle Percentage", "Masa kostna", "Beztłuszczowa masa ciała", "Wiek metaboliczny", "BMR", "Body Water Percentage", "Protein Percentage")
        val values = listOf("61.90 kg", "21.9", "29.5%", "27.5%", "5", "41.04 kg", "41.1%", "2.60 kg", "43.64 kg", "41", "1306 kcal", "48.4%", "16.4%")
        val lines = labels.indices.flatMap { tile(it % 3, it / 3, labels[it], values[it]) }
        val result = RenphoScreenshotImport.parse(lines, 900)
        assertEquals(13, result.values.size)
        assertEquals(61.9, result.values.getValue("weight").toDouble(), .001)
        assertEquals(1306.0, result.values.getValue("bmr").toDouble(), .001)
        assertEquals(16.4, result.values.getValue("protein").toDouble(), .001)
        assertTrue(result.warnings.isEmpty())
    }
    @Test fun missingValuesStayBlankAndSmallerComparisonZeroIsNotImportedAsTheMainValue() {
        val lines = tile(1, 0, "Tłuszcz trzewny", "unreadable").toMutableList()
        lines[2] = lines[2].copy(text = "0")
        assertFalse("visceral" in RenphoScreenshotImport.parse(lines, 900).values)
    }
    @Test fun conflictingScreenshotsDoNotSilentlyChooseOneWeighing() {
        val a = RenphoImportResult(mapOf("weight" to "61.9", "fat" to "29.5"), emptyList())
        val b = RenphoImportResult(mapOf("weight" to "62.9", "fat" to "29.5"), emptyList())
        val result = RenphoScreenshotImport.combine(listOf(a, b))
        assertFalse("weight" in result.values)
        assertEquals("29.5", result.values["fat"])
        assertTrue(result.warnings.isNotEmpty())
    }
    @Test fun wrappedEnglishLabelIsRecognizedAndNoDateIsInferredFromTheComparisonHeading() {
        val lines = listOf(RenphoOcrLine("Compared with 28.01.2026", 0, 0, 900, 30),
            RenphoOcrLine("Body Fat", 630, 100, 870, 125), RenphoOcrLine("Percentage", 630, 130, 870, 155),
            RenphoOcrLine("29,5%", 650, 180, 850, 225))
        val result = RenphoScreenshotImport.parse(lines, 900)
        assertEquals(mapOf("fat" to "29.5"), result.values)
    }
}
