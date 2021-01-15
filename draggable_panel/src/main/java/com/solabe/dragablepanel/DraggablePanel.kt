package com.solabe.dragablepanel

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.customview.widget.ViewDragHelper
import java.lang.IllegalStateException

/*| ------ open state ------ |  draggingBorder == -draggingRange */
/*|                          |*/
/*|                          |*/
/*|                          |*/
/*|                          |*/
/*| ----- anchor state ----- |  draggingBorder == -anchorState */
/*|                          |*/
/*|                          |*/
/*|                          |*/
/*|                          |*/
/*| ----- closed state ----- | draggingBorder = 0 */


class DraggablePanel(context: Context, attrs: AttributeSet) : CoordinatorLayout(context, attrs) {

    private var draggingState = 0
    private var dragHelper: ViewDragHelper? = null
    private var draggingBorder: Int = 0
    var onViewPositionChanged: (Int)->(Unit) = {}
    var peekHeight = 0
    var currentState = States.STATE_CLOSED
    var maxOffset: Int = 0

    var draggingRange : Int = 0
        set(value) {
            if (value < 0) throw IllegalStateException("Dragging range cannot be less than 0")
            field = value
        }

    var anchorRange : Int = 0
        set(value) {
            if (value < 0) throw IllegalStateException("Anchor range cannot be less than 0")
            field = value
        }

    val isMoving: Boolean
        get() = draggingState == ViewDragHelper.STATE_DRAGGING || draggingState == ViewDragHelper.STATE_SETTLING

    init {
        dragHelper = ViewDragHelper.create( this, 1.0f, DragHelperCallback())

        context.obtainStyledAttributes(attrs, R.styleable.AnchorDraggablePage).apply {
            draggingRange = getDimensionPixelSize(R.styleable.AnchorDraggablePage_dragRange, 0)
            anchorRange = getDimensionPixelSize(R.styleable.AnchorDraggablePage_anchorHeight, 0)
            peekHeight = getDimensionPixelSize(R.styleable.AnchorDraggablePage_peekHeight, 0)
        }.recycle()
    }

    override fun onLayoutChild(child: View, layoutDirection: Int) {
        super.onLayoutChild(child, layoutDirection)
        maxOffset = if (peekHeight == 0) {
            height/2
        } else {
            height - peekHeight
        }
        child.translationY = maxOffset.toFloat()
    }

    inner class DragHelperCallback : ViewDragHelper.Callback() {
        override fun onViewDragStateChanged(state: Int) {
            if (state == draggingState) { // no change
                return
            }
            if ((draggingState == ViewDragHelper.STATE_DRAGGING || draggingState == ViewDragHelper.STATE_SETTLING) && state == ViewDragHelper.STATE_IDLE) {
                // the view stopped from moving.
                when (Math.abs(draggingBorder)) {
                    0 -> currentState = States.STATE_CLOSED
                    draggingRange -> currentState = States.STATE_OPEN
                    else -> if (anchorRange != 0) {
                        if (Math.abs(draggingBorder) == anchorRange) {
                            currentState = States.STATE_ANCHOR
                        }
                    }
                }
            }
            draggingState = state
        }

        override fun onViewPositionChanged(changedView: View, left: Int, top: Int, dx: Int, dy: Int) {
            draggingBorder = top
            onViewPositionChanged(dy)
        }

        override fun getViewVerticalDragRange(child: View): Int {
            return draggingRange
        }

        override fun tryCaptureView(view: View, i: Int): Boolean {
            return true
        }

        override fun clampViewPositionVertical(child: View, top: Int, dy: Int): Int {
            val bottomBound = draggingRange
            return if (top < 0) Math.max(-bottomBound, top) else 0
        }

        override fun onViewReleased(releasedChild: View, xvel: Float, yvel: Float) {
            var settleDestY = 0
            when (currentState) {
                States.STATE_OPEN -> {
                    if (yvel > 0) {
                        settleDestY = -anchorRange
                    }
                }
                States.STATE_ANCHOR -> {
                    settleDestY = if (yvel > 0) 0 else -draggingRange
                }
                States.STATE_CLOSED -> {
                    if (yvel < 0) {
                        settleDestY = if (draggingBorder >= -anchorRange) {
                            -anchorRange
                        } else {
                            -draggingRange
                        }
                    }
                }
            }
            if (dragHelper?.settleCapturedViewAt(0, settleDestY) == true) {
                ViewCompat.postInvalidateOnAnimation(this@DraggablePanel)
            }
        }
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        return dragHelper?.shouldInterceptTouchEvent(event) ?: true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return if (isMoving) {
            dragHelper?.processTouchEvent(event)
            true
        } else {
            super.onTouchEvent(event)
        }
    }

    override fun computeScroll() { // needed for automatic settling.
        if (dragHelper?.continueSettling(true) == true) {
            ViewCompat.postInvalidateOnAnimation(this)
        }
    }

    private fun startSettlingAnimation(child: View, state: States) {
        val top: Int = when (state) {
            States.STATE_ANCHOR -> -anchorRange
            States.STATE_CLOSED -> 0
            States.STATE_OPEN -> -draggingRange
        }
        if (dragHelper?.smoothSlideViewTo(child, child.left, top) == true) {
            ViewCompat.postOnAnimation(child, SettleRunnable(child))
        }
        currentState = state
    }

    fun setState(state: States) {
        if (parent != null && parent.isLayoutRequested && isAttachedToWindow) {
            post { startSettlingAnimation(getChildAt(0), state) }
        } else {
            startSettlingAnimation(getChildAt(0), state)
        }
    }

    enum class States {
        STATE_OPEN, STATE_ANCHOR, STATE_CLOSED
    }

    private inner class SettleRunnable(private val view: View) : Runnable {

        override fun run() {
            if (dragHelper?.continueSettling(true) == true) {
                ViewCompat.postOnAnimation(view, this)
            }
        }
    }
}
