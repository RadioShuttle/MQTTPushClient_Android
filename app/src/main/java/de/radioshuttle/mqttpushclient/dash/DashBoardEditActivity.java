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
import androidx.lifecycle.ViewModelProviders;
import de.radioshuttle.mqttpushclient.JavaScriptEditorActivity;
import de.radioshuttle.mqttpushclient.PushAccount;
import de.radioshuttle.mqttpushclient.R;
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

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class DashBoardEditActivity extends AppCompatActivity
        implements AdapterView.OnItemSelectedListener, ColorPickerDialog.Callback {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dash_board_edit);

        Bundle args = getIntent().getExtras();
        if (args == null) {
            return;
        }

        mMode = args.getInt(ARG_MODE, MODE_ADD);
        String json = args.getString(ARG_ACCOUNT);
        String itemClassName = args.getString(ARG_TYPE);
        int itemID = args.getInt(ARG_ITEM_ID, -1);
        final int groupPos = args.getInt(ARG_GROUP_POS, -1);
        final int itemPos  = args.getInt(ARG_ITEM_POS, -1);
        mGroupSelInit = (savedInstanceState == null);
        mItemSelInit = (savedInstanceState == null);
        mTextSizeSelInit = (savedInstanceState == null);

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

                if (mMode == MODE_EDIT) {
                    if (itemID != -1) {
                        DashBoardViewModel.ItemContext ic = mViewModel.getItem(itemID);
                        if (ic == null) {
                            throw new RuntimeException("Edit item not found");
                        }
                        mItem = ic.item;
                    } else {
                        throw new RuntimeException("Edit item not found");
                    }
                } else { // MODE_ADD
                    DashBoardViewModel.ItemContext ic = new DashBoardViewModel.ItemContext();
                    mItem = (Item) Class.forName(itemClassName).newInstance();
                }

                /* color buttons */
                if (savedInstanceState == null) {
                    mColor = mItem.color;
                    mBackground = mItem.background;
                } else {
                    if (savedInstanceState.containsKey(KEY_COLOR)) {
                        mColor = savedInstanceState.getInt(KEY_COLOR);
                    }
                    if (savedInstanceState.containsKey(KEY_BACKGROUND)) {
                        mBackground = savedInstanceState.getInt(KEY_BACKGROUND);
                    }
                }

                int defaultColor = Color.BLACK;
                TextView tv = findViewById(R.id.dash_color_label);
                if (tv != null) {
                    ColorStateList tc = tv.getTextColors();
                    defaultColor = tc.getDefaultColor();
                }
                final int defaultBackground = ContextCompat.getColor(this, R.color.dashboad_item_background);
                mColorLabelBorderColor = defaultColor; // use default text color as border color

                mColorButton = findViewById(R.id.dash_color_button);

                if (mColorButton != null) {
                    final int defColor = defaultColor;
                    mColorButton.setColor((mColor == 0 ? defColor : mColor), mColorLabelBorderColor);
                    mColorButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            showColorDialog(defColor, (mColor == 0 ? defColor : mColor), mColorLabelBorderColor, "color");

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

                /* text size */
                mTextSizeSpinner = findViewById(R.id.dash_textSize);
                if (mTextSizeSpinner != null) {
                    ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                            R.array.dash_label_size_array, android.R.layout.simple_spinner_item);
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    mTextSizeSpinner.setOnItemSelectedListener(this);
                    mTextSizeSpinner.setAdapter(adapter);
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
        outState.putInt(KEY_COLOR, mColor);
        outState.putInt(KEY_BACKGROUND, mBackground);
        if (!Utils.isEmpty(mFilterScriptContent)) {
            outState.putString(KEY_FILTER_SCRIPT, mFilterScriptContent);
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
        } else if (parent == mTextSizeSpinner) {
            if (mTextSizeSelInit) {
                mTextSizeSelInit = false;
                int textSizeIdx = (mItem.textsize <= 0 ? Item.DEFAULT_TEXTSIZE : mItem.textsize ) -1;
                if (textSizeIdx != position && mTextSizeSpinner.getAdapter() != null && textSizeIdx >= 0 && textSizeIdx < mTextSizeSpinner.getAdapter().getCount()) {
                    mTextSizeSpinner.setSelection(textSizeIdx);
                }
            }
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

    protected void showColorDialog(int defaultColor, int currentColor, int labelBorderColor, String id) {
        Fragment currentColorPickerDlg = getSupportFragmentManager().findFragmentByTag(ColorPickerDialog.class.getSimpleName());
        if (currentColorPickerDlg == null) {
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.addToBackStack(null);

            // Create and show the dialog.
            ArrayList<Integer> palette = ColorPickerDialog.simplePalette();
            palette.add(0, defaultColor);
            // chekc if current color in palette. if not add
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
            case "color" :
                mColorButton.setColor(color, mColorLabelBorderColor);
                if (idx == 0) {
                    mColor = 0; // default system color
                } else {
                    mColor = color;
                }
                break;
            case "bcolor" :
                mBColorButton.setColor(color, mColorLabelBorderColor);
                if (idx == 0) {
                    mBackground = 0; // default system color
                } else {
                    mBackground = color;
                }
                break;
        }
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
        // MqttTopic.validate(topic, true);
        return true; //TODO:
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

    protected void saveAndQuit() {
        Intent intent = new Intent();
        intent.putExtra(ARG_MODE, mMode);
        intent.putExtra(ARG_ITEM_POS, mPosSpinner.getSelectedItemPosition());
        if (mItem != null) {
            intent.putExtra(ARG_TYPE, mItem.getClass().getName());
            if (!(mItem instanceof GroupItem)) {
                intent.putExtra(ARG_GROUP_POS, mGroupSpinner.getSelectedItemPosition());
                mItem.topic_s = mEditTextTopicSub.getText().toString();
                mItem.script_f = mFilterScriptContent == null ? "" : mFilterScriptContent;
            }
            if (mEditTextLabel != null) {
                mItem.label = mEditTextLabel.getText().toString();
            }
            if ( mTextSizeSpinner.getAdapter() != null && mTextSizeSpinner.getAdapter().getCount() > 0) {
                mItem.textsize = mTextSizeSpinner.getSelectedItemPosition() + 1;
            }
            try {
                mItem.color = mColor;
                mItem.background = mBackground;
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
                int itemPos  = getIntent().getIntExtra(ARG_ITEM_POS, AdapterView.INVALID_POSITION);
                if (mPosSpinner.getSelectedItemPosition() != itemPos) {
                    changed = true;
                }
            }
            // color or background changed?
            if (!changed) {
                changed = mItem.color != mColor || mBackground != mItem.background;
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

    protected Button mFiterScriptButton;
    protected EditText mEditTextLabel;
    protected EditText mEditTextTopicSub;
    protected ColorLabel mColorButton;
    protected ColorLabel mBColorButton;
    protected int mColorLabelBorderColor;

    /* color states */
    protected int mColor;
    protected int mBackground;
    protected String KEY_COLOR = "KEY_COLOR";
    protected String KEY_BACKGROUND = "KEY_BACKGROUND";

    protected String mFilterScriptContent;
    protected String KEY_FILTER_SCRIPT = "KEY_FILTER_SCRIPT";

    protected boolean mGroupSelInit, mItemSelInit, mTextSizeSelInit;
    protected Item mItem;
    protected Spinner mGroupSpinner;
    protected Spinner mPosSpinner;
    protected Spinner mTextSizeSpinner;
    protected DashBoardViewModel mViewModel;
    protected int mMode;

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
