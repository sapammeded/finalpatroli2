package com.stt.professionalsecuritypatrol;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.getcapacitor.BridgeActivity;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class MainActivity extends BridgeActivity {
  private static final int RC_CAMERA = 4101;
  private static final int RC_FILE = 4102;
  private Uri pendingCameraUri;
  private String pendingCameraTarget = "";
  private ValueCallback<Uri[]> pendingFileCallback;

  @Override public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    final WebView web = this.bridge.getWebView();
    web.addJavascriptInterface(new AndroidMedia(), "AndroidMedia");
    web.setWebChromeClient(new WebChromeClient() {
      @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
        if (pendingFileCallback != null) pendingFileCallback.onReceiveValue(null);
        pendingFileCallback = callback;
        try {
          Intent intent = params.createIntent();
          startActivityForResult(intent, RC_FILE);
        } catch (Exception e) {
          pendingFileCallback = null;
          callback.onReceiveValue(null);
          return false;
        }
        return true;
      }
    });
  }

  public class AndroidMedia {
    @JavascriptInterface public void captureCamera(String target) {
      runOnUiThread(() -> launchCamera(target == null ? "" : target));
    }
    @JavascriptInterface public void chooseMedia() {
      runOnUiThread(MainActivity.this::launchFilePicker);
    }
  }

  private void launchCamera(String target) {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
      pendingCameraTarget = target;
      ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, RC_CAMERA);
      return;
    }
    Intent camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
    if (camera.resolveActivity(getPackageManager()) == null) { notifyJsError("Tidak ada aplikasi kamera"); return; }
    ContentValues values = new ContentValues();
    values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
    values.put(MediaStore.Images.Media.DISPLAY_NAME, "STT_" + System.currentTimeMillis() + ".jpg");
    pendingCameraUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
    if (pendingCameraUri == null) { notifyJsError("Gagal membuat file foto"); return; }
    camera.putExtra(MediaStore.EXTRA_OUTPUT, pendingCameraUri);
    camera.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
    startActivityForResult(Intent.createChooser(camera, "Pilih Kamera"), RC_CAMERA);
  }

  private void launchFilePicker() {
    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
    i.setType("image/*");
    i.addCategory(Intent.CATEGORY_OPENABLE);
    startActivityForResult(Intent.createChooser(i, "Pilih Foto"), RC_FILE);
  }

  @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == RC_CAMERA && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) launchCamera(pendingCameraTarget);
    else if (requestCode == RC_CAMERA) notifyJsError("Izin kamera ditolak");
  }

  @Override protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == RC_CAMERA) {
      if (resultCode == Activity.RESULT_OK && pendingCameraUri != null) {
        Uri u = pendingCameraUri; pendingCameraUri = null; sendUriToJs(u);
      } else {
        if (pendingCameraUri != null) getContentResolver().delete(pendingCameraUri, null, null);
        pendingCameraUri = null; notifyJsError("Pengambilan foto dibatalkan");
      }
      return;
    }
    if (requestCode == RC_FILE && pendingFileCallback != null) {
      Uri[] result = (resultCode == Activity.RESULT_OK && data != null) ? FileChooserParams.parseResult(resultCode, data) : null;
      pendingFileCallback.onReceiveValue(result); pendingFileCallback = null;
    }
  }

  private void sendUriToJs(Uri uri) {
    try {
      InputStream in = getContentResolver().openInputStream(uri);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buf = new byte[8192]; int n;
      while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
      in.close();
      String b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
      String js = "window.dispatchEvent(new CustomEvent('sttNativeCameraResult',{detail:{dataUrl:'data:image/jpeg;base64," + b64 + "'}}));";
      this.bridge.getWebView().post(() -> this.bridge.getWebView().evaluateJavascript(js, null));
    } catch (Exception e) { notifyJsError("Gagal membaca hasil kamera"); }
  }

  private void notifyJsError(String msg) {
    String safe = msg.replace("\\", "\\\\").replace("'", "\\'");
    this.bridge.getWebView().post(() -> this.bridge.getWebView().evaluateJavascript("window.dispatchEvent(new CustomEvent('sttNativeCameraError',{detail:{message:'" + safe + "'}}));", null));
  }
}
