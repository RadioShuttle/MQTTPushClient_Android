/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient.dash;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProviders;
import de.radioshuttle.mqttpushclient.PushAccount;
import de.radioshuttle.mqttpushclient.R;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TableRow;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class DashBoardEditActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dash_board_edit);

        Bundle args = getIntent().getExtras();
        if (args != null) {
            mMode = args.getInt(ARG_MODE, MODE_ADD);
            String json = args.getString(ARG_ACCOUNT);
            String itemClassName = args.getString(ARG_TYPE);
            int itemID = args.getInt(ARG_ITEM_ID, -1);
            final int groupPos = args.getInt(ARG_GROUP_POS, -1);
            final int itemPos  = args.getInt(ARG_ITEM_POS, -1);
            mGroupSelInit = (savedInstanceState == null);
            mItemSelInit = (savedInstanceState == null);

            /* check arguemnts */
            if (!(json == null || itemClassName == null || (mMode == MODE_EDIT && itemID == -1))) {
                PushAccount b = null;
                try {
                    /* create viewModel */
                    b = PushAccount.createAccountFormJSON(new JSONObject(json));
                    mViewModel = ViewModelProviders.of(
                            this, new DashBoardViewModel.Factory(b, getApplication()))
                            .get(DashBoardViewModel.class);

                    ViewState vs = ViewState.getInstance(getApplication());
                    String account = b.getKey();

                    if (!mViewModel.isInitialized()) {
                        mViewModel.setItems(vs.getDashBoardContent(b.getKey()), vs.getDashBoardModificationDate(account));
                    }

                    mEditTextLabel = findViewById(R.id.dash_name);

                    if (mMode == MODE_EDIT) {
                        int idx = args.getInt(ARG_ITEM_ID, -1);
                        if (idx != -1) {
                            DashBoardViewModel.ItemContext ic = mViewModel.getItem(idx);
                            if (ic == null) {
                                throw new RuntimeException("Edit item not found");
                            }
                            mItem = ic.item;
                            mEditTextLabel.setText(mItem.label);
                        }
                    } else { // MODE_ADD
                        DashBoardViewModel.ItemContext ic = new DashBoardViewModel.ItemContext();
                        mItem = (Item) Class.forName(itemClassName).newInstance();
                    }

                    /* set data */
                    TableRow groupRow = findViewById(R.id.rowGroup);

                    /* group */
                    if (groupRow != null && mItem != null) {
                        if (mItem instanceof GroupItem) {
                            /* group selection is not required when item is a group */
                            groupRow.setVisibility(View.GONE);
                        } else {
                            mGroupSpinner = findViewById(R.id.dash_groupSpinner);
                            if (mGroupSpinner != null) {
                                ArrayList<String> groups = new ArrayList<>();
                                List<GroupItem> groupItems = mViewModel.getGroups();
                                for(int i = 0; i < groupItems.size(); i++) {
                                    groups.add(groupItems.get(i).label);
                                }
                                ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, groups);
                                a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                                mGroupSpinner.setOnItemSelectedListener(this);
                                mGroupSpinner.setAdapter(a);
                                if (mGroupSelInit && groupPos >= 0) {
                                    Log.d(TAG, "set selection group: " + groupPos);
                                    // mFirstInitStep = (itemPos < 0);
                                    mGroupSpinner.setSelection(groupPos);
                                }
                            }
                        }
                    }

                    /* position */
                    mPosSpinner = findViewById(R.id.dash_posSpinner);
                    if (mPosSpinner != null) {
                        if (mItem instanceof GroupItem) {
                            List<GroupItem> groupItems = mViewModel.getGroups();
                            initPosSpinner(groupItems);
                        } else {
                            /* items can only be added, if a group is selected */
                            List<GroupItem> groupItems = mViewModel.getGroups();
                            if (groupPos >= 0 && groupPos < groupItems.size()) {
                                initPosSpinner(mViewModel.getItems(groupItems.get(groupPos).id));
                            }
                        }
                        if (mItemSelInit && itemPos >= 0) {
                            Log.d(TAG, "set selection pos: " + itemPos);
                            mPosSpinner.setSelection(itemPos);
                        }
                    }

                    String title = "";
                    if (mMode == MODE_ADD) {
                        if (mItem instanceof GroupItem) {
                            title = getString(R.string.title_add_group);
                        } else if (mItem instanceof TextItem) {
                            title = getString(R.string.title_add_text);
                        }
                    } else { // mMode == MODE_EDIT
                        if (mItem instanceof GroupItem) {
                            title = getString(R.string.title_edit_group);
                        } else if (mItem instanceof TextItem) {
                            title = getString(R.string.title_edit_text);
                        }
                    }
                    setTitle(title);

                } catch (Exception e) {
                    Log.e(TAG, "init error", e);
                }

            }
        }

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    protected void handleBackPressed() {
        if (hasDataChanged()) {
            QuitWithoutSaveDlg dlg = new QuitWithoutSaveDlg();
            dlg.show(getSupportFragmentManager(), QuitWithoutSaveDlg.class.getSimpleName());
        } else {
            setResult(AppCompatActivity.RESULT_CANCELED);
            finish();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.activity_dash_board_edit, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean consumed = true;
        switch (item.getItemId()) {
            case android.R.id.home:
                handleBackPressed();
            case R.id.action_save:
                saveAndQuit();
                break;
            default:
                consumed = super.onOptionsItemSelected(item);
        }
        return consumed;
    }

    @Override
    public void onBackPressed() {
        handleBackPressed();
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (parent == mGroupSpinner) {
            Log.d(TAG, "onItemSelected group " + position);
            if (mGroupSelInit) {
                final int groupPos = getIntent().getIntExtra(ARG_GROUP_POS, -1);
                if (groupPos != position && groupPos >= 0) {
                    mGroupSpinner.setSelection(groupPos);
                } else {
                    mGroupSelInit = false;
                }
            } else {
                if (mPosSpinner != null) {
                    LinkedList<GroupItem> groupItemList = mViewModel.getGroups();
                    if (position >= 0 && position < groupItemList.size()) {
                        int groupID = groupItemList.get(position).id;
                        LinkedList<Item> items = mViewModel.getItems(groupID);
                        if (items != null) {
                            initPosSpinner(items);
                            mPosSpinner.setSelection(items.size());
                        }
                    }
                }
            }
        } else if (parent == mPosSpinner) {
            if (mItemSelInit) {
                mItemSelInit = false;
                final int itemPos  = getIntent().getIntExtra(ARG_ITEM_POS, -1);
                if (itemPos != position && itemPos >= 0) {
                    mPosSpinner.setSelection(itemPos);
                }
            }
            Log.d(TAG, "onItemSelected pos " + position);
        }
    }

    protected ArrayAdapter<String> createPosAdapter(Spinner s, List<String> adapterItems) {
        ArrayAdapter<String> a = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, adapterItems) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                if (v instanceof TextView) {
                    ((TextView) v).setText(String.valueOf(position +1));
                }
                return v;
            }
        };
        return a;
    }

    protected void initPosSpinner(List<? extends Item> itemList) {
        if (mPosSpinner != null && itemList != null) {
            ArrayList<String> adapterItems = new ArrayList<>();
            int i = 0;
            for(i = 0; i < itemList.size(); i++) {
                adapterItems.add(String.valueOf(i + 1) + " - " + itemList.get(i).label);
            }
            adapterItems.add(String.valueOf(i + 1));
            ArrayAdapter<String> a = createPosAdapter(mPosSpinner, adapterItems);
            a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            mPosSpinner.setOnItemSelectedListener(this);
            mPosSpinner.setAdapter(a);
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        Log.d(TAG, "onNothingSelected");
    }

    public static class QuitWithoutSaveDlg extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(getString(R.string.dlg_back_without_save_title));
            builder.setMessage(getString(R.string.dlg_back_without_save_msg));

            builder.setPositiveButton(R.string.action_back, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    getActivity().setResult(AppCompatActivity.RESULT_CANCELED);
                    getActivity().finish();
                }
            });

            builder.setNegativeButton(R.string.action_cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                }
            });

            AlertDialog dlg = builder.create();
            dlg.setCanceledOnTouchOutside(false);

            return dlg;
        }
    }

    protected void saveAndQuit() {
        Intent intent = new Intent();
        intent.putExtra(ARG_MODE, mMode);
        intent.putExtra(ARG_ITEM_POS, mPosSpinner.getSelectedItemPosition());
        if (mItem != null) {
            intent.putExtra(ARG_TYPE, mItem.getClass().getName());
            if (!(mItem instanceof GroupItem)) {
                intent.putExtra(ARG_GROUP_POS, mGroupSpinner.getSelectedItemPosition());
            }
            if (mEditTextLabel != null) {
                mItem.label = mEditTextLabel.getText().toString();
            }
            try {
                JSONObject jsonObject = mItem.toJSONObject();
                jsonObject.put("id", mItem.id);
                intent.putExtra(ARG_ITEM, jsonObject.toString());
            } catch(JSONException e) {
                Log.d(TAG, "save JSON: " + e.getMessage());
            }
        }
        setResult(AppCompatActivity.RESULT_OK, intent);
        finish();
    }


    protected boolean hasDataChanged() {
        return false; //TODO
    }

    protected EditText mEditTextLabel;

    // protected boolean mFirstInitStep;
    protected boolean mGroupSelInit, mItemSelInit;
    protected Item mItem;
    protected Spinner mGroupSpinner;
    protected Spinner mPosSpinner;
    protected DashBoardViewModel mViewModel;
    protected int mMode;
    // protected DashBoardViewModel.ItemContext mItemContext;

    private final static String TAG = DashBoardEditActivity.class.getSimpleName();

    public final static int MODE_ADD = 1;
    public final static int MODE_EDIT = 2;

    public final static String ARG_ACCOUNT = "ARG_ACCOUNT";
    public final static String ARG_MODE = "ARG_MODE";
    public final static String ARG_TYPE = "ARG_TYPE";
    public final static String ARG_ITEM_ID = "ARG_ITEM_ID";
    public final static String ARG_GROUP_POS = "ARG_GROUP_POS";
    public final static String ARG_ITEM_POS = "ARG_ITEM_POS";
    public final static String ARG_ITEM = "ARG_ITEM";
}
