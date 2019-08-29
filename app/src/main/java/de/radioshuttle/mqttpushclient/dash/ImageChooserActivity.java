/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient.dash;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

import de.radioshuttle.mqttpushclient.R;

public class ImageChooserActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_chooser);
    }

    public final static String ARG_CTRL_IDX = "ARG_CTRL_IDX";
    public final static String ARG_RESOURCE_URI = "ARG_RESOURCE_URI";
}
