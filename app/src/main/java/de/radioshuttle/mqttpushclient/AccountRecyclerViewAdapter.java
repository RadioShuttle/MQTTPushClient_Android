/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2018 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;

import de.radioshuttle.net.Cmd;

public class AccountRecyclerViewAdapter extends RecyclerView.Adapter {

    public interface RowSelectionListener {
        void onItemSelected(int oldPos, int newPos);
        void onItemDeselected();
        void onItemClicked(PushAccount b);
    }


    public AccountRecyclerViewAdapter(AppCompatActivity activity, int selectedRow, RowSelectionListener listener) {
        pushAccounts = null;
        mSelectedRow = selectedRow;
        context = activity.getApplicationContext();
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

        vh.displayName.setText(b.getDisplayName());

        String t = (b.requestErrorTxt == null ? "" : b.requestErrorTxt);
        if (b.requestStatus == Cmd.RC_MQTT_ERROR || b.requestStatus == Cmd.RC_NOT_AUTHORIZED) {
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

        if (b.status == 0 && b.requestStatus == 0 && vh.okImage.getVisibility() != View.VISIBLE) {
            vh.okImage.setVisibility(View.VISIBLE);
        }
        if (!(b.status == 0 && b.requestStatus == 0) && vh.okImage.getVisibility() != View.GONE) {
            vh.okImage.setVisibility(View.GONE);
        }


        holder.itemView.setSelected(mSelectedRow == position);

        // TODO: set color according status
        // ImageViewCompat.setImageTintList(vh.circleImage, ColorStateList.valueOf(ContextCompat.getColor(context, R.color.green)));
    }

    public void setData(ArrayList<PushAccount> pushAccounts) {
        this.pushAccounts = pushAccounts;
        //TODO: sort (if so, check selection by indices (viewModel , ...)
        //TODO: show header in row if there are more than one pushserver
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

        notifyDataSetChanged();
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
        ImageView errorImage;
        ImageView okImage;
        ImageView neutralImage;
        ProgressBar progressBar;
    }

    private boolean mMultiplePushServer;
    private RowSelectionListener rowSelectionListener;
    private int mSelectedRow;
    private Context context;
    private LayoutInflater mInflater;
    private ArrayList<PushAccount> pushAccounts;
}
