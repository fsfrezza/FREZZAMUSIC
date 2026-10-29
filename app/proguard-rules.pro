# Remove low-level diagnostic logging from production, including library calls.
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}
-assumenosideeffects class java.io.PrintStream {
    public void print(...);
    public void println(...);
}
-assumenosideeffects class java.lang.Throwable {
    public void printStackTrace(...);
}
# Keep diagnostic line information for privately stored R8 mappings, not source filenames.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
