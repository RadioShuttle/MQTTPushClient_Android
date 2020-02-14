/*
 * Copyright (c) 2018 HELIOS Software GmbH
 * 30827 Garbsen (Hannover) Germany
 * Licensed under the Apache License, Version 2.0
 */

package de.radioshuttle.mqttpushclient;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;

import de.radioshuttle.fcm.Notifications;
import de.radioshuttle.net.Cmd;
import de.radioshuttle.utils.FirebaseTokens;

public class AccountRecyclerViewAdapter extends RecyclerView.Adapter {

    public interface RowSelectionListener {
        void onItemSelected(int oldPos, int newPos);
        void onItemDeselected();
        void onItemClicked(PushAccount b);
    }


    public AccountRecyclerViewAdapter(AppCompatActivity activity, int selectedRow, RowSelectionListener listener) {
        pushAccounts = null;
        mSelectedRow = selectedRow;
        context = activity;
        mInflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        rowSelectionListener = listener;
        mMultiplePushServer = false;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

        View view = mInflater.inflate(R.layout.activity_account_row, parent, false);
        final ViewHolder holder = new ViewHolder(view);

        holder.pushServer = view.findViewById(R.id.push_server);
        holder.displayName = view.findViewById(R.id.displayName);
        holder.status = view.findViewById(R.id.status);
        holder.errorImage = view.findViewById(R.id.errorImage);
        holder.okImage = view.findViewById(R.id.okImage);
        holder.neutralImage = view.findViewById(R.id.neutralImage);
        holder.progressBar = view.findViewById(R.id.progressBar);
        holder.newMessages = view.findViewById(R.id.messageCnt);
        holder.warningImage = view.findViewById(R.id.warningImage);

        holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
            // Called when the user long-clicks on someView
            public boolean onLongClick(View view) {
                int pos = holder.getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    toggleSelection(pos);
                }
                return true;
            }
        });

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mSelectedRow == -1 && rowSelectionListener != null) {
                    int pos = holder.getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION && pushAccounts != null && pos >= 0 && pos < pushAccounts.size()) {
                        rowSelectionListener.onItemClicked(pushAccounts.get(pos));
                    }
                } else if (mSelectedRow >= 0 && rowSelectionListener != null) {
                    int pos = holder.getAdapterPosition();
                    toggleSelection(pos);
                }
            }
        });

        return holder;
    }

    private void toggleSelection(int pos) {
        int lastSelection = mSelectedRow;
        int newSelection;
        if (pos == mSelectedRow) {
            newSelection = -1;
        } else {
            newSelection = pos;
        }
        mSelectedRow = newSelection;
        notifyItemChanged(newSelection);
        if (lastSelection != -1) {
            notifyItemChanged(lastSelection);
        }

        if (rowSelectionListener != null) {
            if (newSelection == -1) {
                rowSelectionListener.onItemDeselected();
            } else {
                rowSelectionListener.onItemSelected(lastSelection, newSelection);
            }
        }
    }


    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ViewHolder vh = (ViewHolder) holder;

        PushAccount b = pushAccounts.get(position);
        if (mMultiplePushServer) {
            if (vh.pushServer.getVisibility() != View.VISIBLE)
                vh.pushServer.setVisibility(View.VISIBLE);
            vh.pushServer.setText(b.pushserver);
        } else if (vh.pushServer.getVisibility() != View.GONE) {
            vh.pushServer.setVisibility(View.GONE);
        }
        boolean accountHasToken = FirebaseTokens.getInstance(mInflater.getContext()).hasToken(b.getKey());

        vh.displayName.setText(b.getDisplayName());

        String t = (b.requestErrorTxt == null ? "" : b.requestErrorTxt);
        if (b.requestStatus == Cmd.RC_MQTT_ERROR || (b.requestStatus == Cmd.RC_NOT_AUTHORIZED && b.requestErrorCode != 0)) {
            t = context.getString(R.string.errormsg_mqtt_prefix) + " " + t;
        }

        vh.status.setText(t);

        if (b.status == 1 && vh.progressBar.getVisibility() != View.VISIBLE) {
            vh.progressBar.setVisibility(View.VISIBLE);
        }
        if (!(b.status == 1) && vh.progressBar.getVisibility() != View.GONE) {
            vh.progressBar.setVisibility(View.GONE);
        }

        if (b.status == -1 && vh.neutralImage.getVisibility() != View.INVISIBLE) {
            vh.neutralImage.setVisibility(View.INVISIBLE);
        }
        if (!(b.status == -1) && vh.neutralImage.getVisibility() != View.GONE) {
            vh.neutralImage.setVisibility(View.GONE);
        }

        if (b.status == 0 && b.requestStatus != 0 && vh.errorImage.getVisibility() != View.VISIBLE) {
            vh.errorImage.setVisibility(View.VISIBLE);
        }
        if (!(b.status == 0 && b.requestStatus != 0) && vh.errorImage.getVisibility() != View.GONE){
            vh.errorImage.setVisibility(View.GONE);
        }

        if (b.status == 0 && b.requestStatus == 0 && !accountHasToken && vh.warningImage.getVisibility() != View.VISIBLE) {
            vh.warningImage.setVisibility(View.VISIBLE);
        }
        if (!(b.status == 0 && b.requestStatus == 0 && !accountHasToken) && vh.warningImage.getVisibility() != View.GONE) {
            vh.warningImage.setVisibility(View.GONE);
        }

        if (b.status == 0 && b.requestStatus == 0 && accountHasToken && vh.okImage.getVisibility() != View.VISIBLE) {
            vh.okImage.setVisibility(View.VISIBLE);
        }
        if (!(b.status == 0 && b.requestStatus == 0 && accountHasToken) && vh.okImage.getVisibility() != View.GONE) {
            vh.okImage.setVisibility(View.GONE);
        }

        if (b.newMessages <= 0) {
            vh.newMessages.setText("");
        } else {
            vh.newMessages.setText("+" + String.valueOf(b.newMessages));
        }

        holder.itemView.setSelected(mSelectedRow == position);

        /*
         * Inflate a dummy webview. This will reduce creation time of subsequent webview
         * creations (up to 1s). Unfortunately this can only be done on the main UI
         * thread (and blocks everything pending), so the right time to do this is after
         * the last entry of the accountlist is shown to the user (so the chance
         * is minimal that the user will recognize any delay caused by this operation.)
         */
        if (!webviewInit && position == pushAccounts.size() -1) {
            webviewInit = true;
            Handler hander = new Handler(Looper.getMainLooper());
            hander.post(new Runnable() {
                @Override
                public void run() {
                    mDummyWebview = new WebView(mInflater.getContext());
                    // Log.d("Acc", "oncreat");
                }
            });
        }

        // TODO: set color according status
        // ImageViewCompat.setImageTintList(vh.circleImage, ColorStateList.valueOf(ContextCompat.getColor(context, R.color.green)));
    }

    private static boolean webviewInit = false;

    public void setData(ArrayList<PushAccount> pushAccounts) {
        this.pushAccounts = pushAccounts;
        //TODO: sort (if so, check selection by indices (viewModel , ...)
        if (mSelectedRow != -1 && pushAccounts != null && mSelectedRow >= pushAccounts.size()) {
            int old = mSelectedRow;
            mSelectedRow = pushAccounts.size() - 1;
            if (mSelectedRow == -1) {
                rowSelectionListener.onItemDeselected();
            } else {
                rowSelectionListener.onItemSelected(old, mSelectedRow);
            }
        }
        if (pushAccounts != null) {
            HashSet<String> pushServer = new HashSet<>();
            for(PushAccount b : pushAccounts) {
                if (b.pushserver != null && b.pushserver.trim().length() > 0) {
                    pushServer.add(b.pushserver.trim());
                }
            }
            mMultiplePushServer = pushServer.size() > 1;
        }
        readNoOfNewMessages(null);

        notifyDataSetChanged();
    }

    protected void readNoOfNewMessages(String key) {
        if (pushAccounts != null) {
            String name = null;
            for(PushAccount a : pushAccounts) {
                a.newMessages = Notifications.getNoOfNewMessages(context, a.pushserver, a.getMqttAccountName());
            }
        }
    }

    public int getSelectedRow() {
        return mSelectedRow;
    }
    public PushAccount getAccount(int idx) {
        PushAccount b = null;
        if (pushAccounts != null && idx >= 0 && idx < pushAccounts.size()) {
            b = pushAccounts.get(idx);
        }
        return b;
    }

    public void clearSelection() {
        if (mSelectedRow != -1) {
            int old = mSelectedRow;
            mSelectedRow = -1;
            if (rowSelectionListener != null)
                rowSelectionListener.onItemDeselected();
            notifyItemChanged(old);
        }
    }

    @Override
    public int getItemCount() {
        return pushAccounts == null ? 0 : pushAccounts.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public ViewHolder(View v) {
            super(v);
        }
        TextView pushServer;
        TextView displayName;
        TextView status;
        TextView newMessages;
        ImageView errorImage;
        ImageView okImage;
        ImageView neutralImage;
        ImageView warningImage;
        ProgressBar progressBar;
    }

    private WebView mDummyWebview;

    private boolean mMultiplePushServer;
    private RowSelectionListener rowSelectionListener;
    private int mSelectedRow;
    private Context context;
    private LayoutInflater mInflater;
    private ArrayList<PushAccount> pushAccounts;
}
