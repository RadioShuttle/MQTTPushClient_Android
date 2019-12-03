/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2018 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient;

import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import de.radioshuttle.utils.Utils;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.net.URL;
import java.util.HashMap;
import java.util.Locale;

public class HelpActivity extends AppCompatActivity implements SwipeRefreshLayout.OnRefreshListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);

        setTitle(R.string.title_help);

        webView = (WebView) findViewById(R.id.helpWebView);
        webView.getSettings().setJavaScriptEnabled(true);
        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swiperefresh);
        // mSwipeRefreshLayout.setSize(SwipeRefreshLayout.LARGE);
        mSwipeRefreshLayout.setOnRefreshListener(this);

        String url;
        Bundle args = getIntent().getExtras();
        if (args != null && args.containsKey(CONTEXT_HELP) && !Utils.isEmpty(args.getString(CONTEXT_HELP))) {
            url = buildURL(HELP_URL, args.getString(CONTEXT_HELP));
        } else {
            url = HELP_URL;
        }

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        }

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (Build.VERSION.SDK_INT >= 24) {
                    if (loadExternal(request.getUrl().toString())) {
                        Intent webIntent = new Intent(Intent.ACTION_VIEW, request.getUrl());
                        startActivity(webIntent);
                        return true;
                    }
                }
                return false;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Log.d(TAG, " url: " + url);
                if (Build.VERSION.SDK_INT < 24) {
                    if (loadExternal(url)) {
                        Uri requestURL = Uri.parse(url);
                        Intent webIntent = new Intent(Intent.ACTION_VIEW, requestURL);
                        startActivity(webIntent);
                        return true;
                    }
                }
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                /* clear backstack if url is TOC) */
                if (webView != null && url.equals(HELP_URL)) {
                    webView.clearHistory();
                }
                hideProgressBar();
                super.onPageFinished(view, url);
            }
        });
        if (webView.getOriginalUrl() == null) {
            load(url);
        }


        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

    }

    protected boolean loadExternal(String url) {
        boolean loadExt = false;
        if (url != null) {
            try {
                loadExt = new URL(url).toURI().getAuthority().compareToIgnoreCase(new URL(HELP_URL).toURI().getAuthority()) != 0;
            } catch (Exception e) {
                // e.printStackTrace();
            }
        }
        return loadExt;
    }

    protected void load(String url) {
        showProgressBar();
        String lang = Locale.getDefault().getLanguage();
        if (lang != null && !lang.isEmpty()) {
            /* workaround: two digit lang code was not submitted by webview component */
            //TODO: check if two digit lang code is now submitted in newer version
            HashMap<String, String> headers = new HashMap<>();
            lang = lang.toLowerCase();
            if (!lang.equals("en"))
                lang += ",en;q=0.4";
            // Log.d("HelpFragment", "lang code: " + lang);
            headers.put("Accept-Language", lang);
            webView.loadUrl(url, headers);
        } else {
            webView.loadUrl(url);
        }
    }

    protected String buildURL(String baseURL, String page) {
        return baseURL + getLanguageCode() + "/" + page;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                handleBackPressed();
                return true;
            case R.id.menu_reload:
                onRefresh();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onBackPressed() {
        handleBackPressed();
        // super.onBackPressed();
    }

    protected void handleBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            setResult(AppCompatActivity.RESULT_CANCELED);
            finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_help, menu);
        return true;
    }

    @Override
    public void onSaveInstanceState(Bundle outState)
    {
        // Save the state of the WebView
        if (webView != null)
            webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }


    @Override
    public void onRefresh() {
        showProgressBar();
        webView.clearCache(true);
        webView.reload();
    }

    protected void showProgressBar() {
        if (!mSwipeRefreshLayout.isRefreshing())
            mSwipeRefreshLayout.setRefreshing(true);
    }

    protected void hideProgressBar() {
        mSwipeRefreshLayout.setRefreshing(false);
    }

    /** returns language code de, en (default will be en), helper to build context URLs */
    public static String getLanguageCode() {
        Locale def = Locale.getDefault();
        String code;
        if (def.getLanguage().equals(new Locale("de").getLanguage())) {
            code = "de";
       } else {
            code = "en";
        }
        return code;
    }

    private WebView webView;
    private SwipeRefreshLayout mSwipeRefreshLayout;

    private final static String TAG = HelpActivity.class.getSimpleName();

    public final static String CONTEXT_HELP = "CONTEXT_HELP";
    public final static String HELP_URL = "https://help.radioshuttle.de/mqttapp/1.0/";

    public final static String HELP_TOPIC_FILTER_SCRIPTS = "filter-scripts.html";

    //TODO: replace placeholder (filter-scripts.html)
    public final static String HELP_DASH_FILTER_SCRIPT = "filter-scripts.html";
    public final static String HELP_DASH_OUTPUT_SCRIPT = "filter-scripts.html";
    public final static String HELP_DASH_CUSTOM_VIEW_HTML = "filter-scripts.html";

}
