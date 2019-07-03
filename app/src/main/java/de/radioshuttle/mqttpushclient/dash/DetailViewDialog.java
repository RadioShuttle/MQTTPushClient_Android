/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient.dash;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.InputType;
import android.text.format.DateUtils;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.Observer;

import java.io.UnsupportedEncodingException;
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
                mViewModel = ((DashBoardActivity) getActivity()).mViewModel;

                DashBoardViewModel.ItemContext itemContext = mViewModel.getItem(args.getInt("id"));
                if (itemContext != null) {
                    mItem = itemContext.item;

                    inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    root = (ViewGroup) inflater.inflate(R.layout.dialog_detail_view, null);
                    View view = null;

                    mDefaultBackground = ContextCompat.getColor(getActivity(), R.color.dashboad_item_background);

                    /* calc size of dash item*/
                    float defSizeDPI = 250f; //TODO: default size?
                    DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
                    int a = Math.min(displayMetrics.heightPixels, displayMetrics.widthPixels);
                    int w = (int) (defSizeDPI * getResources().getDisplayMetrics().density);
                    int w2 = (int) ((float) a * .9f - 64f * getResources().getDisplayMetrics().density);

                    boolean publishEnabled = !Utils.isEmpty(mItem.topic_p) || !Utils.isEmpty(mItem.script_p);

                    if (mItem instanceof TextItem){
                        view =  inflater.inflate(R.layout.activity_dash_board_item_text, null);

                        mTextContent = view.findViewById(R.id.textContent);
                        mLabel = view.findViewById(R.id.name);

                        ViewGroup.LayoutParams lp = mTextContent.getLayoutParams();
                        lp.width = Math.min(w, w2);
                        lp.height = lp.width;
                        mTextContent.setLayoutParams(lp);

                        mDefaultTextColor = mLabel.getTextColors().getDefaultColor();

                        if (publishEnabled) {
                            /* tint send button and value editor */
                            ImageButton sendButton = view.findViewById(R.id.sendButton);
                            sendButton.setVisibility(View.VISIBLE);
                            ColorStateList csl = ColorStateList.valueOf(mItem.textcolor == 0 ? mDefaultTextColor : mItem.textcolor);
                            ImageViewCompat.setImageTintList(sendButton, csl);

                            final EditText editText = view.findViewById(R.id.editValue);
                            if (((TextItem) mItem).inputtype == TextItem.TYPE_NUMBER) {
                                /* set numeric keyboard */
                                editText.setInputType(InputType.TYPE_CLASS_NUMBER|
                                        InputType.TYPE_NUMBER_FLAG_SIGNED|InputType.TYPE_NUMBER_FLAG_DECIMAL);
                            }

                            editText.setVisibility(View.VISIBLE);
                            editText.setTextColor(mItem.textcolor == 0 ? mDefaultTextColor : mItem.textcolor);

                            sendButton.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    try {
                                        performSend(editText != null ? editText.getText().toString().getBytes("UTF-8") : null);
                                    } catch (UnsupportedEncodingException e) {}
                                }
                            });
                        }
                    }

                    if (view != null) {

                        mViewModel.mDashBoardItemsLiveData.observe(this, new Observer<List<Item>>() {
                            @Override
                            public void onChanged(List<Item> items) {
                                // Log.d(TAG, "item data updated.");
                                updateView();
                            }
                        });

                        /* tint buttons */
                        ImageButton closeButton = root.findViewById(R.id.closeButton);
                        ColorStateList csl = ColorStateList.valueOf(mItem.textcolor == 0 ? mDefaultTextColor : mItem.textcolor);
                        ImageViewCompat.setImageTintList(closeButton, csl);
                        closeButton.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                dismiss();
                            }
                        });

                        mErrorButton = root.findViewById(R.id.errorButton);
                        ImageViewCompat.setImageTintList(mErrorButton, csl);
                        mErrorButton.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                toogleViewMode(VIEW_ERROR_1);
                            }
                        });

                        mErrorButton2 = root.findViewById(R.id.errorButton2);
                        ImageViewCompat.setImageTintList(mErrorButton2, csl);
                        mErrorButton2.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                toogleViewMode(VIEW_ERROR_2);
                            }
                        });

                        if (savedInstanceState != null) {
                            if (mItem.data.get("error") instanceof String) {
                                mViewMode = savedInstanceState.getInt(KEY_VIEW_MODE, 0);
                            }
                        }

                        /* error view set size */
                        mErrorLabel = root.findViewById(R.id.errorLabel);
                        mErrorContent = root.findViewById(R.id.errorContent);
                        ViewGroup.LayoutParams lp = mErrorContent.getLayoutParams();
                        lp.width = Math.min(w, w2);
                        lp.height = lp.width;
                        mErrorContent.setLayoutParams(lp);

                        mCurrentView = view;
                        updateView();
                        root.addView(view, 0);
                        /*
                        ConstraintSet cs = new ConstraintSet();
                        cs.clone((ConstraintLayout) root);
                        cs.connect(bottom.getId(), ConstraintSet.TOP, view.getId(), ConstraintSet.BOTTOM);
                        cs.applyTo((ConstraintLayout) root);
                        */
                    }
                }
            }
        }

        return root;
    }

    protected void performSend(byte[] value) {
        //TODO: consider ignoring empty values
        mViewModel.publish(mItem.topic_p, value, mItem.retain, mItem);
    }

    protected void updateView() {

        /* if there is an error, show corresonding error button (if not already shown) */
        if (mItem.data.get("error") instanceof String) {
            if (mErrorButton.getVisibility() != View.VISIBLE) {
                mErrorButton.setVisibility(View.VISIBLE);
            }
        } else {
            /* no error, hide error button  */
            if (mViewMode == VIEW_ERROR_1) {
                if (mErrorButton.getVisibility() != View.GONE) {
                    /* hide error view (this may happen if a new result comes in with no error) */
                    mErrorButton.setVisibility(View.GONE);
                    mViewMode = VIEW_DASHITEM;
                }
            }
        }

        if (mItem.data.get("error2") instanceof String) {
            if (mErrorButton2.getVisibility() != View.VISIBLE) {
                mErrorButton2.setVisibility(View.VISIBLE);
            }
        } else {
            /* no error, hide error button  */
            if (mViewMode == VIEW_ERROR_2) {
                if (mErrorButton2.getVisibility() != View.GONE) {
                    /* hide error view (this may happen if a new result comes in with no error) */
                    mErrorButton2.setVisibility(View.GONE);
                    mViewMode = VIEW_DASHITEM;
                }
            }
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

        if (mViewMode == VIEW_DASHITEM) {
            /* make sure current attached dash item is visible and error view is gone */
            if (mErrorContent.getVisibility() != View.GONE) {
                mErrorContent.setVisibility(View.GONE);
                mErrorLabel.setVisibility(View.GONE);
            }
            if (mCurrentView.getVisibility() != View.VISIBLE) {
                mCurrentView.setVisibility(View.VISIBLE);
            }

            if (mTextContent != null) { //mItem instanceof TextItem
                mItem.setViewTextAppearance(mTextContent, mLabel.getTextColors().getDefaultColor());
                mItem.setViewBackground(mTextContent, mDefaultBackground);
                mTextContent.setText((String) mItem.data.get("content"));

                mLabel.setText(mItem.label + (receivedDateStr != null ? " - " + receivedDateStr : ""));
            } else {
                //TODO: continue here to set other dash item related data
            }
        } else {
            /* make sure current attached dash item is gone and error view is visible */
            if (mErrorContent.getVisibility() != View.VISIBLE) {
                mErrorContent.setVisibility(View.VISIBLE);
                mErrorLabel.setVisibility(View.VISIBLE);
            }
            if (mCurrentView.getVisibility() != View.GONE) {
                mCurrentView.setVisibility(View.GONE);
            }

            String displayError = null;
            if (mViewMode == VIEW_ERROR_1) {
                Object javaScriptError = mItem.data.get("error");
                if (javaScriptError instanceof String) {
                    displayError = getString(R.string.javascript_err) + " " + javaScriptError;
                }
            } else if (mViewMode == VIEW_ERROR_2) {
                Object outputError = mItem.data.get("error2");
                if (outputError instanceof String) {
                    displayError = "" + outputError;
                }
            }

            mItem.setViewBackground(mErrorContent, mDefaultBackground);
            if (mItem.textcolor != 0) {
                mErrorContent.setTextColor(mItem.textcolor);
            }

            mErrorContent.setText(displayError);
            mErrorLabel.setText(mItem.label + (receivedDateStr != null ? " - " + receivedDateStr : ""));
        }
    }

    protected void toogleViewMode(int state) {
        if (mViewMode == state) {
            mViewMode = 0;
        } else {
            mViewMode = state;
        }
        updateView();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_VIEW_MODE, mViewMode);
    }
    protected DashBoardViewModel mViewModel;

    protected View mCurrentView;
    protected TextView mLabel;
    protected int mDefaultBackground;
    protected TextView mTextContent;
    protected int mDefaultTextColor;
    protected Item mItem;

    protected int mViewMode;
    protected TextView mErrorContent;
    protected TextView mErrorLabel;
    protected ImageButton mErrorButton;
    protected ImageButton mErrorButton2;

    protected DateFormat mFormatter = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault());;
    protected DateFormat mTimeFormatter = DateFormat.getTimeInstance(DateFormat.SHORT);

    protected final static int VIEW_DASHITEM = 0;
    protected final static int VIEW_ERROR_1 = 1;
    protected final static int VIEW_ERROR_2 = 2;
    protected final static String KEY_VIEW_MODE = "KEY_VIEW_MODE";

    private final static String TAG = DetailViewDialog.class.getSimpleName();
}
