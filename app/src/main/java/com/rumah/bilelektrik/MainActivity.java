package com.rumah.bilelektrik;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.google.mlkit.vision.text.TextRecognizer;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int REQUEST_OCR_IMAGE = 44;
    private static final Set<String> ALLOWED_FIELDS = new HashSet<>(Arrays.asList(
            "bill", "zprev", "zcurr", "sprev", "scurr"));
    private WebView webView;
    private TextRecognizer textRecognizer;
    private String requestedField = "";
    private Uri cameraPhotoUri;
    private File cameraPhotoFile;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(15, 49, 67));
        getWindow().setNavigationBarColor(Color.rgb(15, 49, 67));
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(244, 248, 250));
        setContentView(webView);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        webView.addJavascriptInterface(new OcrBridge(), "AndroidOCR");
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // Keep all application content local; do not expose the native bridge to remote pages.
                return !"file".equalsIgnoreCase(request.getUrl().getScheme());
            }
        });
        webView.loadUrl("file:///android_asset/index.html");
    }

    public class OcrBridge {
        @JavascriptInterface public void scan(String field, String source) {
            if (!ALLOWED_FIELDS.contains(field) || !("camera".equals(source) || "gallery".equals(source))) return;
            runOnUiThread(() -> openImageChooser(field, source));
        }
        @JavascriptInterface public void shareText(String content) {
            if (content == null || content.length() > 15000) return;
            runOnUiThread(() -> {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_TEXT, content);
                try { startActivity(Intent.createChooser(send, "Kongsi keputusan")); }
                catch (Exception ex) { Toast.makeText(MainActivity.this, "Tiada aplikasi untuk berkongsi.", Toast.LENGTH_LONG).show(); }
            });
        }
        @JavascriptInterface public void copyText(String content) {
            if (content == null || content.length() > 15000) return;
            runOnUiThread(() -> {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText("Bil Elektrik", content));
                Toast.makeText(MainActivity.this, "Keputusan disalin.", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void openImageChooser(String field, String source) {
        requestedField = field;
        cameraPhotoUri = null;
        cameraPhotoFile = null;
        try {
            if ("camera".equals(source)) {
                File cacheFolder = new File(getCacheDir(), "ocr");
                if (!cacheFolder.exists() && !cacheFolder.mkdirs()) throw new IOException("Cannot create photo cache");
                cameraPhotoFile = File.createTempFile("meter_", ".jpg", cacheFolder);
                cameraPhotoUri = FileProvider.getUriForFile(this,
                        getPackageName() + ".fileprovider", cameraPhotoFile);
                Intent capture = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                capture.putExtra(MediaStore.EXTRA_OUTPUT, cameraPhotoUri);
                capture.setClipData(ClipData.newRawUri("Foto bacaan", cameraPhotoUri));
                capture.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                startActivityForResult(capture, REQUEST_OCR_IMAGE);
            } else {
                Intent choose = new Intent(Intent.ACTION_GET_CONTENT);
                choose.setType("image/*");
                choose.addCategory(Intent.CATEGORY_OPENABLE);
                choose.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivityForResult(Intent.createChooser(choose, "Pilih gambar"), REQUEST_OCR_IMAGE);
            }
        } catch (ActivityNotFoundException e) {
            sendError("Tiada aplikasi kamera/galeri ditemui.");
        } catch (Exception e) {
            sendError("Tidak dapat membuka gambar: " + e.getMessage());
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_OCR_IMAGE) return;
        if (resultCode != RESULT_OK) {
            sendCancelled();
            return;
        }
        Uri photo = (cameraPhotoUri != null) ? cameraPhotoUri : (data != null ? data.getData() : null);
        if (photo == null) { sendError("Gambar tidak diperoleh."); return; }
        try {
            InputImage image = InputImage.fromFilePath(this, photo);
            final String field = requestedField;
            textRecognizer.process(image)
                .addOnSuccessListener(result -> {
                    String rawText = result.getText();
                    sendOcrResult(field, rawText == null ? "" : rawText);
                    removeTempPhoto();
                })
                .addOnFailureListener(ex -> {
                    sendError("OCR gagal membaca gambar. Cuba gambar yang lebih jelas atau isi manual.");
                    removeTempPhoto();
                });
        } catch (Exception ex) {
            sendError("Gambar tidak dapat dibaca. Cuba gambar lain.");
            removeTempPhoto();
        }
    }

    private void removeTempPhoto() {
        if (cameraPhotoFile != null) {
            cameraPhotoFile.delete();
            cameraPhotoFile = null;
        }
        cameraPhotoUri = null;
    }

    private void sendOcrResult(String field, String rawText) {
        final String js = "window.BilOCR.receive(" + JSONObject.quote(field) + "," + JSONObject.quote(rawText) + ");";
        runOnUiThread(() -> webView.evaluateJavascript(js, null));
    }
    private void sendError(String message) {
        final String js = "window.BilOCR.failed(" + JSONObject.quote(requestedField) + "," + JSONObject.quote(message) + ");";
        runOnUiThread(() -> webView.evaluateJavascript(js, null));
    }
    private void sendCancelled() {
        final String js = "window.BilOCR.cancelled(" + JSONObject.quote(requestedField) + ");";
        runOnUiThread(() -> webView.evaluateJavascript(js, null));
    }

    @Override public void onBackPressed() {
        // Single-screen form; let back close the app rather than navigate to remote WebView pages.
        super.onBackPressed();
    }
    @Override protected void onDestroy() {
        if (textRecognizer != null) textRecognizer.close();
        removeTempPhoto();
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}
