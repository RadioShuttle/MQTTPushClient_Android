/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient.dash;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.format.DateUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import de.radioshuttle.mqttpushclient.PushAccount;
import de.radioshuttle.mqttpushclient.R;
import de.radioshuttle.net.Cmd;
import de.radioshuttle.net.PublishRequest;
import de.radioshuttle.net.Request;
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
                mViewModelPublish = ViewModelProviders.of(this).get(DetailViewDialog.PublishViewModel.class);

                mAutofillDisabled = (savedInstanceState == null ? false : savedInstanceState.getBoolean(KEY_AUTOFILL_DISABLED, false));

                DashBoardViewModel.ItemContext itemContext = mViewModel.getItem(args.getInt("id"));
                if (itemContext != null) {
                    mItem = itemContext.item;

                    inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    root = (ViewGroup) inflater.inflate(R.layout.dialog_detail_view, null);
                    View view = null;
                    ViewStub viewStub = root.findViewById(R.id.viewStub);
                    ImageButton refreshButton = root.findViewById(R.id.refreshButton);

                    mDefaultBackground = ContextCompat.getColor(getActivity(), R.color.dashboad_item_background);

                    /* calc size of dash item*/
                    float defSizeDPI = 250f; //TODO: default size?
                    DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
                    Log.d(TAG, "metrics " + displayMetrics.widthPixels / displayMetrics.density);

                    int a = Math.min(displayMetrics.heightPixels, displayMetrics.widthPixels);
                    int w = (int) (defSizeDPI * getResources().getDisplayMetrics().density);
                    int w2 = (int) ((float) a * .9f - 64f * getResources().getDisplayMetrics().density);

                    mPublishEnabled = !Utils.isEmpty(mItem.topic_p) || !Utils.isEmpty(mItem.script_p);

                    // treat optionlist with no items as text
                    if (mItem instanceof TextItem || (mItem instanceof OptionList && ((OptionList) mItem).optionList.isEmpty())){
                        viewStub.setLayoutResource(R.layout.activity_dash_board_item_text);
                        view = viewStub.inflate();

                        mTextContent = view.findViewById(R.id.textContent);
                        mContentContainer = mTextContent;
                        mLabel = view.findViewById(R.id.name);

                        mDefaultTextColor = mLabel.getTextColors().getDefaultColor();

                        if (mPublishEnabled) {
                            /* tint send button and value editor */
                            ImageButton sendButton = view.findViewById(R.id.sendButton);
                            sendButton.setVisibility(View.VISIBLE);

                            int color;
                            if (mItem.textcolor == DColor.OS_DEFAULT || mItem.textcolor == DColor.CLEAR) {
                                color = mDefaultTextColor;
                            } else {
                                color = (int) mItem.textcolor;
                            }
                            ColorStateList csl = ColorStateList.valueOf(color);
                            ImageViewCompat.setImageTintList(sendButton, csl);

                            mTextViewEditText = view.findViewById(R.id.editValue);
                            if (((TextItem) mItem).inputtype == TextItem.TYPE_NUMBER) {
                                /* set numeric keyboard */
                                mTextViewEditText.setInputType(InputType.TYPE_CLASS_NUMBER|
                                        InputType.TYPE_NUMBER_FLAG_SIGNED|InputType.TYPE_NUMBER_FLAG_DECIMAL);
                            }

                            mTextViewEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                                @Override
                                public void onFocusChange(View v, boolean hasFocus) {
                                    Log.d(TAG, "focus changed: " + hasFocus);
                                    /* user touces control*/
                                    if (hasFocus) {
                                        mAutofillDisabled = true;
                                    }
                                }
                            });

                            mTextViewEditText.setVisibility(View.VISIBLE);
                            mTextViewEditText.setTextColor(color);

                            sendButton.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    try {
                                        performSend(mTextViewEditText != null ? mTextViewEditText.getText().toString().getBytes("UTF-8") : null, false);
                                    } catch (UnsupportedEncodingException e) {}
                                }
                            });
                        }
                    } else if (mItem instanceof ProgressItem) {
                        viewStub.setLayoutResource(R.layout.activity_dash_board_item_progress);
                        view = viewStub.inflate();

                        mContentContainer = view.findViewById(R.id.progressBarContent);
                        mItemProgressBar = view.findViewById(R.id.itemProgressBar);

                        mSeekBar = view.findViewById(R.id.itemSeekBar);

                        mTextContent = view.findViewById(R.id.textContent);
                        mLabel = view.findViewById(R.id.name);
                        mDefaultTextColor = mLabel.getTextColors().getDefaultColor();
                        mDefaultProgressColor = DColor.fetchAccentColor(getContext());

                        if (mPublishEnabled) {
                            mProgressFormatter = NumberFormat.getInstance();
                            mProgressFormatter.setMinimumFractionDigits(((ProgressItem) mItem).decimal);
                            mProgressFormatter.setMaximumFractionDigits(((ProgressItem) mItem).decimal);
                            mProgressFormatterUS = NumberFormat.getInstance(Locale.US);
                            mProgressFormatterUS.setMinimumFractionDigits(((ProgressItem) mItem).decimal);
                            mProgressFormatterUS.setMaximumFractionDigits(((ProgressItem) mItem).decimal);
                            mItemProgressBar.setVisibility(View.GONE);
                            mSeekBar.setVisibility(View.VISIBLE);
                            mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                                    updateProgressView(progress);
                                    if (fromUser) {
                                        performSendForProgressItem(progress);
                                    }
                                }
                                public void onStartTrackingTouch(SeekBar seekBar) {
                                    Log.d(TAG, "focus changed: " + true);
                                    mAutofillDisabled = true;
                                    mSeekbarPressed = true;
                                }
                                public void onStopTrackingTouch(SeekBar seekBar) {
                                    mSeekbarPressed = false;
                                    if (mCurrentPublishID == -1 && mViewModelPublish.lastCompletedRequest != null) {
                                        PushAccount b = mViewModelPublish.lastCompletedRequest.getAccount();
                                        if (b.requestStatus == Cmd.RC_OK && mViewModelPublish.lastCompletedRequest.requestStatus == Cmd.RC_OK) {
                                            mViewModel.onMessagePublished(mViewModelPublish.lastCompletedRequest.getMessage());
                                        }
                                    }
                                }
                            });
                        } else {
                            /* tint progress bar */
                        }

                    } else if (mItem instanceof Switch) {
                        viewStub.setLayoutResource(R.layout.activity_dash_board_item_switch);
                        view = viewStub.inflate();

                        mContentContainer = view.findViewById(R.id.switchContainer);
                        int padding = (int) (40d * getResources().getDisplayMetrics().density);
                        mContentContainer.setPadding(padding, padding, padding, padding);

                        mLabel = view.findViewById(R.id.name);
                        mDefaultTextColor = mLabel.getTextColors().getDefaultColor();
                        mDefaultButtonBackground = DColor.fetchColor(getContext(), R.attr.colorButtonNormal);
                        mSwitchButton = view.findViewById(R.id.toggleButton);
                        mDefaultButtonTintColor = ContextCompat.getColor(getContext(), R.color.button_tint_default);

                        mSwitchImageButton = view.findViewById(R.id.toggleImageButton);
                        Switch sw = (Switch) mItem;

                        mSwitchButton.setClickable(true);
                        mSwitchButton.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                onButtonClicked();
                            }
                        });
                        mSwitchImageButton.setClickable(true);
                        mSwitchImageButton.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                onButtonClicked();
                            }
                        });
                        // mContentContainer = view.findViewById()
                    } else if (mItem instanceof OptionList) {
                        /* if not publish enabled, text view is user (see above) */
                        OptionList ol = (OptionList) mItem;
                        viewStub.setLayoutResource(R.layout.activity_dash_board_item_optionlist);
                        view = viewStub.inflate();

                        mLabel = view.findViewById(R.id.name);
                        mDefaultTextColor = mLabel.getTextColors().getDefaultColor();
                        mOptionListRecyclerView = view.findViewById(R.id.optionList);

                        /* init list view*/
                        mOptionListRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));

                        mContentContainer = mOptionListRecyclerView;

                        long color = mItem.getTextcolor();
                        int dcolor;
                        if (color == DColor.OS_DEFAULT || color == DColor.CLEAR) {
                            dcolor = mDefaultTextColor;
                        } else {
                            dcolor = (int) color;
                        }
                        ColorDrawable divider = new ColorDrawable(dcolor);

                        DividerItemDecoration decoration = new DividerItemDecoration(getContext(), DividerItemDecoration.VERTICAL);
                        decoration.setDrawable(divider);
                        mOptionListRecyclerView.addItemDecoration(decoration);

                        OptionListAdapter adapter = new OptionListAdapter(getContext(), dcolor, dcolor);
                        LinkedList<OptionList.Option> optionList = new LinkedList<>();
                        if (ol.optionList != null && ol.optionList.size() > 0) {
                            optionList.addAll(ol.optionList);
                        }
                        if (mPublishEnabled) {
                            adapter.setCallback(new OptionListAdapter.Callback() {
                                @Override
                                public void onOptionClicked(OptionList.Option o, boolean isSelected) {
                                    /* if an option is already selected, a click does not deselect */
                                    if (!isSelected) {
                                        byte[] content = null;
                                        if (o != null && o.value != null) {
                                            try {
                                                content = o.value.getBytes("UTF-8");
                                            } catch (UnsupportedEncodingException e) {
                                            }
                                        }
                                        if (content == null) {
                                            content = new byte[0];
                                        }
                                        performSend(content, false);
                                    }
                                }
                            });
                        }

                        adapter.setData(ol.optionList);
                        mOptionListRecyclerView.setAdapter(adapter);
                        if (savedInstanceState == null) {
                            int pos = ol.getSelectedPosition();
                            if (pos != -1) {
                                mOptionListRecyclerView.scrollToPosition(pos);
                            }
                        }

                    } else if (mItem instanceof CustomItem) {
                        final CustomItem citem = (CustomItem) mItem;
                        viewStub.setLayoutResource(R.layout.activity_dash_board_item_custom);
                        view = viewStub.inflate();

                        mContentContainer = view.findViewById(R.id.webContent);
                        mItemProgressBar = view.findViewById(R.id.webProgressBar);
                        mLabel = view.findViewById(R.id.name);
                        mDefaultTextColor = mLabel.getTextColors().getDefaultColor();

                        long xcolor = citem.getTextcolor();
                        int color;
                        if (xcolor == DColor.OS_DEFAULT || xcolor == DColor.CLEAR) { // clear is inavalid, treat as DEFAULT
                            color = mDefaultTextColor;
                        } else {
                            color = (int) xcolor;
                        }

                        if (Build.VERSION.SDK_INT >= 21) {
                            ColorStateList pt = mItemProgressBar.getProgressTintList();
                            if (pt == null || pt.getDefaultColor() != color) {
                                mItemProgressBar.setIndeterminateTintList(ColorStateList.valueOf(color));
                            }
                        } else {
                            Drawable d = mItemProgressBar.getIndeterminateDrawable();
                            if (d != null) {
                                d.setColorFilter(color, PorterDuff.Mode.SRC_IN);
                            }
                        }


                        final WebView webView = (WebView) mContentContainer;
                        webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
                        webView.getSettings().setJavaScriptEnabled(true);
                        // webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
                        CustomItem.JSObject webInterface = citem.getWebInterface();
                        webInterface.setViewModel(mViewModel);
                        webView.addJavascriptInterface(webInterface, "MQTT");
                        webView.setWebChromeClient(new WebChromeClient() {
                            @Override
                            public void onProgressChanged(WebView view, int newProgress) {
                                super.onProgressChanged(view, newProgress);
                                if (newProgress < 100 && mItemProgressBar.getVisibility() != View.VISIBLE) {
                                    mItemProgressBar.setVisibility(View.VISIBLE);
                                } else if (newProgress == 100 && mItemProgressBar.getVisibility() != View.GONE) {
                                    // do not hide progress bar if publish request is running
                                    if (citem.getWebInterface().currentPublishRequest.get() == 0) {
                                        mItemProgressBar.setVisibility(View.GONE);
                                    }
                                }
                            }
                            @Override
                            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                                String err = "" + consoleMessage.message() + ", line: " + consoleMessage.lineNumber();
                                handleWebViewError(err, citem);
                                return true;
                            }
                        });
                        webView.setWebViewClient(new WebViewClient() {
                            @Override
                            public void onPageFinished(WebView view, String url) {
                                super.onPageFinished(view, url);
                                mWebViewIsLoading = false;
                                StringBuilder js = new StringBuilder();
                                if (Build.VERSION.SDK_INT < 19) {
                                    js.append("javascript:");
                                }
                                Log.d(TAG, "onPageFinished");
                                js.append(wrapper_webview_js);
                                js.append(' ');
                                js.append(javascript_color_js);

                                js.append(CustomItem.build_onMqttPushClientInitCall(mViewModel.getPushAccount(), citem, true));
                                if (citem.hasMessageData()) {
                                    js.append(CustomItem.build_onMqttMessageCall(citem));
                                }
                                Log.d(TAG, "detail view on page finished: "); //TODO: remove

                                if (Build.VERSION.SDK_INT >= 19) {
                                    Log.d(TAG, js.toString());
                                    webView.evaluateJavascript(js.toString(), null);
                                } else {
                                    webView.loadUrl(js.toString());
                                }

                            }
                            @TargetApi(23)
                            @Override
                            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                                /* check if error already reported */
                                String err = error.getDescription().toString();
                                handleWebViewError(err, citem);
                            }

                            @Override
                            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                                handleWebViewError(description, citem);
                            }

                            @TargetApi(21)
                            @Nullable
                            @Override
                            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                                // Log.d(TAG, "shouldInterceptRequest 1: " + request.getUrl().toString());
                                return ImageResource.handleWebResource(getContext(), request.getUrl());
                            }

                            @Nullable
                            @Override
                            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                                // Log.d(TAG, "shouldInterceptRequest 2: " + url);
                                WebResourceResponse r = null;
                                try {
                                    r = ImageResource.handleWebResource(getContext(), Uri.parse(url));
                                } catch(Exception e) {}
                                return r;
                            }
                        });

                        //TOOO: consider enabliing refresh button for custom view (reloads html page)
                        /*
                        if (refreshButton != null) {
                            refreshButton.setVisibility(View.VISIBLE);
                            refreshButton.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    webView.reload();
                                    // reload dashboard item instance too:
                                    Bundle args = getArguments();
                                    DashBoardViewModel.ItemContext itemContext = mViewModel.getItem(args.getInt("id"));
                                    if (itemContext != null && itemContext.item instanceof CustomItem) {
                                        // we do not know antything about the adapters webview here, but the adapter
                                        // can be notified to refresh its content
                                        CustomItem customItem = (CustomItem) itemContext.item;
                                        if (!customItem.reloadRequested) {
                                            customItem.reloadRequested = true;
                                            customItem.notifyDataChanged();
                                        }
                                    }
                                }
                            });
                        }
                        */

                        if (savedInstanceState == null) {
                            // loading is done in updateView
                            mWebViewIsLoading = false;
                        } else {
                            mWebViewIsLoading = savedInstanceState.getBoolean(KEY_WEBVIEW_ISLOADING);
                            mWebViewHTML = savedInstanceState.getString(KEY_WEBVIEW_HTML);
                            webView.restoreState(savedInstanceState);
                            // webView.reload();
                        }
                        try {
                            wrapper_webview_js = Utils.getRawStringResource(getContext(), "javascript_wrapper_webview", true);
                            javascript_color_js = Utils.getRawStringResource(getContext(), "javascript_color", true);

                        } catch (IOException e) {
                            Log.d(TAG, "Error loading raw resource: custom_view_js", e);
                        }
                        ((DashConstraintLayout) view).setInterceptTouchEvent(false);

                    } else { // unknown or deprecated type

                    }

                    ViewGroup.LayoutParams lp = mContentContainer.getLayoutParams();
                    lp.width = Math.min(w, w2);
                    lp.height = lp.width;
                    mContentContainer.setLayoutParams(lp);

                    if (view != null) {

                        mViewModel.mDashBoardItemsLiveData.observe(this, new Observer<List<Item>>() {
                            @Override
                            public void onChanged(List<Item> items) {
                                // Log.d(TAG, "item data updated.");
                                updateView(); //TODO: check mItem - it might have updated by other client!
                            }
                        });

                        final long liveDataSince = System.currentTimeMillis();
                        mItem.liveData.observe(this, new Observer<Integer>() {
                            @Override
                            public void onChanged(Integer integer) {
                                if (mItem.liveDataTimestamp >= liveDataSince) {
                                    updateView();
                                }
                            }
                        });

                        /* tint buttons */
                        ImageButton closeButton = root.findViewById(R.id.closeButton);
                        ColorStateList csl = ColorStateList.valueOf(mDefaultTextColor);
                        ImageViewCompat.setImageTintList(closeButton, csl);
                        closeButton.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                dismiss();
                            }
                        });

                        if (refreshButton != null) {
                            ImageViewCompat.setImageTintList(refreshButton, csl);
                        }

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
                        lp = mErrorContent.getLayoutParams();
                        lp.width = Math.min(w, w2);
                        lp.height = lp.width;
                        mErrorContent.setLayoutParams(lp);

                        mProgressBar = root.findViewById(R.id.progressBar);
                        if (mProgressBar != null) {
                            if (Build.VERSION.SDK_INT >= 21) {
                                mProgressBar.setIndeterminateTintMode(PorterDuff.Mode.SRC_ATOP);
                                mProgressBar.setIndeterminateTintList(csl);
                            } else {
                                Drawable drawable = mProgressBar.getIndeterminateDrawable();
                                if (drawable != null) {
                                    drawable.setColorFilter(csl.getDefaultColor(), PorterDuff.Mode.SRC_ATOP);
                                }
                            }
                        }

                        mCurrentView = view;
                        updateView();

                        if (savedInstanceState != null) {
                            mCurrentPublishID = savedInstanceState.getLong(KEY_PUBLISH_ID, -1L);
                            if (mCurrentPublishID > 0) {
                                mProgressBar.setVisibility(View.VISIBLE);
                            }
                        }

                        mViewModel.mPublishRequest.observe(this, new Observer<Request>() {
                            @Override
                            public void onChanged(Request request) {
                                if (request instanceof PublishRequest) {
                                    onPublish((PublishRequest) request);
                                }
                            }
                        });

                    }
                }
            }
        }

        return root;
    }

    protected void handleWebViewError(String err, CustomItem item) {
        if (err == null) {
            err = "";
        }
        if (item != null && !Utils.equals(err, item.data.get("error"))) {
            item.data.put("error", err);
            item.notifyDataChanged();
        }
    }

    protected void performSend(byte[] value, boolean queueRequest) {
        if (mCurrentPublishID > 0) {
            if (!queueRequest) {
                Toast t = Toast.makeText(getContext(), getString(R.string.op_in_progress), Toast.LENGTH_LONG);
                t.show();
            } else {
                mViewModelPublish.queue = value;
            }
        } else {
            //TODO: consider ignoring empty values
            mProgressBar.setVisibility(View.VISIBLE);
            mCurrentPublishID = mViewModel.publish(mItem.topic_p, value, mItem.retain, mItem);
        }
    }

    protected void performSendForProgressItem(int progress) {
        try {
            ProgressItem pItem = (ProgressItem) mItem;
            double f = (double) progress / (double) mSeekBar.getMax();
            double value = (pItem.range_max - pItem.range_min) * f + pItem.range_min;
            performSend(mProgressFormatterUS.format(value).getBytes("UTF-8"), true); // format with "." decimal separator
        } catch (Exception e) {
            Log.e(TAG, "Format error (progress item)", e);
        }
    }


    protected void onPublish(PublishRequest request) {
        if (request != null && mItem != null) {
            if (request.getItemID() == mItem.id) {
                if (mItem instanceof ProgressItem) {
                    if (request.hasCompleted()) {
                        if (!mSeekbarPressed) {
                            mViewModelPublish.lastCompletedRequest = null;
                            PushAccount b = request.getAccount();
                            if (b.requestStatus == Cmd.RC_OK && request.requestStatus == Cmd.RC_OK) {
                                mViewModel.onMessagePublished(request.getMessage());
                            }
                        } else {
                            mViewModelPublish.lastCompletedRequest = request; // will be used if seek bar released to update view
                        }
                    }
                }
                if (mCurrentPublishID == request.getmPublishID()) {
                    if (!request.hasCompleted()) {
                        mProgressBar.setVisibility(View.VISIBLE);
                    } else {
                        if (mViewModelPublish.queue != null) {
                            byte[] lastSetValue = mViewModelPublish.queue;
                            mViewModelPublish.queue = null;
                            mCurrentPublishID = mViewModel.publish(mItem.topic_p, lastSetValue, mItem.retain, mItem);
                            return;
                        }
                        mProgressBar.setVisibility(View.GONE);
                        DashBoardViewModel.ItemContext ic = mViewModel.getItem(request.getItemID());
                        Toast t;
                        if (mItem instanceof ProgressItem) {
                            if (mItem.data.get("error") instanceof String && mErrorButton != null && mErrorButton.getVisibility() != View.VISIBLE) {
                                updateView();
                            } else if (mItem.data.get("error2") instanceof String && mErrorButton2 != null && mErrorButton2.getVisibility() != View.VISIBLE) {
                                updateView();
                            }
                        }
                        if (ic != null && ic.item != null && !Utils.isEmpty((String) ic.item.data.get("error2"))) {
                            t = Toast.makeText(getContext(), getString(R.string.errormsg_general_error), Toast.LENGTH_LONG);
                            t.show();
                        } else {
                            /* show sent confirmation message */
                            // t = Toast.makeText(getContext(), getString(R.string.dlg_info_message_published), Toast.LENGTH_LONG);
                            // t.show();
                        }
                        mCurrentPublishID = -1;
                    }
                } else if (mItem instanceof CustomItem) {
                    CustomItem cItem = (CustomItem) mItem;
                    if (!request.hasCompleted()) {
                        //TODO: show progress
                        if (mItemProgressBar.getVisibility() != View.VISIBLE) {
                            mItemProgressBar.setVisibility(View.VISIBLE);

                        }
                    } else { // request completed
                        if (mItemProgressBar.getVisibility() != View.GONE && !mWebViewIsLoading) {
                            mItemProgressBar.setVisibility(View.GONE);
                        }
                    }
                }

            }
        }
    }

    protected void updateProgressView(int progress) {
        ProgressItem pItem = (ProgressItem) mItem;
        double f = (double) progress / (double) mSeekBar.getMax();
        double value;
        double valuePC;
        if (pItem.percent) {
            valuePC = f * 100d;
            mSeekBarFormattedValue = (int) Math.floor(valuePC + .5d) + "%";
        } else {
            value = (pItem.range_max - pItem.range_min) * f + pItem.range_min;
            mSeekBarFormattedValue = mProgressFormatter.format(value);
        }
        if (mTextContent != null) {
            mTextContent.setText(getContent());
        }
    }

    protected String getContent() {
        String content = (String) mItem.data.get("content");
        if (mItem instanceof ProgressItem) {
            if (mItem.data.get("content.progress") instanceof String) {
                content = (String) mItem.data.get("content.progress");
            }
            if (!Utils.isEmpty(mSeekBarFormattedValue)) {
                content += " / " + mSeekBarFormattedValue;
            }
        }
        return content;
    }

    protected void onButtonClicked() {
        if (mItem instanceof Switch) {
            Switch sw = (Switch) mItem;
            /* is switch? then toggle state */
            String t;
            if (!Utils.isEmpty(sw.valOff)) {
                if (sw.isOnState()) {
                    t = sw.valOff;
                } else {
                    t = sw.val;
                }
            } else {
                t = sw.val;
            }
            if (t == null) {
                t = "";
            }
            try {
                performSend(t.getBytes("UTF-8"), false);
            } catch (UnsupportedEncodingException e) {}
        }
    }

    protected void updateView() {

        /* if there is an error, show corresonding error button (if not already shown) */
        if (mItem.data.get("error") instanceof String) {
            if (mErrorButton.getVisibility() != View.VISIBLE) {
                mErrorButton.setVisibility(View.VISIBLE);
            }
        } else {
            /* no error, hide error button  */
            if (mErrorButton.getVisibility() != View.GONE) {
                mErrorButton.setVisibility(View.GONE);
            }
            if (mViewMode == VIEW_ERROR_1) {
                /* hide error view (this may happen if a new result comes in with no error) */
                mViewMode = VIEW_DASHITEM;
            }
        }

        if (mItem.data.get("error2") instanceof String) {
            if (mErrorButton2.getVisibility() != View.VISIBLE) {
                mErrorButton2.setVisibility(View.VISIBLE);
            }
        } else {
            if (mErrorButton2.getVisibility() != View.GONE) {
                mErrorButton2.setVisibility(View.GONE);
            }
            /* no error, hide error button  */
            if (mViewMode == VIEW_ERROR_2) {
                /* hide error view (this may happen if a new result comes in with no error) */
                mViewMode = VIEW_DASHITEM;
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

            /* container backgroud */
            if (mContentContainer != null) {
                mItem.setViewBackground(mContentContainer, mDefaultBackground, true);
            }

            /* text value appearance */
            if (mTextContent != null) {
                mItem.setViewTextAppearance(mTextContent, mLabel.getTextColors().getDefaultColor());
                String content = getContent();
                if (mItem instanceof OptionList) { // implies mPublishEnabled == false
                    /* if publish is not enabled, a text view is shown instad an options list */
                    String displayValue = ((OptionList) mItem).getDisplayValue();
                    if (displayValue != null) {
                        content = displayValue;
                    }
                }
                mTextContent.setText(content);
            }

            if (mItem instanceof TextItem) {
                if (!mAutofillDisabled && mTextViewEditText != null) {
                    mTextViewEditText.setText((String) mItem.data.get("text.default"));
                    // mTextViewEditText.requestFocus();
                    // mTextViewEditText.selectAll();
                }
            } else if (mItem instanceof ProgressItem) {
                if (mItemProgressBar != null && mSeekBar != null) {
                    ProgressItem p = (ProgressItem) mItem;
                    int value = 0;
                    /* if java script error, there is no valid data, set progress bar to 0 */
                    if (mItem.data.get("error") instanceof String) {
                    } else {
                        String val = (String) mItem.data.get("content");
                        if (!Utils.isEmpty(val)) {
                            try {
                                double v = Double.parseDouble(val);
                                if (p.range_min < p.range_max && v >= p.range_min && v <= p.range_max) {
                                    double f = ProgressItem.calcProgessInPercent(v, p.range_min, p.range_max) / 100d;
                                    value = (int) (Math.floor((double) mItemProgressBar.getMax() * f + .5d));
                                }
                            } catch (Exception e) {
                            }
                        }
                    }
                    ProgressBar pb = null;
                    if (mItemProgressBar.getVisibility() == View.VISIBLE) {
                        mItemProgressBar.setProgress(value);
                        pb = mItemProgressBar;
                    }
                    if (mSeekBar.getVisibility() == View.VISIBLE) {
                        if (!mAutofillDisabled) {
                            mSeekBar.setProgress(value);
                        }
                        pb = mSeekBar;
                    }

                    long pcolor = (p.data.get("ctrl_color") != null ? (Long) p.data.get("ctrl_color") : p.progresscolor);
                    pcolor = (pcolor == 0 ? mDefaultProgressColor : pcolor);
                    int color;
                    if (pcolor == DColor.OS_DEFAULT || pcolor == DColor.CLEAR) {
                        color = mDefaultProgressColor;
                    } else {
                        color = (int) pcolor;
                    }

                    if (Build.VERSION.SDK_INT >= 21) {
                        ColorStateList pt = pb.getProgressTintList();
                        if (pt == null || pt.getDefaultColor() != color) {
                            pb.setProgressTintList(ColorStateList.valueOf(color));
                            pb.setProgressBackgroundTintList(ColorStateList.valueOf(color));
                            if (pb instanceof SeekBar) {
                                ((SeekBar) pb).setThumbTintList(ColorStateList.valueOf(color));
                            }
                        }
                    } else {
                        Drawable d = pb.getProgressDrawable();
                        if (d != null) {
                            d.setColorFilter(color, PorterDuff.Mode.SRC_IN);
                            if (pb instanceof SeekBar) {
                                Drawable t = ((SeekBar) pb).getThumb();
                                if (t != null) {
                                    t.setColorFilter(color, PorterDuff.Mode.SRC_IN);
                                }
                            }
                        }
                    }
                }
            } else if (mItem instanceof Switch) {
                Switch sw = (Switch) mItem;

                /* if stateless, show onValue */
                String val = null;
                long fcolor;
                long bcolor;
                boolean noTint;
                boolean isOnState = sw.isOnState();

                Drawable icon;

                if (isOnState) {
                    val = sw.val;
                    fcolor = sw.data.containsKey("ctrl_color") ? (Long) sw.data.get("ctrl_color") : sw.color;
                    bcolor = sw.data.containsKey("ctrl_background") ? (Long) sw.data.get("ctrl_background") : sw.bgcolor;
                    icon = (Drawable) sw.data.get("ctrl_image_blob");
                    if (icon != null) {
                        icon = icon.getConstantState().newDrawable(getResources()); //TODO: consider caching newDrawable
                    } else {
                        icon = sw.imageDetail;
                    }
                    noTint = fcolor == DColor.CLEAR;
                } else {
                    val = sw.valOff;
                    fcolor = sw.data.containsKey("ctrl_color_off") ? (Long) sw.data.get("ctrl_color_off") : sw.colorOff;
                    bcolor = sw.data.containsKey("ctrl_background_off") ? (Long) sw.data.get("ctrl_background_off") : sw.bgcolorOff;
                    icon = (Drawable) sw.data.get("ctrl_image_off_blob");
                    if (icon != null) {
                        icon = icon.getConstantState().newDrawable(getResources()); //TODO: consider caching newDrawable
                    } else {
                        icon = sw.imageDetailOff;
                    }
                    noTint = fcolor == DColor.CLEAR;
                }
                ColorStateList csl;
                if (bcolor == DColor.OS_DEFAULT || bcolor == DColor.CLEAR) {
                    csl = ColorStateList.valueOf(mDefaultButtonBackground);
                } else {
                    csl = ColorStateList.valueOf((int) bcolor);
                }

                /* show button or image button */
                if (icon == null) {
                    if (mSwitchButton.getVisibility() != View.VISIBLE) {
                        mSwitchButton.setVisibility(View.VISIBLE);
                    }
                    if (mSwitchImageButton.getVisibility() != View.GONE) {
                        mSwitchImageButton.setVisibility(View.GONE);
                    }
                    /* if stateless, show onValue */
                    mSwitchButton.setText(val);
                    int color;
                    if (fcolor == DColor.OS_DEFAULT || fcolor == DColor.CLEAR) {
                        color = mDefaultButtonTintColor;
                    } else {
                        color = (int) fcolor;
                    }
                    mSwitchButton.setTextColor(color);
                    ViewCompat.setBackgroundTintList(mSwitchButton, csl);

                } else {
                    if (mSwitchButton.getVisibility() != View.GONE) {
                        mSwitchButton.setVisibility(View.GONE);
                    }
                    if (mSwitchImageButton.getVisibility() != View.VISIBLE) {
                        mSwitchImageButton.setVisibility(View.VISIBLE);
                    }
                    if (mSwitchImageButton.getDrawable() != icon) {
                        mSwitchImageButton.setImageDrawable(icon);
                    }

                    ViewCompat.setBackgroundTintList(mSwitchImageButton, csl);
                    int color;
                    if (fcolor == DColor.OS_DEFAULT) {
                        color = mDefaultButtonTintColor;
                    } else {
                        color = (int) fcolor;
                    }
                    ColorStateList tcsl = ColorStateList.valueOf(color);
                    if (noTint) {
                        ImageViewCompat.setImageTintList(mSwitchImageButton, null);
                    } else {
                        ImageViewCompat.setImageTintList(mSwitchImageButton, tcsl);
                    }
                }
            } else if (mItem instanceof CustomItem) {
                if (mContentContainer instanceof WebView) {
                    final WebView webView = (WebView) mContentContainer;
                    final CustomItem citem = (CustomItem) mItem;

                    if (!Utils.equals(mWebViewHTML, citem.getHtml())) { // load html, if not already done or changed
                        mWebViewHTML = citem.getHtml();
                        mWebViewIsLoading = true;
                        //TODO: check, Samsung 4.4 does not refresh if use  loadDataWithBaseURL (see old solution with loadData();
                        webView.loadDataWithBaseURL(CustomItem.BASE_URL, mWebViewHTML, "text/html", "utf-8", null);

                        /*
                        String encodedHtml = Base64.encodeToString(mWebViewHTML.getBytes(), Base64.NO_PADDING);
                        webView.loadData(encodedHtml, "text/html", "base64");
                        */

                    } else {
                        if (!mWebViewIsLoading && citem.hasMessageData()) {
                            String jsOnMqttMessageCall = CustomItem.build_onMqttMessageCall(citem);
                            if (Build.VERSION.SDK_INT >= 19) {
                                webView.evaluateJavascript(jsOnMqttMessageCall, null);
                            } else {
                                webView.loadUrl(jsOnMqttMessageCall);
                            }
                        }
                    }
                }
            } else if (mItem instanceof OptionList || mPublishEnabled) {
                if (mOptionListRecyclerView != null && mOptionListRecyclerView.getAdapter() instanceof OptionListAdapter) {
                    OptionListAdapter adapter = (OptionListAdapter) mOptionListRecyclerView.getAdapter();
                    adapter.setSelection(getContent());
                }

            } else {
                //TODO: continue here to set other dash item related data
            }

            if (mLabel != null) {
                mLabel.setText(mItem.label + (receivedDateStr != null ? " - " + receivedDateStr : ""));
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
                    if (mItem instanceof CustomItem || mItem.data.containsKey("error.item") || ((String) javaScriptError).equals(getString(R.string.error_image_not_found))) {
                        displayError = getString(R.string.dash_item_err) + " " + javaScriptError;
                    } else {
                        displayError = getString(R.string.javascript_err) + " " + javaScriptError;
                    }
                }
            } else if (mViewMode == VIEW_ERROR_2) {
                Object outputError = mItem.data.get("error2");
                if (outputError instanceof String) {
                    displayError = "" + outputError;
                }
            }

            // set default colors for error content
            mErrorContent.setBackgroundColor(mDefaultBackground);
            mErrorContent.setTextColor(mDefaultTextColor);

            /*
            mItem.setViewBackground(mErrorContent, mDefaultBackground, true);
            int color;
            if (mItem.textcolor == DColor.OS_DEFAULT || mItem.textcolor == DColor.CLEAR) {
                color = mDefaultTextColor;
            } else {
                color = (int) mItem.textcolor;
            }

            if (mItem.textcolor != 0) { //TODO: if transparent (=0) consider using system default
                mErrorContent.setTextColor(color);
            }
             */

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
        outState.putLong(KEY_PUBLISH_ID, mCurrentPublishID);
        outState.putBoolean(KEY_AUTOFILL_DISABLED, mAutofillDisabled);
        if (mContentContainer instanceof WebView) {
            ((WebView) mContentContainer).saveState(outState);
            outState.putBoolean(KEY_WEBVIEW_ISLOADING, mWebViewIsLoading);
            outState.putString(KEY_WEBVIEW_HTML, mWebViewHTML);
        }
    }

    protected static class PublishViewModel extends ViewModel {
        public PublishViewModel() {
            queue = null;
        }
        public byte[] queue;
        public PublishRequest lastCompletedRequest;
    }

    protected DashBoardViewModel mViewModel;

    protected View mCurrentView;
    protected TextView mLabel;
    protected int mDefaultBackground;
    protected int mDefaultButtonTintColor;
    protected int mDefaultButtonBackground;
    protected int mDefaultProgressColor;
    protected View mContentContainer;
    protected TextView mTextContent;
    protected int mDefaultTextColor;
    protected Item mItem;

    protected int mViewMode;
    protected TextView mErrorContent;
    protected TextView mErrorLabel;
    protected ImageButton mErrorButton;
    protected ImageButton mErrorButton2;
    protected ProgressBar mProgressBar;
    protected Button mSwitchButton;
    protected ImageButton mSwitchImageButton;
    protected RecyclerView mOptionListRecyclerView;

    protected long mCurrentPublishID;
    protected ProgressBar mItemProgressBar;
    protected SeekBar mSeekBar;
    protected NumberFormat mProgressFormatter;
    protected NumberFormat mProgressFormatterUS;
    protected String mSeekBarFormattedValue;
    protected boolean mSeekbarPressed;
    protected PublishViewModel mViewModelPublish;
    protected boolean mWebViewIsLoading;
    protected String mWebViewHTML;
    protected String wrapper_webview_js;
    protected String javascript_color_js;
    protected boolean mPublishEnabled;

    /* input controls */
    protected boolean mAutofillDisabled; // when user touches control, autofill (setting default value) is disabled until published
    protected EditText mTextViewEditText;

    protected DateFormat mFormatter = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault());;
    protected DateFormat mTimeFormatter = DateFormat.getTimeInstance(DateFormat.SHORT);

    protected final static int VIEW_DASHITEM = 0;
    protected final static int VIEW_ERROR_1 = 1;
    protected final static int VIEW_ERROR_2 = 2;
    protected final static String KEY_VIEW_MODE = "KEY_VIEW_MODE";
    protected final static String KEY_PUBLISH_ID = "KEY_PUBLISH_ID";
    protected final static String KEY_AUTOFILL_DISABLED = "KEY_AUTOFILL_DISABLED";
    protected final static String KEY_WEBVIEW_ISLOADING = "KEY_WEBVIEW_ISLOADING";
    protected final static String KEY_WEBVIEW_HTML = "KEY_WEBVIEW_HTML";

    private final static String TAG = DetailViewDialog.class.getSimpleName();
}
