/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient.dash;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import de.radioshuttle.mqttpushclient.CertificateErrorDialog;
import de.radioshuttle.mqttpushclient.JavaScriptEditorActivity;
import de.radioshuttle.mqttpushclient.PushAccount;
import de.radioshuttle.mqttpushclient.R;
import de.radioshuttle.net.Cmd;
import de.radioshuttle.net.DashboardRequest;
import de.radioshuttle.net.Request;
import de.radioshuttle.utils.MqttTopic;
import de.radioshuttle.utils.Utils;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class DashBoardEditActivity extends AppCompatActivity implements
        AdapterView.OnItemSelectedListener, ColorPickerDialog.Callback,
        CertificateErrorDialog.Callback,
        Observer<Request>
{

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dash_board_edit);

        Bundle args = getIntent().getExtras();
        if (args == null) {
            return;
        }

        if (savedInstanceState == null) {
            mMode = args.getInt(ARG_MODE, MODE_ADD);
            mSelectedGroupIdx = args.getInt(ARG_GROUP_POS, -1);
            mSelectedPosIdx = args.getInt(ARG_ITEM_POS, -1);
            mSelectedTextIdx = Item.DEFAULT_TEXTSIZE - 1;
        } else {
            mMode = args.getInt(ARG_MODE);
            mSelectedGroupIdx = savedInstanceState.getInt(KEY_SELECTED_GROUP, -1);
            mSelectedPosIdx = savedInstanceState.getInt(KEY_SELECTED_POS, -1);
            mSelectedTextIdx = savedInstanceState.getInt(KEY_SELECTED_TEXT, -1);
        }

        String json = args.getString(ARG_ACCOUNT);
        String itemClassName = args.getString(ARG_TYPE);
        String dashboardContentRaw;
        long dashboardContentVersion;

        int itemID;
        itemID = args.getInt(ARG_ITEM_ID, -1);
        dashboardContentRaw = args.getString(ARG_DASHBOARD, "");
        dashboardContentVersion =  args.getLong(ARG_DASHBOARD_VERSION, 0L);

        /* check arguemnts */
        if (!(json == null || itemClassName == null || (mMode == MODE_EDIT && itemID == -1))) {
            PushAccount b = null;
            try {
                /* create viewModel */
                b = PushAccount.createAccountFormJSON(new JSONObject(json));
                mViewModel = ViewModelProviders.of(
                        this, new DashBoardViewModel.Factory(b, getApplication()))
                        .get(DashBoardViewModel.class);

                if (!mViewModel.isInitialized()) {
                    mViewModel.setItems(dashboardContentRaw, dashboardContentVersion);
                }

                if (mMode == MODE_EDIT) {
                    if (itemID != -1) {
                        DashBoardViewModel.ItemContext ic = mViewModel.getItem(itemID);
                        if (ic == null) {
                            throw new RuntimeException("Edit item not found");
                        }
                        mItem = ic.item;
                        if (savedInstanceState == null) {
                            mSelectedTextIdx = (mItem.textsize <= 0 ? Item.DEFAULT_TEXTSIZE : mItem.textsize ) -1;
                        }
                    } else {
                        throw new RuntimeException("Edit item not found");
                    }
                } else { // MODE_ADD
                    DashBoardViewModel.ItemContext ic = new DashBoardViewModel.ItemContext();
                    mItem = (Item) Class.forName(itemClassName).newInstance();
                }

                /* textcolor buttons */
                if (savedInstanceState == null) {
                    mTextColor = mItem.textcolor;
                    mBackground = mItem.background;
                } else {
                    if (savedInstanceState.containsKey(KEY_TEXTCOLOR)) {
                        mTextColor = savedInstanceState.getInt(KEY_TEXTCOLOR);
                    }
                    if (savedInstanceState.containsKey(KEY_BACKGROUND)) {
                        mBackground = savedInstanceState.getInt(KEY_BACKGROUND);
                    }
                }

                int defaultColor = Color.BLACK;
                TextView tv = findViewById(R.id.dash_text_color_label);
                if (tv != null) {
                    ColorStateList tc = tv.getTextColors();
                    defaultColor = tc.getDefaultColor();
                }
                final int defaultBackground = ContextCompat.getColor(this, R.color.dashboad_item_background);
                mColorLabelBorderColor = defaultColor; // use default text textcolor as border textcolor

                mColorButton = findViewById(R.id.dash_text_color_button);

                if (mColorButton != null) {
                    final int defColor = defaultColor;
                    mColorButton.setColor((mTextColor == 0 ? defColor : mTextColor), mColorLabelBorderColor);
                    mColorButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            showColorDialog(defColor, (mTextColor == 0 ? defColor : mTextColor), mColorLabelBorderColor, "textcolor");

                        }
                    });
                }

                mBColorButton = findViewById(R.id.dash_bcolor_button);
                if (mBColorButton != null) {
                    mBColorButton.setColor((mBackground == 0 ? defaultBackground : mBackground), mColorLabelBorderColor);
                    mBColorButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            showColorDialog(defaultBackground, (mBackground == 0 ? defaultBackground : mBackground), mColorLabelBorderColor,  "bcolor");
                        }
                    });
                }

                /* name / label */
                mEditTextLabel = findViewById(R.id.dash_name);
                if (savedInstanceState == null) {
                    mEditTextLabel.setText(mItem.label);
                }


                /* group */
                TableRow groupRow = findViewById(R.id.rowGroup);
                if (groupRow != null && mItem != null) {
                    if (mItem instanceof GroupItem) {
                        /* group selection is not required when item is a group */
                        groupRow.setVisibility(View.GONE);
                    } else {
                        mGroupSpinner = findViewById(R.id.dash_groupSpinner);
                        if (mGroupSpinner != null) {
                            // DBUtils.spinnerSelectWorkaround(mGroupSpinner, mSelectedGroupIdx, "group");
                            ArrayList<String> groups = new ArrayList<>();
                            List<GroupItem> groupItems = mViewModel.getGroups();
                            for(int i = 0; i < groupItems.size(); i++) {
                                groups.add(groupItems.get(i).label);
                            }
                            ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, groups);
                            a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                            mGroupSpinner.setOnItemSelectedListener(this);
                            mGroupSpinner.setAdapter(a);
                            // selection below is sometimes not working, see workaround above DBUtils.spinnerSelectWorkaround()
                            if (mSelectedGroupIdx >= 0) {
                                Log.d(TAG, "set selection group: " + mSelectedGroupIdx);
                                // mFirstInitStep = (itemPos < 0);
                                mGroupSpinner.setSelection(mSelectedGroupIdx, false);
                            }
                        }
                    }
                }

                /* position */
                mPosSpinner = findViewById(R.id.dash_posSpinner);
                if (mPosSpinner != null) {
                    mPosSpinner.setOnItemSelectedListener(this);
                    if (mItem instanceof GroupItem) {
                        List<GroupItem> groupItems = mViewModel.getGroups();
                        initPosSpinner(groupItems);
                    } else {
                        /* items can only be added, if a group is selected */
                        List<GroupItem> groupItems = mViewModel.getGroups();
                        if (mSelectedGroupIdx >= 0 && mSelectedGroupIdx < groupItems.size()) {
                            initPosSpinner(mViewModel.getItems(groupItems.get(mSelectedGroupIdx).id));
                        }
                    }
                    if (mSelectedPosIdx >= 0) {
                        mPosSpinner.setSelection(mSelectedPosIdx, false);
                        Log.d(TAG, "idx: " + mSelectedPosIdx + " sel: " + mPosSpinner.getSelectedItemPosition());
                    }
                }

                /* text size */
                mTextSizeSpinner = findViewById(R.id.dash_textSize);
                if (mTextSizeSpinner != null) {
                    ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                            R.array.dash_label_size_array, android.R.layout.simple_spinner_item);
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    mTextSizeSpinner.setAdapter(adapter);
                    if (mSelectedTextIdx >= 0) {
                        Log.d(TAG, "set selection textsize: " + mSelectedTextIdx);
                        mPosSpinner.setSelection(mSelectedTextIdx, false);
                    }
                }

                /* subscribed topic */
                TableRow topicSubRow = findViewById(R.id.rowTopicSub);
                if (topicSubRow != null) {
                    mEditTextTopicSub = findViewById(R.id.dash_subscribe);
                    if (mItem instanceof GroupItem) {
                        topicSubRow.setVisibility(View.GONE);
                    } else if (savedInstanceState == null) {
                        mEditTextTopicSub.setText(mItem.topic_s);
                    }
                }

                /* filter/UI sctipt */
                TableRow rowFilterScript = findViewById(R.id.rowFilterScript);
                if (rowFilterScript != null) {
                    mFiterScriptButton = findViewById(R.id.filterButton);
                    if (mItem instanceof GroupItem) {
                        rowFilterScript.setVisibility(View.GONE);
                    } else {
                        mFiterScriptButton.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                openFilterScriptEditor();
                            }
                        });
                        if (savedInstanceState == null){
                            mFilterScriptContent = mItem.script_f;
                        } else {
                            mFilterScriptContent = savedInstanceState.getString(KEY_FILTER_SCRIPT, "");
                        }
                        setFilterButtonText();
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

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        mViewModel.mSaveRequest.observe(this, this );

        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swiperefresh);
        mSwipeRefreshLayout.setEnabled(false);
        mSwipeRefreshLayout.setRefreshing(mViewModel.isRequestActive());
        updateUI(!mViewModel.isRequestActive());

    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_TEXTCOLOR, mTextColor);
        outState.putInt(KEY_BACKGROUND, mBackground);
        if (!Utils.isEmpty(mFilterScriptContent)) {
            outState.putString(KEY_FILTER_SCRIPT, mFilterScriptContent);
        }
        outState.putInt(ARG_MODE, mMode);
        if (mGroupSpinner != null) {
            outState.putInt(KEY_SELECTED_GROUP, mGroupSpinner.getSelectedItemPosition());
        }
        if (mPosSpinner != null) {
            outState.putInt(KEY_SELECTED_POS, mPosSpinner.getSelectedItemPosition());
        }
        if (mTextSizeSpinner != null) {
            outState.putInt(KEY_SELECTED_TEXT, mTextSizeSpinner.getSelectedItemPosition());
        }

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
                break;
            case R.id.action_save:
                save();
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
            if (mPosSpinner != null && (mGinit && mPinit)) {
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
            if (!mGinit) {
                if (mSelectedGroupIdx >= 0 && position != mSelectedGroupIdx) {
                    mGroupSpinner.setSelection(mSelectedGroupIdx);
                }
            }
            mGinit = true;
        } else if (parent == mPosSpinner) {
            if (!mPinit) {
                if (mSelectedPosIdx >= 0 && position != mSelectedPosIdx) {
                    mPosSpinner.setSelection(mSelectedPosIdx);
                }
            }
            mPinit = true;
        } else if (parent == mTextSizeSpinner) {
            if (!mTinit) {
                if (mSelectedTextIdx >= 0 && position != mSelectedTextIdx) {
                    mTextSizeSpinner.setSelection(mSelectedTextIdx);
                }
            }
            mTinit = true;
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
            // mPosSpinner.setOnItemSelectedListener(this);
            mPosSpinner.setAdapter(a);
        }
    }

    protected void showColorDialog(int defaultColor, int currentColor, int labelBorderColor, String id) {
        Fragment currentColorPickerDlg = getSupportFragmentManager().findFragmentByTag(ColorPickerDialog.class.getSimpleName());
        if (currentColorPickerDlg == null) {
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.addToBackStack(null);

            // Create and show the dialog.
            ArrayList<Integer> palette = ColorPickerDialog.simplePalette();
            palette.add(0, defaultColor);
            // chekc if current textcolor in palette. if not add
            boolean found = false;
            for(int i = 0; i < palette.size(); i++) {
                if (palette.get(i) == currentColor) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                if (palette.size() > 1) {
                    palette.add(1, currentColor);
                } else {
                    palette.add(currentColor);
                }
            }

            DialogFragment newFragment = ColorPickerDialog.newInstance(id, palette, labelBorderColor);
            newFragment.show(ft, ColorPickerDialog.class.getSimpleName());
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        Log.d(TAG, "onNothingSelected");
    }

    @Override
    public void onColorSelected(int idx, int color, String id) {
        switch (id) {
            case "textcolor" :
                mColorButton.setColor(color, mColorLabelBorderColor);
                if (idx == 0) {
                    mTextColor = 0; // default system textcolor
                } else {
                    mTextColor = color;
                }
                break;
            case "bcolor" :
                mBColorButton.setColor(color, mColorLabelBorderColor);
                if (idx == 0) {
                    mBackground = 0; // default system background color
                } else {
                    mBackground = color;
                }
                break;
        }
    }

    @Override
    public void onChanged(Request request) {
        if (request != null && request instanceof DashboardRequest) {
            DashboardRequest dashboardRequest = (DashboardRequest) request;
            PushAccount b = dashboardRequest.getAccount();
            if (b.status == 1) {
                mSwipeRefreshLayout.setRefreshing(true);
            } else {
                boolean isNew = false;
                if (mViewModel.isCurrentRequest(request)) {
                    isNew = mViewModel.isRequestActive(); // result already processed/displayed?
                    mViewModel.confirmResultDelivered();
                    mSwipeRefreshLayout.setRefreshing(false);
                    updateUI(true);

                    /* handle cerificate exception */
                    if (b.hasCertifiateException()) {
                        //TODO
                    }
                    b.setCertificateExeption(null); // mark es "processed"
                    /* end handle cerificate exception */
                    /* handle insecure connection */
                    if (b.inSecureConnectionAsk) {
                        //TODO
                    }
                    b.inSecureConnectionAsk = false; // mark as "processed"
                    if (b.requestStatus != Cmd.RC_OK) {
                        String t = (b.requestErrorTxt == null ? "" : b.requestErrorTxt);
                        if (b.requestStatus == Cmd.RC_MQTT_ERROR || (b.requestStatus == Cmd.RC_NOT_AUTHORIZED && b.requestErrorCode != 0)) {
                            t = getString(R.string.errormsg_mqtt_prefix) + " " + t;
                        }
                        showErrorMsg(t);
                    } else {
                        if (dashboardRequest.saveSuccesful()) {
                            if (isNew) {
                                Intent data = new Intent();
                                data.putExtra(ARG_DASHBOARD, dashboardRequest.getReceivedDashboard());
                                data.putExtra(ARG_DASHBOARD_VERSION, dashboardRequest.getServerVersion());
                                setResult(AppCompatActivity.RESULT_OK, data);
                                Toast.makeText(getApplicationContext(), R.string.info_data_saved, Toast.LENGTH_LONG).show();
                                finish();
                            }
                        } else if (!dashboardRequest.saveSuccesful() && dashboardRequest.requestStatus != Cmd.RC_OK) {
                            String t = (dashboardRequest.requestErrorTxt == null ? "" : dashboardRequest.requestErrorTxt);
                            if (dashboardRequest.requestStatus == Cmd.RC_MQTT_ERROR) {
                                t = getString(R.string.errormsg_mqtt_prefix) + " " + t;
                            }
                            showErrorMsg(t);
                        } else if (dashboardRequest.isVersionError()) {
                            String t = getString(R.string.dash_err_version_err);
                            showErrorMsg(t);
                        }
                    }

                }
            }
        }

    }

    @Override
    public void retry(Bundle args) {
        //TODO
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

    protected boolean isValidInput() {
        boolean valid = true;
        if (!(mItem instanceof GroupItem)) {
            String subTopic = mEditTextTopicSub.getText().toString();
            if (!Utils.isEmpty(subTopic)) {
                try {
                    MqttTopic.validate(subTopic, true);
                } catch(IllegalArgumentException i) {
                    mEditTextTopicSub.setError(getString(R.string.err_invalid_topic_format));
                    valid = false;
                }
            }
        }
        return valid;
    }

    protected void setFilterButtonText() {
        if (Utils.isEmpty(mFilterScriptContent)) {
            mFiterScriptButton.setText(getString(R.string.dlg_filter_button_add));
        } else {
            mFiterScriptButton.setText(getString(R.string.dlg_filter_button_edit));
        }
    }

    protected void openFilterScriptEditor() {
        if (!mActivityStarted) {
            mActivityStarted = true;

            Intent intent = new Intent(this, JavaScriptEditorActivity.class);
            if (Utils.isEmpty(mFilterScriptContent)) {
                intent.putExtra(JavaScriptEditorActivity.ARG_TITLE, getString(R.string.title_add_javascript));
            } else {
                intent.putExtra(JavaScriptEditorActivity.ARG_TITLE, getString(R.string.title_edit_javascript));
                intent.putExtra(JavaScriptEditorActivity.ARG_JAVASCRIPT, mFilterScriptContent);
            }

            String header = getString(R.string.dash_filter_script_header);
            intent.putExtra(JavaScriptEditorActivity.ARG_HEADER, header);
            intent.putExtra(JavaScriptEditorActivity.ARG_JSPREFIX, "function filterMsg(msg, acc, view) {\n var content = msg.text;");
            intent.putExtra(JavaScriptEditorActivity.ARG_JSSUFFIX, " return content;\n}");
            intent.putExtra(JavaScriptEditorActivity.ARG_COMPONENT, JavaScriptEditorActivity.CONTENT_FILTER);

            Bundle args = getIntent().getExtras();
            String acc = args.getString(ARG_ACCOUNT);
            if (!Utils.isEmpty(acc)) {
                intent.putExtra(JavaScriptEditorActivity.ARG_ACCOUNT, acc);
            }
            startActivityForResult(intent, 1);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mActivityStarted = false;
        // requestCode == 1 for JavaScriptEditor
        if (requestCode == 1 && resultCode == AppCompatActivity.RESULT_OK) {
            mFilterScriptContent = data.getStringExtra(JavaScriptEditorActivity.ARG_JAVASCRIPT);
            setFilterButtonText();
        }
    }

    protected void save() {
        if (!hasDataChanged()) {
            Toast.makeText(getApplicationContext(), R.string.error_data_unmodified, Toast.LENGTH_LONG).show();
        } else if (isValidInput()){
            if (mItem != null) {
                int itemPos = mPosSpinner != null ? mPosSpinner.getSelectedItemPosition() : 0;
                int groupPos = -1;
                if (!(mItem instanceof GroupItem) && mGroupSpinner != null) {
                    groupPos = mGroupSpinner.getSelectedItemPosition();
                    mItem.topic_s = mEditTextTopicSub.getText().toString();
                    mItem.script_f = mFilterScriptContent == null ? "" : mFilterScriptContent;
                }
                if (mEditTextLabel != null) {
                    mItem.label = mEditTextLabel.getText().toString();
                }
                if (mTextSizeSpinner != null && mTextSizeSpinner.getAdapter() != null && mTextSizeSpinner.getAdapter().getCount() > 0) {
                    mItem.textsize = mTextSizeSpinner.getSelectedItemPosition() + 1;
                }
                mItem.textcolor = mTextColor;
                mItem.background = mBackground;

                /* convert complete dashboard to json */
                LinkedList<GroupItem> groupItems = new LinkedList<>();
                HashMap<Integer, LinkedList<Item>> items = new HashMap<>();
                mViewModel.copyItems(groupItems, items);

                if (mMode == MODE_ADD) {
                    if (mItem instanceof GroupItem) {
                        DashBoardViewModel.addGroup(groupItems, items, itemPos, (GroupItem) mItem);
                    } else {
                        /* it there is no group yet, create group and add item to it */
                        if (groupItems.size() == 0) {
                            GroupItem groupItem = new GroupItem();
                            groupItem.label = getString(R.string.new_group_label);
                            DashBoardViewModel.addGroup(groupItems, items, 0, groupItem);
                            groupPos = 0;
                            itemPos = 0;
                        }
                        DashBoardViewModel.addItem(groupItems, items, groupPos, itemPos, mItem);
                    }
                } else { // MODE_EDIT
                    if (mItem instanceof GroupItem) {
                        DashBoardViewModel.setGroup(groupItems, itemPos, (GroupItem) mItem);
                    } else {
                        DashBoardViewModel.setItem(groupItems, items, groupPos, itemPos, mItem);
                    }
                }
                JSONArray arr = DBUtils.createJSONStrFromItems(groupItems, items, true);
                updateUI(false);
                mViewModel.saveDashboard(arr);
                Log.d(TAG, "json: "+  arr.toString());
            }

        }
    }

    protected void updateUI(boolean enableFields) {
        setEnabled(mFiterScriptButton, enableFields);
        setEnabled(mEditTextLabel, enableFields);
        setEnabled(mEditTextTopicSub, enableFields);
        setEnabled(mColorButton, enableFields);
        setEnabled(mBColorButton, enableFields);
        setEnabled(mGroupSpinner, enableFields);
        setEnabled(mPosSpinner, enableFields);
        setEnabled(mTextSizeSpinner, enableFields);
    }

    protected void setEnabled(View v, boolean enabled) {
        if (v != null) {
            v.setEnabled(enabled);
        }
    }

    protected void showErrorMsg(String msg) {
        View v = findViewById(R.id.rView);
        if (v != null) {
            mSnackbar = Snackbar.make(v, msg, Snackbar.LENGTH_INDEFINITE);
            mSnackbar.show();
        }
    }

    protected boolean hasDataChanged() {
        boolean changed = false;
        if (!Utils.equals(mEditTextLabel.getText().toString(), mItem.label)) {
            changed = true;
        } else {
            // group, position changed ?
            if (!(mItem instanceof GroupItem)) {
                int groupPos = getIntent().getIntExtra(ARG_GROUP_POS, AdapterView.INVALID_POSITION);
                if (mGroupSpinner.getSelectedItemPosition() != groupPos) {
                    changed = true;
                }
                // subscribe topic changed?
                if (!changed && !Utils.equals(mEditTextTopicSub.getText().toString(), mItem.topic_s)) {
                    changed = true;
                }
                // filter script changed?
                if (!changed && !Utils.equals(mFilterScriptContent, mItem.script_f)) {
                    changed = true;
                }
            }
            if (!changed) {
                int itemPos = getIntent().getIntExtra(ARG_ITEM_POS, AdapterView.INVALID_POSITION);
                if (mPosSpinner.getSelectedItemPosition() != itemPos) {
                    changed = true;
                }
            }
            // textcolor or background changed?
            if (!changed) {
                changed = mItem.textcolor != mTextColor || mBackground != mItem.background;
            }
            // text size changed?
            if (!changed && mTextSizeSpinner.getAdapter() != null && mTextSizeSpinner.getAdapter().getCount() > 0) {
                int textSizePos = (mItem.textsize <= 0 ? Item.DEFAULT_TEXTSIZE : mItem.textsize) - 1;
                changed = mTextSizeSpinner.getSelectedItemPosition() != textSizePos;
            }
        }

        return changed;
    }

    protected boolean mActivityStarted;

    //UI elements
    protected Button mFiterScriptButton;
    protected EditText mEditTextLabel;
    protected EditText mEditTextTopicSub;
    protected ColorLabel mColorButton;
    protected ColorLabel mBColorButton;
    protected Spinner mGroupSpinner;
    protected Spinner mPosSpinner;
    protected Spinner mTextSizeSpinner;

    boolean mGinit, mPinit, mTinit;

    protected int mColorLabelBorderColor;

    /* textcolor states */
    protected int mTextColor;
    protected int mBackground;
    protected final static String KEY_TEXTCOLOR = "KEY_TEXTCOLOR";
    protected final static String KEY_BACKGROUND = "KEY_BACKGROUND";

    protected String mFilterScriptContent;
    protected final static String KEY_FILTER_SCRIPT = "KEY_FILTER_SCRIPT";

    protected int mSelectedGroupIdx;
    protected final static String KEY_SELECTED_GROUP = "KEY_SELECTED_GROUP";
    protected int mSelectedPosIdx;
    protected final static String KEY_SELECTED_POS = "KEY_SELECTED_POS";
    protected int mSelectedTextIdx;
    protected final static String KEY_SELECTED_TEXT = "KEY_SELECTED_TEXT";


    protected Item mItem;

    protected DashBoardViewModel mViewModel;
    protected int mMode;

    private SwipeRefreshLayout mSwipeRefreshLayout;
    private Snackbar mSnackbar;

    private final static String TAG = DashBoardEditActivity.class.getSimpleName();

    public final static int MODE_ADD = 1;
    public final static int MODE_EDIT = 2;

    public final static String ARG_ACCOUNT = "ARG_ACCOUNT";
    public final static String ARG_MODE = "ARG_MODE";
    public final static String ARG_TYPE = "ARG_TYPE";
    public final static String ARG_ITEM_ID = "ARG_ITEM_ID";
    public final static String ARG_GROUP_POS = "ARG_GROUP_POS";
    public final static String ARG_ITEM_POS = "ARG_ITEM_POS";
    public final static String ARG_DASHBOARD = "ARG_DASHBOARD";
    public final static String ARG_DASHBOARD_VERSION = "ARG_DASHBOARD_VERSION";
}
