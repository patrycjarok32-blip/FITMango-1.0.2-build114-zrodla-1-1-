package com.example

import com.example.viewmodel.MangoPremium6
import org.junit.Assert.assertEquals
import org.junit.Test

class MangoPremium6Test {
    @Test fun treeNeverPenalizesGapsAndUsesRecordedDays() {
        assertEquals("Nasionko", MangoPremium6.treeStage(0).title)
        assertEquals("Nasionko", MangoPremium6.treeStage(4).title)
        assertEquals("Rośnie", MangoPremium6.treeStage(5).title)
        assertEquals("Młode drzewko", MangoPremium6.treeStage(14).title)
        assertEquals("Owocujące drzewko", MangoPremium6.treeStage(30).title)
        assertEquals("Owocujące drzewko", MangoPremium6.treeStage(100).title)
    }
}
