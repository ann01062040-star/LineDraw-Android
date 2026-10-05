package com.linedraw.app

import com.linedraw.app.engine.*
import org.junit.Assert.*
import org.junit.Test

class TargetSelectionTest {
    @Test fun terminalFooterUsesVisibleScreenBoundsAcrossSizes() {
        for(height in listOf(640,1280,2400,3200)) {
            val footer=ActionTarget("使用優惠券",0,height-100,400,height+2)
            assertTrue(TargetSelection.isFooter(footer,24,height))
            assertFalse(TargetSelection.isFooter(footer.copy(top=100,bottom=160),24,height))
            assertFalse(TargetSelection.isFooter(footer.copy(top=height,bottom=height+100),24,height))
        }
    }
    @Test fun labelledFooterWorksAcrossScreenSizesAndInsets() {
        for (height in listOf(640,1280,2400,3200)) {
            val header=ActionTarget("加入好友",20,100,350,150)
            val footer=ActionTarget("抽選",0,height-120,400,height-20)
            assertEquals(1,TargetSelection.pick(listOf(header,footer),24,height))
        }
    }
    @Test fun duplicateAccessibilityNodesForSameTargetAreOneButton() {
        val target=ActionTarget("抽選",0,900,400,1000)
        assertEquals(0,TargetSelection.pick(listOf(target,target),0,1000))
    }
    @Test fun webViewPixelRoundingAtScreenEdgeStillFindsVisibleButton() {
        assertEquals(0,TargetSelection.pick(listOf(ActionTarget("抽選",0,2184,1082,2402)),0,2400))
    }
    @Test fun twoFooterActionsOrOffscreenOrHugeParentsNeverGuess() {
        val a=ActionTarget("抽選",0,800,190,900)
        val b=ActionTarget("抽選",200,800,400,900)
        assertNull(TargetSelection.pick(listOf(a,b),0,1000))
        assertNull(TargetSelection.pick(listOf(a.copy(bottom=1200)),0,1000))
        assertNull(TargetSelection.pick(listOf(a.copy(top=0)),0,1000))
        assertNull(TargetSelection.pick(listOf(a),0,0))
    }
}
