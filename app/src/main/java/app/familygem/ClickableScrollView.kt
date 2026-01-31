package app.familygem

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.ScrollView
import kotlin.math.abs

/** ScrollView that does not consume touch events. */
class ClickableScrollView(context: Context, attrs: AttributeSet) : ScrollView(context, attrs) {

    private var startY = 0f

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startY = event.y
                super.onTouchEvent(event)
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(event.y - startY) > 20) {
                    return true // Starts scrolling
                }
            }
        }
        return false
    }
}
