# keep the bridge the web page calls
-keepclassmembers class app.overlie.MainActivity$Bridge {
    @android.webkit.JavascriptInterface <methods>;
}
