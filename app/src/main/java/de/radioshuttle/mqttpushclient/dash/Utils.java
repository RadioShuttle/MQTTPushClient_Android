/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient.dash;

import android.content.Context;
import android.graphics.Rect;
import android.util.Log;
import android.view.View;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import de.radioshuttle.mqttpushclient.R;

public class Utils {

    public static class ItemDecoration extends RecyclerView.ItemDecoration {
        public ItemDecoration(Context context) {
            mSpacing  = context.getResources().getDimensionPixelSize(R.dimen.dashboard_spacing);
        }

        @Override
        public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            super.getItemOffsets(outRect, view, parent, state);
            outRect.top = mSpacing;
            outRect.left = mSpacing;
            outRect.right = mSpacing;
            outRect.bottom = mSpacing;
        }

        int mSpacing;
    }

    public static class SpanSizeLookup extends GridLayoutManager.SpanSizeLookup {
        public SpanSizeLookup(RecyclerView recyclerView) {
            mRecyclerView = recyclerView;
        }
        RecyclerView mRecyclerView;

        @Override
        public int getSpanSize(int position) {
            int spanSize = 1;
            if (mRecyclerView != null && mRecyclerView.getLayoutManager()instanceof GridLayoutManager) {
                int spanCount = ((GridLayoutManager) mRecyclerView.getLayoutManager()).getSpanCount();
                RecyclerView.Adapter a = mRecyclerView.getAdapter();
                if (a instanceof DashBoardAdapter) {
                    if (a.getItemViewType(position) == Item.TYPE_HEADER) {
                        spanSize = spanCount;
                    } else {
                        List<Item> list = ((DashBoardAdapter) a).getData();
                        if (list != null && position + 1 < list.size()) {
                            if (list.get(position).groupIdx != list.get(position + 1).groupIdx) {
                                int z = 1;
                                for(int i = position - 1; i >= 0 && list.get(i).groupIdx == list.get(position).groupIdx && list.get(i).getType() != Item.TYPE_HEADER; i--) {
                                    z++; //TODO: this can be calculated in viewModel when "building" adapter data
                                }
                                if (z % spanCount > 0) {
                                    spanSize = spanCount - (z % spanCount) + 1;
                                }
                                // Log.d(SpanSizeLookup.class.getSimpleName()+".SpanSizeLookup", "position: " + position + ", z: " + z + ", span size: " + spanSize);
                            }
                        }
                    }
                }
            }
            return spanSize;
        }
    }
}
