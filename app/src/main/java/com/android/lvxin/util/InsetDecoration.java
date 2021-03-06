package com.android.lvxin.util;

import android.content.Context;
import android.graphics.Rect;
import android.support.v7.widget.RecyclerView;
import android.view.View;

/**
 * Created by muqing on 11/18/15.
 */
public class InsetDecoration extends RecyclerView.ItemDecoration {
    private int mInsets;

    public InsetDecoration(Context context, int insets) {
        mInsets = insets;
    }

    @Override
    public void getItemOffsets(Rect outrect, View view, RecyclerView parent, RecyclerView.State state) {
        outrect.set(mInsets, mInsets , mInsets, mInsets);
    }
}

