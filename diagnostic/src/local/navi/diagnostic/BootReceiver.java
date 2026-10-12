package local.navi.diagnostic;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

/** One launch per actual boot; no timer, foreground watchdog or overlay. */
public class BootReceiver extends BroadcastReceiver {
    static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("startup", Context.MODE_PRIVATE);
    }
    static Intent homeIntent() {
        return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
    }
    static boolean isDefaultHome(Context context) {
        ResolveInfo resolved = context.getPackageManager().resolveActivity(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY);
        return resolved != null && resolved.activityInfo != null
            && context.getPackageName().equals(resolved.activityInfo.packageName);
    }
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        WifiConnectionService.start(context,true);
        SharedPreferences prefs = preferences(context);
        String result = "자동 실행 꺼짐";
        if (prefs.getBoolean("boot_enabled", false)) {
            if (isDefaultHome(context)) {
                result = "기본 홈으로 지정됨 · 홈 시작은 시스템에 맡김";
            } else {
                try {
                    context.startActivity(new Intent(context, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
                    result = "부팅 시 앱 실행 요청 완료";
                } catch (RuntimeException e) {
                    result = "실행 요청 실패: " + e.getClass().getSimpleName();
                }
            }
        }
        prefs.edit().putLong("last_boot", System.currentTimeMillis()).putString("boot_result", result).commit();
    }
}
