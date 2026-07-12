# وضعیت اعتبارسنجی بسته

- ساختار پروژه، Kotlin DSL، Manifest، منابع، parserها و مسیرهای اصلی پردازش به‌صورت ایستا بررسی شده‌اند.
- تست‌های واحد برای parsing لینک، فایل‌های اشتراکی و streamها داخل پروژه قرار دارند.
- workflow آمادهٔ GitHub Actions، وظایف `testDebugUnitTest` و `assembleDebug` را اجرا می‌کند.
- محیط تولید این بسته Android SDK و دسترسی مستقیم Maven نداشت؛ بنابراین APK در همین محیط assemble نشده است. نخستین Gradle Sync یا اجرای workflow، اعتبارسنجی نهایی وابستگی‌های باینری و native را انجام می‌دهد.
