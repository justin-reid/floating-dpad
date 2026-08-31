# The Shizuku user service is loaded by class name into a separate process
# running as the shell UID. Its name and the generated AIDL stub must survive.
-keep class com.floatingdpad.input.** { *; }
-keep class rikka.shizuku.** { *; }
