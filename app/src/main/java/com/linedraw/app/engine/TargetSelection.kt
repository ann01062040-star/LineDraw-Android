package com.linedraw.app.engine

data class ActionTarget(val label: String, val left: Int, val top: Int, val right: Int, val bottom: Int)

/** All coordinates come from the current accessibility tree, in screen pixels. */
object TargetSelection {
    fun isFooter(target: ActionTarget, screenTop: Int, screenBottom: Int): Boolean {
        val height=screenBottom-screenTop
        val centre=target.top+(target.bottom-target.top)/2
        return height>0 && target.right>target.left && target.bottom>target.top &&
            target.top>=screenTop+height*0.65 && centre in screenTop until screenBottom
    }
    fun pick(targets: List<ActionTarget>, screenTop: Int, screenBottom: Int): Int? {
        val height = screenBottom - screenTop
        if (height <= 0) return null
        val unique = targets.indices.filter { i ->
            val t = targets[i]
            // WebView can round CSS edges a few pixels past the viewport. The tap centre
            // must be visible; requiring every edge to fit rejects valid bottom buttons.
            val centre=t.top+(t.bottom-t.top)/2
            t.right > t.left && t.bottom > t.top && centre in screenTop until screenBottom &&
                minOf(t.bottom,screenBottom)-maxOf(t.top,screenTop) >= (t.bottom-t.top)*0.5 &&
                t.bottom - t.top <= height * 0.45
        }.distinctBy { targets[it] }
        if (unique.size == 1) return unique.single()
        // Only prefer a unique, labelled action in the footer; never guess between footer buttons.
        return unique.filter { isFooter(targets[it],screenTop,screenBottom) }.singleOrNull()
    }
}
