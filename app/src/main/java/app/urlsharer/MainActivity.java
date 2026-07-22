package app.urlsharer;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A tiny, dependency-free activity that receives a URL (via VIEW or SEND),
 * lets the user edit it in a large text area, and then hands it off to any
 * browser or app that can open it through the system chooser.
 */
public class MainActivity extends Activity {

    private static final int REQUEST_BROWSER_ROLE = 1;
    private EditText urlText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        urlText = findViewById(R.id.urlText);
        Button openButton = findViewById(R.id.openButton);
        openButton.setOnClickListener(v -> openWith());
        TextView versionText = findViewById(R.id.versionText);
        versionText.setText(getString(R.string.version, BuildConfig.VERSION_NAME));

        handleIntent(getIntent());
        if (savedInstanceState == null) {
            urlText.post(this::promptForDefaultBrowser);
        }
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

    /** Shows a chooser listing every browser/app that can open the edited URL. */
    private void openWith() {
        String url = urlText.getText().toString().trim();
        if (TextUtils.isEmpty(url)) {
            Toast.makeText(this, R.string.error_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        Uri uri = Uri.parse(url);
        if (uri.getScheme() == null) {
            // Assume http(s) when the user typed a bare host like "example.com".
            uri = Uri.parse("https://" + url);
        }

        Intent probe = new Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE);
        PackageManager pm = getPackageManager();
        List<ResolveInfo> matches = pm.queryIntentActivities(probe, PackageManager.MATCH_ALL);
        matches.sort(new ResolveInfo.DisplayNameComparator(pm));

        Set<String> seen = new LinkedHashSet<>();
        List<ResolveInfo> choices = new ArrayList<>();
        List<Intent> targets = new ArrayList<>();
        for (ResolveInfo info : matches) {
            String pkg = info.activityInfo.packageName;
            if (getPackageName().equals(pkg) || !seen.add(pkg)) {
                continue;
            }
            Intent target = new Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE);
            target.setComponent(new ComponentName(pkg, info.activityInfo.name));
            choices.add(info);
            targets.add(target);
        }

        if (targets.isEmpty()) {
            Toast.makeText(this, R.string.error_no_app, Toast.LENGTH_LONG).show();
            return;
        }

        int rowPadding = Math.round(16 * getResources().getDisplayMetrics().density);
        ArrayAdapter<ResolveInfo> adapter = new ArrayAdapter<ResolveInfo>(
                this, android.R.layout.activity_list_item, android.R.id.text1, choices) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View row = super.getView(position, convertView, parent);
                row.setPadding(rowPadding, rowPadding, rowPadding, rowPadding);
                ResolveInfo info = getItem(position);
                ((ImageView) row.findViewById(android.R.id.icon)).setImageDrawable(info.loadIcon(pm));
                ((TextView) row.findViewById(android.R.id.text1)).setText(info.loadLabel(pm));
                return row;
            }
        };

        new AlertDialog.Builder(this)
                .setTitle(R.string.open_with)
                .setAdapter(adapter, (dialog, which) -> {
                    try {
                        startActivity(targets.get(which));
                    } catch (ActivityNotFoundException e) {
                        Toast.makeText(this, R.string.error_no_app, Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }
}
