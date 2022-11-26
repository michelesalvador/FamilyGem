package app.familygem;

import android.content.Context;
import android.text.Layout;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatTextView;

/**
 * TextView that shrink correctly the width even when it contains multiple lines.
 */
public class ElasticTextView extends AppCompatTextView {

    public ElasticTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int widthMode = MeasureSpec.getMode(widthSpec);
        if (widthMode == MeasureSpec.AT_MOST) {
            Layout layout = getLayout();
            if (layout != null) {
                int maxWidth = (int)Math.ceil(getMaxLineWidth(layout)) +
                        getCompoundPaddingLeft() + getCompoundPaddingRight();
                widthSpec = MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST);
            }
        }
        super.onMeasure(widthSpec, heightSpec);
    }

    private float getMaxLineWidth(Layout layout) {
        float max_width = 0.0f;
        int lines = layout.getLineCount();
        for (int i = 0; i < lines; i++) {
            if (layout.getLineWidth(i) > max_width) {
                max_width = layout.getLineWidth(i);
            }
        }
        return max_width;
    }
}
