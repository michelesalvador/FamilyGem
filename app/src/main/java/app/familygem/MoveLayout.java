package app.familygem;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.VelocityTracker;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.OverScroller;

public class MoveLayout extends FrameLayout {

	private View child;
	final ScaleGestureDetector scaleDetector;
	private final OverScroller scroller;
	private VelocityTracker velocityTracker;
	int width, height;
	int childWidth, childHeight;
	private int lastX, lastY;
	private float downX, downY;
	private int overX, overY;
	private int mendX, mendY; // Position correction for the child with scaled size
	float scale = .7f;
	boolean scaling; // the screen has been touched with two fingers
	boolean virgin; // The screen has not been touched
	boolean leftToRight; // LTR (otherwise RTL)

	public MoveLayout(Context context, AttributeSet attributeSet) {
		super(context, attributeSet);
		ScaleGestureDetector.SimpleOnScaleGestureListener scaleListener = new ScaleGestureDetector.SimpleOnScaleGestureListener() {
			@Override
			public boolean onScale(ScaleGestureDetector scaleGestureDetector) {
				final float scaleFactor = scaleGestureDetector.getScaleFactor();
				float minimum = Math.min((float)width / childWidth, (float)height / childHeight);
				scale = Math.max(minimum, Math.min(scale * scaleFactor, 3));
				child.setScaleX(scale);
				child.setScaleY(scale);
				calcOverScroll(true);
				// Corrects scroll while scaling
				float distX;
				if( leftToRight )
					distX = childWidth / 2f - getScrollX() - scaleGestureDetector.getFocusX();
				else
					distX = width - (getScrollX() + childWidth / 2f) - scaleGestureDetector.getFocusX();
				distX -= distX * scaleFactor;
				float distY = childHeight / 2f - getScrollY() - scaleGestureDetector.getFocusY();
				distY -= distY * scaleFactor;
				scrollBy((int)distX, (int)distY);
				lastX = getScrollX();
				lastY = getScrollY();
				return true;
			}
		};
		scaleDetector = new ScaleGestureDetector(context, scaleListener);
		scroller = new OverScroller(context);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		width = MeasureSpec.getSize(widthMeasureSpec);
		height = MeasureSpec.getSize(heightMeasureSpec);
		setMeasuredDimension(width, height);

		child = getChildAt(0);
		child.setScaleX(scale);
		child.setScaleY(scale);

		// Measure the child as unspecified
		int spec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
		measureChildren(spec, spec);
	}

	// Intercept motion events also on children with click listener (person cards)
	@Override
	public boolean onInterceptTouchEvent(MotionEvent event) {
		switch( event.getActionMasked() ) {
			case MotionEvent.ACTION_DOWN:
				scroller.forceFinished(true);
				postInvalidateOnAnimation();
				calcOverScroll(true);
				lastX = getScrollX();
				lastY = getScrollY();
				downX = event.getX();
				downY = event.getY();
				if( velocityTracker == null )
					velocityTracker = VelocityTracker.obtain();
				else
					velocityTracker.clear();
				velocityTracker.addMovement(event);
				virgin = false;
				break;
			case MotionEvent.ACTION_POINTER_DOWN: // Second finger touch
				scaling = true;
				break;
			case MotionEvent.ACTION_MOVE:
				if( Math.abs(downX - event.getX()) > 10 || Math.abs(downY - event.getY()) > 10 ) {
					return true;
				}
		}
		return false;
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		scaleDetector.onTouchEvent(event);
		switch( event.getActionMasked() ) {
			case MotionEvent.ACTION_DOWN:
				return true;
			case MotionEvent.ACTION_POINTER_DOWN:
				scaling = true;
				return true;
			case MotionEvent.ACTION_MOVE:
				int scrollX = (int)(lastX + downX - event.getX());
				int scrollY = (int)(lastY + downY - event.getY());
				// Horizontal limits
				if( leftToRight ) {
					if( scrollX < mendX - overX ) // Left
						scrollX = mendX - overX;
					else if( scrollX > childWidth - width + overX - mendX ) // Right
						scrollX = childWidth - width + overX - mendX;
				} else { // RTL
					if( scrollX > overX - mendX ) // Right
						scrollX = overX - mendX;
					else if( scrollX < width - childWidth - overX + mendX ) // Left
						scrollX = width - childWidth - overX + mendX;
				}
				// Vertical limits
				if( scrollY < mendY - overY ) // Top
					scrollY = mendY - overY;
				else if( scrollY > childHeight - height + overY - mendY ) // Bottom
					scrollY = childHeight - height + overY - mendY;
				if( !scaling ) {
					scrollTo(scrollX, scrollY);
				}
				velocityTracker.addMovement(event);
				velocityTracker.computeCurrentVelocity(1000);
				return true;
			case MotionEvent.ACTION_UP:
				scaling = false;
				if( leftToRight ) {
					scroller.fling(getScrollX(), getScrollY(),
							(int)-velocityTracker.getXVelocity(), (int)-velocityTracker.getYVelocity(),
							mendX, childWidth - width - mendX,
							mendY, childHeight - height - mendY,
							overX, overY);
				} else {
					scroller.fling(getScrollX(), getScrollY(),
							(int)-velocityTracker.getXVelocity(), (int)-velocityTracker.getYVelocity(),
							width - childWidth + mendX, -mendX,
							mendY, childHeight - height - mendY,
							overX, overY);
				}
				postInvalidate(); //invalidate(); superfluo?
				//velocityTracker.recycle(); // Provoca IllegalStateException: Already in the pool!
				return false;
		}
		return super.onTouchEvent(event);
	}

	@Override
	public void computeScroll() {
		if( scroller.computeScrollOffset() ) {
			scrollTo(scroller.getCurrX(), scroller.getCurrY());
			invalidate();
		}
	}

	// Calculate overscroll and mend
	// @param centering Add to 'mendX' and to 'mendY' the space to center a small child inside moveLayout
	void calcOverScroll(boolean centering) {
		overX = (int)(width / 4 * scale);
		overY = (int)(height / 4 * scale);

		mendX = (int)(childWidth - childWidth * scale) / 2;
		if( centering && childWidth * scale < width )
			mendX -= (width - childWidth * scale) / 2;
		mendY = (int)(childHeight - childHeight * scale) / 2;
		if( centering && childHeight * scale < height )
			mendY -= (height - childHeight * scale) / 2;
	}

	float minimumScale() {
		if( childWidth * scale < width && childHeight * scale < height ) {
			scale = Math.min((float)width / childWidth, (float)height / childHeight);
			child.setScaleX(scale);
			child.setScaleY(scale);
		}
		return scale;
	}

	// Scroll to x and y
	void panTo(int x, int y) {
		calcOverScroll(false);
		// Remove eccessive space around
		if( childHeight * scale - y < height ) // There is space below
			y = (int)(childHeight * scale - height);
		if( y < 0 ) // There is space above
			y = Math.min(0, (int)(childHeight * scale - height) / 2);
		if( leftToRight ) {
			if( childWidth * scale - x < width ) // There is space on the right
				x = (int)(childWidth * scale - width);
			if( x < 0 ) // There is space on the left
				x = Math.min(0, (int)(childWidth * scale - width) / 2);
			scrollTo(x + mendX, y + mendY);
		} else { // RTL
			if( childWidth * scale + x < width ) // There is space on the left
				x = -(int)(childWidth * scale - width);
			if( x > 0 ) // There is space on the right
				x = Math.max(0, -(int)(childWidth * scale - width) / 2);
			scrollTo(x - mendX, y + mendY );
		}
	}

	// Adjust the child position while its size could change during the initial animation
	void keepPositionResizing() {
		if( child.getWidth() != childWidth ) {
			float initialMendX = (child.getWidth() - child.getWidth() * scale) / 2;
			float spaceLeft = getScrollX() - initialMendX + width / 2f;
			float shiftX = (childWidth * scale * spaceLeft) / (child.getWidth() * scale) - spaceLeft
					+ ((childWidth - childWidth * scale) / 2 - initialMendX);
			scroller.startScroll(getScrollX(), getScrollY(), (int)shiftX, 0, 0);
			lastX = getScrollX();
		}
	}

	void displayAll() {
		scale = 0;
		minimumScale();
		calcOverScroll(true);
		scrollTo(leftToRight ? mendX : -mendX, mendY);
	}
}
