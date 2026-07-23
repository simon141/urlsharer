package app.urlsharer;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A tiny, dependency-free activity that receives a URL (via VIEW or SEND),
 * lets the user edit it in a large text area, and then hands it off to any
 * installed browser through a row of quick-open icons.
 */
public class MainActivity extends Activity {

    private static final int REQUEST_BROWSER_ROLE = 1;
    private EditText urlText;
    private LinearLayout browserButtons;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        urlText = findViewById(R.id.urlText);
        browserButtons = findViewById(R.id.browserButtons);
        TextView versionText = findViewById(R.id.versionText);
        versionText.setText(getString(R.string.version, BuildConfig.VERSION_NAME));

        handleIntent(getIntent());
        if (savedInstanceState == null) {
            urlText.post(this::promptForDefaultBrowser);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        populateBrowserButtons();
    }

    /** Offers a direct route to Android's default-browser prompt when needed. */
    private void promptForDefaultBrowser() {
        if (isDefaultBrowser()) {
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.default_browser_title)
                .setMessage(R.string.default_browser_message)
                .setNegativeButton(R.string.not_now, null)
                .setPositiveButton(R.string.set_as_default, (dialog, which) -> requestBrowserRole())
                .show();
    }

    private boolean isDefaultBrowser() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roles = getSystemService(RoleManager.class);
            return roles != null && roles.isRoleAvailable(RoleManager.ROLE_BROWSER)
                    && roles.isRoleHeld(RoleManager.ROLE_BROWSER);
        }

        ResolveInfo current = getPackageManager().resolveActivity(
                new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")),
                PackageManager.MATCH_DEFAULT_ONLY);
        return current != null && getPackageName().equals(current.activityInfo.packageName);
    }

    private void requestBrowserRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roles = getSystemService(RoleManager.class);
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
                try {
                    startActivityForResult(
                            roles.createRequestRoleIntent(RoleManager.ROLE_BROWSER),
                            REQUEST_BROWSER_ROLE);
                } catch (RuntimeException ignored) {
                    openDefaultAppsSettings();
                }
                return;
            }
        }
        openDefaultAppsSettings();
    }

    private void openDefaultAppsSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));
        } catch (RuntimeException ignored) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (RuntimeException e) {
                Toast.makeText(this, R.string.error_default_settings, Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    /** Pulls a URL out of whatever intent launched us and puts it in the text area. */
    private void handleIntent(Intent intent) {
        if (intent == null) {
            return;
        }

        String extracted = null;
        String action = intent.getAction();

        if (Intent.ACTION_VIEW.equals(action)) {
            Uri data = intent.getData();
            if (data != null) {
                extracted = data.toString();
            }
        } else if (Intent.ACTION_SEND.equals(action)) {
            CharSequence shared = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
            if (shared != null) {
                extracted = shared.toString();
            }
        }

        if (!TextUtils.isEmpty(extracted)) {
            urlText.setText(extracted.trim());
            // Place the caret at the end so long URLs are easy to keep editing.
            urlText.setSelection(urlText.getText().length());
        }
    }

    /** Queries installed web handlers and builds one square icon button per app. */
    private void populateBrowserButtons() {
        PackageManager pm = getPackageManager();
        List<ResolveInfo> matches = new ArrayList<>();
        for (String url : new String[]{"https://example.com", "http://example.com"}) {
            Intent probe = new Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addCategory(Intent.CATEGORY_BROWSABLE);
            matches.addAll(pm.queryIntentActivities(probe, PackageManager.MATCH_ALL));
        }
        matches.sort(new ResolveInfo.DisplayNameComparator(pm));
        browserButtons.removeAllViews();

        Set<String> seen = new LinkedHashSet<>();
        int size = dp(64);
        int padding = dp(8);
        for (ResolveInfo info : matches) {
            String pkg = info.activityInfo.packageName;
            if (getPackageName().equals(pkg)
                    || MainActivity.class.getName().equals(info.activityInfo.name)
                    || !seen.add(pkg)) {
                continue;
            }

            if (browserButtons.getChildCount() > 0) {
                View divider = new View(this);
                divider.setBackgroundColor(Color.GRAY);
                LinearLayout.LayoutParams dividerParams =
                        new LinearLayout.LayoutParams(dp(1), dp(48));
                dividerParams.gravity = Gravity.CENTER_VERTICAL;
                dividerParams.setMarginStart(dp(4));
                dividerParams.setMarginEnd(dp(4));
                browserButtons.addView(divider, dividerParams);
            }

            ImageButton button = new ImageButton(this);
            button.setBackgroundColor(Color.TRANSPARENT);
            button.setImageDrawable(info.loadIcon(pm));
            button.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
            button.setPadding(padding, padding, padding, padding);
            button.setContentDescription(getString(R.string.open_in, info.loadLabel(pm)));
            button.setOnClickListener(v -> openInBrowser(pkg));
            browserButtons.addView(button, new LinearLayout.LayoutParams(size, size));
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private Uri getEditedUri() {
        String url = urlText.getText().toString().trim();
        if (TextUtils.isEmpty(url)) {
            Toast.makeText(this, R.string.error_empty, Toast.LENGTH_SHORT).show();
            return null;
        }

        Uri uri = Uri.parse(url);
        if (uri.getScheme() == null) {
            // Assume http(s) when the user typed a bare host like "example.com".
            uri = Uri.parse("https://" + url);
        }
        return uri;
    }

    private void openInBrowser(String pkg) {
        Uri uri = getEditedUri();
        if (uri == null) {
            return;
        }

        Intent target = new Intent(Intent.ACTION_VIEW, uri)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setPackage(pkg);
        try {
            startActivity(target);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.error_no_app, Toast.LENGTH_LONG).show();
            populateBrowserButtons();
        }
    }
}
