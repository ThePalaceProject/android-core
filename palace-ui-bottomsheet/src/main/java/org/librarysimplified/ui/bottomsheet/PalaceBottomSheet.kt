package org.librarysimplified.ui.bottomsheet

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.AttrRes
import org.slf4j.LoggerFactory
import kotlin.math.abs

class PalaceBottomSheet : FrameLayout, PalaceBottomSheetType {

  private var handleCloseString: String = ""
  private var handleOpenString: String = ""
  private val ignoreOpenListener = IgnoreOpenListener()

  private var openListener: PalaceBottomSheetType.SheetOpenListenerType =
    this.ignoreOpenListener

  private val logger =
    LoggerFactory.getLogger(PalaceBottomSheet::class.java)

  private lateinit var handle: View
  private lateinit var contentArea: ViewGroup
  private var isOpen = false

  constructor(
    context: Context
  ) : super(context) {
    this.initializeLayout(context)
  }

  constructor(
    context: Context,
    attrs: AttributeSet?
  ) : super(context, attrs) {
    this.initializeLayout(context)
  }

  constructor(
    context: Context,
    attrs: AttributeSet?,
    @AttrRes defStyleAttr: Int
  ) : super(context, attrs, defStyleAttr) {
    this.initializeLayout(context)
  }

  private fun initializeLayout(
    context: Context
  ) {
    View.inflate(context, R.layout.palace_bottom_sheet, this)
  }

  /**
   * A swipe gesture listener that allows users to open and close the drawer based on upwards
   * and downwards swipes on the drawer handle.
   */

  private class SwipeGestureListener(
    private val parent: PalaceBottomSheet
  ) : SimpleOnGestureListener() {
    private val swipeThreshold = 100
    private val swipeVelocityThreshold = 100

    override fun onFling(
      e1: MotionEvent?,
      e2: MotionEvent,
      velocityX: Float,
      velocityY: Float
    ): Boolean {
      if (e1 == null) {
        return false
      }

      val deltaX = e2.x - e1.x
      val deltaY = e2.y - e1.y
      val absDeltaY = Math.abs(deltaY)
      val absDeltaX = Math.abs(deltaX)

      if (absDeltaY > absDeltaX) {
        if (absDeltaY > this.swipeThreshold && abs(velocityY) > this.swipeVelocityThreshold) {
          if (deltaY > 0) {
            this.parent.drawerClose()
          } else {
            this.parent.drawerOpen()
          }
          return true
        }
      }
      return false
    }
  }

  override fun addView(
    child: View?,
    index: Int,
    params: ViewGroup.LayoutParams?
  ) {
    if (child == null) {
      return
    }

    /*
     * Android will add the bottom sheet's inflated view to itself. When it does this, we
     * need to intercept the view and set up the various sub views.
     */

    if (child.id == R.id.palaceBottomSheet) {
      super.addView(child, index, params)
      this.contentArea =
        child.findViewById(R.id.palaceBottomSheetContent)
      this.handle =
        child.findViewById(R.id.palaceBottomSheetHandle)

      val gestureDetector =
        GestureDetector(this.context, SwipeGestureListener(this))

      this.handle.setOnTouchListener { v, event -> gestureDetector.onTouchEvent(event) }
      this.handle.setOnClickListener {
        this.toggle()
      }
      return
    }

    /*
     * Otherwise, we assume the incoming view is a view that needs to be added to the content
     * area. It's pointless to even speculate on all of the ways that Android might screw this
     * up. You should generally assume, even in ancient and so-called "well tested" classes like
     * View, that Android will consistently violate its own API contracts and call whatever it
     * likes whenever it likes.
     */

    try {
      this.contentArea.addView(child, index, params)
    } catch (e: Throwable) {
      this.logger.debug("Failed to add view: ", e)
    }
  }

  override fun drawerSetHandleAccessibilityStrings(
    openHandle: Int,
    closeHandle: Int
  ) {
    this.handleOpenString =
      this.context.getString(openHandle)
    this.handleCloseString =
      this.context.getString(closeHandle)

    this.updateHandleAccessibilityStrings()
  }

  private fun updateHandleAccessibilityStrings() {
    if (this.isOpen()) {
      this.handle.contentDescription = this.handleCloseString
    } else {
      this.handle.contentDescription = this.handleOpenString
    }
  }

  override fun drawerOpen() {
    this.logger.debug("Opening...")
    this.isOpen = true
    this.updateHandleAccessibilityStrings()
    this.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO

    val animator = ValueAnimator.ofFloat(this.translationY, 0f)
    animator.duration = 100
    animator.addUpdateListener {
      val valueNow = animator.animatedValue as Float
      this.translationY = valueNow
      this.broadcastOpenState()
    }
    animator.start()
  }

  override fun drawerOpenInstantly() {
    this.logger.debug("Opening...")
    this.isOpen = true
    this.updateHandleAccessibilityStrings()
    this.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO

    this.translationY = 0.0f
    this.broadcastOpenState()
  }

  override fun onLayout(
    changed: Boolean,
    left: Int,
    top: Int,
    right: Int,
    bottom: Int
  ) {
    super.onLayout(changed, left, top, right, bottom)

    /*
     * The drawer won't learn its own height until the first layout pass. Therefore, if the
     * user has told the drawer that it must be open (or closed), then we need to instantly
     * apply either of these states when the view's size changes (in other words, when a layout
     * occurs).
     */

    if (this.isOpen()) {
      this.drawerOpenInstantly()
    } else {
      this.drawerCloseInstantly()
    }
  }

  override fun drawerClose() {
    this.logger.debug("Closing...")
    this.isOpen = false
    this.updateHandleAccessibilityStrings()
    this.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS

    val contentHeight = this.contentArea.height
    val contentHeightF = contentHeight.toFloat()
    val animator = ValueAnimator.ofFloat(0.0f, contentHeightF)
    animator.duration = 100
    animator.addUpdateListener {
      val valueNow = animator.animatedValue as Float
      this.translationY = valueNow
      this.broadcastOpenState()
    }
    animator.start()
  }

  override fun drawerCloseInstantly() {
    this.logger.debug("Closing...")
    this.isOpen = false
    this.updateHandleAccessibilityStrings()
    this.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS

    val contentHeight = this.contentArea.height
    this.translationY = contentHeight.toFloat()
    this.broadcastOpenState()
  }

  override fun isOpen(): Boolean {
    return this.isOpen
  }

  override fun setOpenListener(
    listener: PalaceBottomSheetType.SheetOpenListenerType?
  ) {
    if (listener == null) {
      this.openListener = this.ignoreOpenListener
    } else {
      this.openListener = listener
    }

    this.broadcastOpenState()
  }

  private fun broadcastOpenState() {
    val contentHeight = this.contentArea.height
    val contentHeightF = contentHeight.toFloat()
    val positionNow = this.translationY
    if (contentHeightF > 0.0) {
      val position = positionNow.toDouble() / contentHeightF.toDouble()
      this.openListener.onOpenChanged(1.0 - position)
    }
  }

  private class IgnoreOpenListener : PalaceBottomSheetType.SheetOpenListenerType {
    override fun onOpenChanged(state: Double) {
      // Nothing required.
    }
  }
}
