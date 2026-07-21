package app.urlsharer;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
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

    private EditText urlText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        urlText = findViewById(R.id.urlText);
        Button openButton = findViewById(R.id.openButton);
        openButton.setOnClickListener(v -> openWith());

        handleIntent(getIntent());
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

        // Build an explicit intent for each app that can open the URL. Relying on a
        // plain createChooser() lets the system collapse straight to the default
        // browser when it thinks there is only one candidate; enumerating targets
        // ourselves guarantees the full list is always offered.
        Intent probe = new Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE);
        PackageManager pm = getPackageManager();
        List<ResolveInfo> matches = pm.queryIntentActivities(probe, PackageManager.MATCH_ALL);

        String selfPackage = getPackageName();
        Set<String> seen = new LinkedHashSet<>();
        List<Intent> targets = new ArrayList<>();
        for (ResolveInfo info : matches) {
            String pkg = info.activityInfo.packageName;
            // Skip ourselves (avoids a redirect loop) and de-duplicate per app.
            if (selfPackage.equals(pkg) || !seen.add(pkg)) {
                continue;
            }
            Intent target = new Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE);
            target.setComponent(new ComponentName(pkg, info.activityInfo.name));
            targets.add(target);
        }

        if (targets.isEmpty()) {
            Toast.makeText(this, R.string.error_no_app, Toast.LENGTH_LONG).show();
            return;
        }

        // First target seeds the chooser; the rest are added as initial intents.
        Intent chooser = Intent.createChooser(targets.remove(0), getString(R.string.open_with));
        if (!targets.isEmpty()) {
            chooser.putExtra(
                    Intent.EXTRA_INITIAL_INTENTS,
                    targets.toArray(new Parcelable[0]));
        }

        try {
            startActivity(chooser);
        } catch (RuntimeException e) {
            Toast.makeText(this, R.string.error_no_app, Toast.LENGTH_LONG).show();
        }
    }
}
