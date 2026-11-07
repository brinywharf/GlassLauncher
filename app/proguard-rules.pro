# Glass Launcher R8 rules
# Services / activity are kept through the manifest automatically.

# Status bar expand is called by reflection on a framework class (nothing of ours to keep),
# but keep line numbers for readable crash reports during the alpha.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Annotations referenced by some libraries but not present at runtime
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.jetbrains.annotations.**
