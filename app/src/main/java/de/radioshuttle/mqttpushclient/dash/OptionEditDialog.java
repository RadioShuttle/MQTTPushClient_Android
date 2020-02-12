/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient.dash;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;

import java.util.ArrayList;
import java.util.List;

import de.radioshuttle.mqttpushclient.JavaScriptEditorActivity;
import de.radioshuttle.mqttpushclient.R;
import de.radioshuttle.utils.Utils;

import static de.radioshuttle.mqttpushclient.dash.DashBoardEditActivity.ARG_ACCOUNT;
import static de.radioshuttle.mqttpushclient.dash.DashBoardEditActivity.ARG_MODE;
import static de.radioshuttle.mqttpushclient.dash.DashBoardEditActivity.MODE_ADD;

public class OptionEditDialog extends DialogFragment implements AdapterView.OnItemSelectedListener {
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        Bundle args = getArguments();
        int mode = (args != null ? args.getInt(ARG_MODE) : MODE_ADD);
        if (mode == MODE_ADD) {
            builder.setTitle(R.string.title_add_option_entry);
        } else {
            builder.setTitle(R.string.title_edit_option_entry);
        }

        LayoutInflater inflater = getActivity().getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.activity_dash_board_edit_option_dlg, null);

        mPayload = dialogView.findViewById(R.id.dash_option_payload);
        mDisplayVal = dialogView.findViewById(R.id.dash_option_displayval);
        mImageNote = dialogView.findViewById(R.id.dash_option_image_note);
        mImageNone = dialogView.findViewById(R.id.dash_option_image_none);
        mImageButton = dialogView.findViewById(R.id.dash_option_image);
        mPosSpinner = dialogView.findViewById(R.id.dash_posSpinner);
        if (args != null) {
            if (savedInstanceState == null) {
                String error = args.getString(ARG_ERROR_1);
                mPayload.setText(args.getString(ARG_PAYLOAD));
                if (error != null) {
                    mPayload.setError(error);
                }
                mDisplayVal.setText(args.getString(ARG_DISPLAY_VAL));
                error = args.getString(ARG_ERROR_2);
                if (error != null) {
                    mDisplayVal.setError(error);
                }
                error = args.getString(ARG_ERROR_3); //TODO: required?
                if (error != null) {
                    mImageNote.setText(error);
                }

                mURI = args.getString(OptionEditDialog.ARG_IMAGE_URI);
            } else {
                mURI = savedInstanceState.getString(ARG_IMAGE_URI);
            }
            int size = args.getInt(ARG_LISTSIZE, -1);
            if (size >= 0) {
                if (savedInstanceState == null) {
                    mSelectedPosIdx = args.getInt(ARG_NEW_POS, -1); // pos set by a previous call
                    if (mSelectedPosIdx == AdapterView.INVALID_POSITION) {
                        mSelectedPosIdx = args.getInt(ARG_POS, -1);
                    }
                } else {
                    mSelectedPosIdx = savedInstanceState.getInt(ARG_POS);
                }
                mPosSpinner.setOnItemSelectedListener(this);
                initPosSpinner(size, mode);

                if (mSelectedPosIdx >= 0) {
                    mPosSpinner.setSelection(mSelectedPosIdx, false);
                }

            }

            setImageButton();
        }

        builder.setView(dialogView);
        builder.setPositiveButton(
                mode == MODE_ADD ? R.string.action_add : R.string.action_set,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        OptionList.Option result = new OptionList.Option();
                        result.value = mPayload.getText().toString();
                        result.displayValue = mDisplayVal.getText().toString();
                        result.imageURI = mURI;
                        result.newPos = mPosSpinner.getSelectedItemPosition();
                        if (getActivity() instanceof DashBoardEditActivity) {
                            DashBoardEditActivity activity = (DashBoardEditActivity) getActivity();
                            activity.onEditOptionDialogFinished(getArguments(), result);
                        }
                    }
                });

        builder.setNegativeButton(R.string.action_cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });

        mImageNone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openImageChooser();
            }
        });

        mImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openImageChooser();
            }
        });

        AlertDialog dlg = builder.create();
        dlg.setCanceledOnTouchOutside(false);

        return dlg;
    }

    protected void openImageChooser() {
        if (!mActivityStarted) {
            mActivityStarted = true;

            Intent intent = new Intent(getContext(), ImageChooserActivity.class);
            intent.putExtra(ImageChooserActivity.ARG_CTRL_IDX, CONTROL_CODE);
            Bundle iargs = getArguments();
            if (!Utils.isEmpty(mURI)) {
                intent.putExtra(ImageChooserActivity.ARG_RESOURCE_URI, mURI);
            }
            intent.putStringArrayListExtra(ImageChooserActivity.ARG_LOCKED_RES, new ArrayList<String>());

            Bundle args = getActivity().getIntent().getExtras();
            String acc = args.getString(ARG_ACCOUNT);
            if (!Utils.isEmpty(acc)) {
                intent.putExtra(JavaScriptEditorActivity.ARG_ACCOUNT, acc);
            }
            startActivityForResult(intent, REQUEST_CODE);
        }
    }

    protected void updateButtonVisibility() {
        if (!Utils.isEmpty(mURI)) {
            if (mImageButton.getVisibility() != View.VISIBLE) {
                mImageButton.setVisibility(View.VISIBLE);
            }
            if (mImageNone.getVisibility() != View.GONE) {
                mImageNone.setVisibility(View.GONE);
            }
        } else {
            if (mImageButton.getVisibility() != View.GONE) {
                mImageButton.setVisibility(View.GONE);
            }
            if (mImageNone.getVisibility() != View.VISIBLE) {
                mImageNone.setVisibility(View.VISIBLE);
            }
        }

    }

    protected void setImageButton() {
        if (!Utils.isEmpty(mURI)) {
            //TODO: consider loading asnyc
            boolean found = false;
            if (ImageResource.isInternalResource(mURI)) {
                mImageButton.setImageResource(IconHelper.INTENRAL_ICONS.get(mURI));
                found = true;
            } else if (ImageResource.isExternalResource(mURI)) {
                try {
                    BitmapDrawable bm = ImageResource.loadExternalImage(getContext(), mURI);
                    mImageButton.setImageDrawable(bm);
                    if (bm != null) {
                        found = true;
                    }
                } catch(Exception e) {
                    Log.d(TAG, "error loading image (ext): " , e);
                }
            }
            if (!found) {
                mImageNote.setText(getString(R.string.error_image_not_found));
            } else {
                mImageNote.setText("");
            }
        }
        updateButtonVisibility();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mActivityStarted = false;
        //TODO: hndle URI, display images
        if (requestCode  == REQUEST_CODE) {
            if (resultCode == AppCompatActivity.RESULT_OK) {
                /* if no URI submitted, user has choosen NO IMAGE */
                String uri = data.getStringExtra(ImageChooserActivity.ARG_RESOURCE_URI);
                int ctrlIdx = data.getIntExtra(ImageChooserActivity.ARG_CTRL_IDX, -1);
                if (ctrlIdx == CONTROL_CODE) {
                    if (Utils.isEmpty(uri)) {
                        Log.i(TAG, "image: none");
                        mURI = "";
                    } else {
                        mURI = uri;
                    }
                }
                setImageButton();
            }
        }
    }

    protected ArrayAdapter<String> createPosAdapter(Spinner s, List<String> adapterItems) {
        ArrayAdapter<String> a = new ArrayAdapter<String>(getContext(), android.R.layout.simple_spinner_item, adapterItems) {
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

    protected void initPosSpinner(int size, int mode) {
        if (mPosSpinner != null && size >= 0) {
            ArrayList<String> adapterItems = new ArrayList<>();
            int i = 0;
            for(i = 0; i < size; i++) {
                adapterItems.add(String.valueOf(i + 1) /* + " - " + itemList.get(i).label  */);
            }
            adapterItems.add(String.valueOf(i + 1));

            ArrayAdapter<String> a = createPosAdapter(mPosSpinner, adapterItems);
            a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            // mPosSpinner.setOnItemSelectedListener(this);
            mPosSpinner.setAdapter(a);
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (parent == mPosSpinner) {
            if (!mPinit) {
                if (mSelectedPosIdx >= 0 && position != mSelectedPosIdx) {
                    mPosSpinner.setSelection(mSelectedPosIdx);
                }
            }
            mPinit = true;
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(ARG_IMAGE_URI, mURI);
        outState.putInt(ARG_POS, mPosSpinner.getSelectedItemPosition());
    }

    boolean mActivityStarted;
    EditText mDisplayVal;
    EditText mPayload;
    TextView mImageNote;
    Button mImageNone;
    ImageButton mImageButton;
    Spinner mPosSpinner;
    boolean mPinit;
    int mSelectedPosIdx;
    String mURI;

    static final int REQUEST_CODE = 4;
    static final int CONTROL_CODE = 4;
    static final String ARG_POS  = "ARG_POS";
    static final String ARG_NEW_POS  = "ARG_NEW_POS";
    static final String ARG_LISTSIZE  = "ARG_LISTSIZE";
    static final String ARG_PAYLOAD = "ARG_PAYLOAD";
    static final String ARG_DISPLAY_VAL = "ARG_DISPLAY_VAL";
    static final String ARG_IMAGE_URI = "ARG_IMAGE_URI";
    static final String ARG_ERROR_1 = "ARG_ERROR_1";
    static final String ARG_ERROR_2 = "ARG_ERROR_2";
    static final String ARG_ERROR_3 = "ARG_ERROR_3";

    private final static String TAG = OptionEditDialog.class.getSimpleName();

}
