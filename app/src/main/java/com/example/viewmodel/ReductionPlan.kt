package com.example.viewmodel

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class ReductionStage(val calories: Int, val weeks: Int)
data class ReductionPlan(val start: String, val stages: List<ReductionStage>)
data class ReductionPeriod(val stage: ReductionStage, val start: LocalDate, val endExclusive: LocalDate, val firstWeek: Int) {
    fun contains(date: LocalDate) = date >= start && date < endExclusive
    fun daysRemaining(date: LocalDate) = ChronoUnit.DAYS.between(date, endExclusive).coerceAtLeast(0)
}

object ReductionPlanSupport {
    fun validate(plan: ReductionPlan) {
        val start = LocalDate.parse(plan.start)
        require(start.year in 1900..2200) { "Podaj datę startu w formacie RRRR-MM-DD (1900–2200)." }
        require(plan.stages.size in 1..12) { "Plan powinien mieć od 1 do 12 etapów." }
        plan.stages.forEach {
            require(it.calories in 1..10000) { "Podaj dodatnią liczbę kalorii (do 10000)." }
            require(it.weeks in 1..52) { "Każdy etap powinien trwać od 1 do 52 tygodni." }
        }
    }
    fun periods(plan: ReductionPlan): List<ReductionPeriod> {
        validate(plan)
        var date = LocalDate.parse(plan.start)
        var week = 1
        return plan.stages.map { stage ->
            val end = date.plusWeeks(stage.weeks.toLong())
            ReductionPeriod(stage, date, end, week).also { date = end; week += stage.weeks }
        }
    }
}
