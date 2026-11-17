package app.overlie;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Overlie as a real Android app: the web app (bundled in assets/) runs in a WebView,
 * fully offline. Native code only handles camera permission, the photo picker,
 * and saving/sharing photos to the gallery.
 */
public class MainActivity extends Activity {
    // Fake https origin so the page is a "secure context" (needed for the camera)
    private static final String ORIGIN = "https://overlie.local/";
    private static final int REQ_CAMERA = 1, REQ_PICK = 2, REQ_STORAGE = 3;

    private WebView web;
    private PermissionRequest pendingPermission;
    private ValueCallback<Uri[]> fileCallback;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        web = new WebView(this);
        web.setBackgroundColor(0xff0d0c0b);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);

        web.addJavascriptInterface(new Bridge(), "OverlieNative");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                String url = r.getUrl().toString();
                if (!url.startsWith(ORIGIN)) return null;
                String path = url.substring(ORIGIN.length());
                int q = path.indexOf('?');
                if (q >= 0) path = path.substring(0, q);
                if (path.isEmpty()) path = "index.html";
                try {
                    InputStream in = getAssets().open(path);
                    return new WebResourceResponse(mime(path), "utf-8", in);
                } catch (Exception e) {
                    return new WebResourceResponse("text/plain", "utf-8", 404, "Not found", null, null);
                }
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> {
                    if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        request.grant(request.getResources());
                    } else {
                        pendingPermission = request;
                        requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
                    }
                });
            }

            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = cb;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.setType("image/*");
                i.addCategory(Intent.CATEGORY_OPENABLE);
                try {
                    startActivityForResult(Intent.createChooser(i, "Overlay photo"), REQ_PICK);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });

        if (Build.VERSION.SDK_INT < 29
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
        }

        web.loadUrl(ORIGIN + "index.html");
    }

    private static String mime(String p) {
        if (p.endsWith(".html")) return "text/html";
        if (p.endsWith(".js")) return "application/javascript";
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".webmanifest")) return "application/manifest+json";
        return "application/octet-stream";
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (code == REQ_CAMERA && pendingPermission != null) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                pendingPermission.grant(pendingPermission.getResources());
            } else {
                pendingPermission.deny();
            }
            pendingPermission = null;
        }
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        if (code == REQ_PICK && fileCallback != null) {
            Uri[] picked = null;
            if (result == RESULT_OK && data != null && data.getData() != null) picked = new Uri[]{data.getData()};
            fileCallback.onReceiveValue(picked);
            fileCallback = null;
        }
    }

    @Override
    protected void onPause() { super.onPause(); web.onPause(); }

    @Override
    protected void onResume() { super.onResume(); web.onResume(); }

    @Override
    public void onBackPressed() {
        // close the photo preview first if it's open, otherwise leave the app
        web.evaluateJavascript("(function(){var s=document.getElementById('sheet');if(s&&s.classList.contains('on')){s.classList.remove('on');return 1}return 0})()",
                v -> { if (!"1".equals(v)) finish(); });
    }

    /** Writes a JPEG into Pictures/Overlie and returns its gallery Uri. */
    private Uri saveToGallery(byte[] jpeg, String name) throws Exception {
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Images.Media.DISPLAY_NAME, name);
            cv.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            cv.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Overlie");
            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
            if (uri == null) throw new Exception("no uri");
            try (OutputStream out = getContentResolver().openOutputStream(uri)) { out.write(jpeg); }
            return uri;
        } else {
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Overlie");
            if (!dir.exists() && !dir.mkdirs()) throw new Exception("no folder");
            File f = new File(dir, name);
            try (FileOutputStream out = new FileOutputStream(f)) { out.write(jpeg); }
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Images.Media.DATA, f.getAbsolutePath());
            cv.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
            return uri != null ? uri : Uri.fromFile(f);
        }
    }

    private void toast(String m) { runOnUiThread(() -> Toast.makeText(this, m, Toast.LENGTH_SHORT).show()); }

    /** Called from the page as window.OverlieNative.* */
    class Bridge {
        @JavascriptInterface
        public void save(String b64, String name) {
            try {
                saveToGallery(Base64.decode(b64, Base64.DEFAULT), name);
                toast("Saved to Pictures/Overlie");
            } catch (Exception e) {
                toast("Couldn't save the photo");
            }
        }

        @JavascriptInterface
        public void share(String b64, String name) {
            try {
                Uri uri = saveToGallery(Base64.decode(b64, Base64.DEFAULT), name);
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("image/jpeg");
                send.putExtra(Intent.EXTRA_STREAM, uri);
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                runOnUiThread(() -> startActivity(Intent.createChooser(send, "Share photo")));
            } catch (Exception e) {
                toast("Couldn't share the photo");
            }
        }
    }
}
