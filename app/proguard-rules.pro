# R8 rules for Previsor de Datos Móviles.
#
# Compose, Room, WorkManager and the AndroidX artifacts ship their own
# consumer keep rules, so only reflection-instantiated app classes need
# explicit keeps here.

# WorkManager instantiates workers by class name from the work spec.
-keep class com.example.notifications.PrepaidExpiryWorker {
    public <init>(...);
}
-keep class com.example.notifications.BalanceReadingReminderWorker {
    public <init>(...);
}
-keep class com.example.notifications.RechargeReminderWorker {
    public <init>(...);
}
-keep class com.example.updates.UpdateCheckWorker {
    public <init>(...);
}
-keep class com.example.updates.UpdateDownloadWorker {
    public <init>(...);
}

# Keep crash reports readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
