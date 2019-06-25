/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient.dash;

import android.content.Context;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.Observer;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import de.radioshuttle.mqttpushclient.R;
import de.radioshuttle.utils.Utils;

public class DetailViewDialog extends DialogFragment {

    public static DetailViewDialog newInstance(Item item) {

        Bundle args = new Bundle();
        args.putInt("id", item.id);

        DetailViewDialog fragment = new DetailViewDialog();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setStyle(DialogFragment.STYLE_NO_TITLE, 0);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,  Bundle savedInstanceState) {
        ViewGroup root = null;

        if (getArguments() != null) {
            Bundle args = getArguments();
            if (getActivity() instanceof DashBoardActivity) {
                DashBoardViewModel viewModel = ((DashBoardActivity) getActivity()).mViewModel;

                DashBoardViewModel.ItemContext itemContext = viewModel.getItem(args.getInt("id"));
                if (itemContext != null) {
                    mItem = itemContext.item;

                    inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    root = (ViewGroup) inflater.inflate(R.layout.dialog_detail_view, null);
                    View view = null;

                    mDefaultBackground = ContextCompat.getColor(getActivity(), R.color.dashboad_item_background);

                    if (mItem instanceof TextItem){
                        view =  inflater.inflate(R.layout.activity_dash_board_item_text, null);

                        mTextContent = view.findViewById(R.id.textContent);
                        mLabel = view.findViewById(R.id.name);
                        mDefaultTextColor = mLabel.getTextColors().getDefaultColor();

                    }

                    viewModel.mDashBoardItemsLiveData.observe(this, new Observer<List<Item>>() {
                        @Override
                        public void onChanged(List<Item> items) {
                            Log.d(TAG, "item data updated.");
                            updateItemData();
                        }
                    });

                    updateItemData();

                    if (view != null) {
                        View bottom = root.findViewById(R.id.closeButton);

                        root.addView(view, 0);

                        ConstraintSet cs = new ConstraintSet();
                        cs.clone((ConstraintLayout) root);
                        cs.connect(bottom.getId(), ConstraintSet.TOP, view.getId(), ConstraintSet.BOTTOM);
                        cs.applyTo((ConstraintLayout) root);

                    }
                }
            }
        }

        return root;
    }

    protected void updateItemData() {

        String displayError = null;
        Object javaScriptError = mItem.data.get("error");
        if (javaScriptError instanceof String) {
            displayError = getString(R.string.javascript_err) + " " + javaScriptError;
        }

        Long when = (Long) mItem.data.get("msg.received");
        String receivedDateStr = null;
        if (when != null) {
            if (DateUtils.isToday(when)) {
                receivedDateStr = mTimeFormatter.format(new Date(when));
            } else {
                receivedDateStr = mFormatter.format(new Date(when));
            }
        }


        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();

        if (mTextContent != null) { //mItem instanceof TextItem
            float defSizeDPI = 250f; //TODO: default size?

            int a = Math.min(displayMetrics.heightPixels, displayMetrics.widthPixels);

            ViewGroup.LayoutParams lp = mTextContent.getLayoutParams();
            int w = (int) (defSizeDPI * getResources().getDisplayMetrics().density);
            int w2 = (int) ((float) a * .9f - 64f * getResources().getDisplayMetrics().density);
            lp.width = Math.min(w, w2);
            lp.height = lp.width;
            mTextContent.setLayoutParams(lp);
            mItem.setViewTextAppearance(mTextContent, mLabel.getTextColors().getDefaultColor());
            mItem.setViewBackground(mTextContent, mDefaultBackground);

            if (!Utils.isEmpty(displayError)) {
                mTextContent.setText(displayError); //TODO: consider displaying errors in own textfield
            } else {
                mTextContent.setText((String) mItem.data.get("content"));
            }

            mLabel.setText(mItem.label + (receivedDateStr != null ? " - " + receivedDateStr : ""));
        }

    }

    protected TextView mLabel;
    protected int mDefaultBackground;
    protected TextView mTextContent;
    protected int mDefaultTextColor;
    protected Item mItem;

    protected DateFormat mFormatter = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault());;
    protected DateFormat mTimeFormatter = DateFormat.getTimeInstance(DateFormat.SHORT);


    private final static String TAG = DetailViewDialog.class.getSimpleName();
}
