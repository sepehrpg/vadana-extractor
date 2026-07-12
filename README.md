# Vadana Extractor for Android

نسخهٔ اندرویدی استخراج‌گر ضبط‌های Adobe Connect / وادانا با **Kotlin** و **Jetpack Compose**.

این پروژه منطق اصلی مخزن `phoseinq/vadana-extractor` را به معماری اندروید منتقل می‌کند:

- تحلیل لینک ضبط و session
- دانلود بستهٔ آفلاین Adobe Connect با نمایش پیشرفت و retry
- استخراج فایل‌های Share Pod با نام واقعی
- خواندن `mainstream.xml`، `indexstream.xml` و تمام `ftcontent*.xml`
- بازسازی قلم، متن، حذف شکل‌ها و تعویض صفحهٔ وایت‌برد
- قرار دادن PDF اشتراکی در پس‌زمینهٔ دست‌خط
- ساخت PDF چندصفحه‌ای وایت‌برد
- استخراج و ترکیب segmentهای صوتی `cameraVoip*.flv`
- بازسازی screen-share و PDF/pointer روی timeline
- ساخت MP4 همگام با H.264/MPEG-4 + AAC
- WorkManager foreground، اعلان پیشرفت و لغو عملیات
- ذخیرهٔ خروجی در `Downloads/Vadana`، `Movies/Vadana` و `Music/Vadana`
- رمزگذاری URL و session موقت Worker با Android Keystore
- جلوگیری از SSRF، redirect بین دامنه‌ای و filename/path traversal

## نیازمندی‌ها

- Android Studio جدید
- JDK 17
- Android SDK 35
- Android 10 یا بالاتر (`minSdk 29`)
- اینترنت برای دریافت وابستگی‌های Gradle

## اجرا

1. پوشه را در Android Studio باز کنید.
2. اجازه دهید Gradle Sync کامل شود.
3. یک دستگاه یا شبیه‌ساز Android 10+ انتخاب کنید.
4. ماژول `app` را اجرا کنید.

ساخت خط فرمان:

```bash
./gradlew assembleDebug
```

در ویندوز:

```bat
gradlew.bat assembleDebug
```

اسکریپت‌های `gradlew` این بسته در اولین اجرا Gradle 8.11.1 را دریافت می‌کنند. در صورت تمایل می‌توانید از Android Studio گزینهٔ استاندارد **Generate Gradle Wrapper** را اجرا کنید تا `gradle-wrapper.jar` نیز تولید شود.

## استفاده

1. لینک کامل ضبط را وارد کنید؛ برای کلاس‌های خصوصی مقدار `session=` نیز باید داخل لینک باشد.
2. روی «تحلیل کلاس» بزنید.
3. خروجی‌های موردنظر را انتخاب کنید.
4. کیفیت ویدئو را انتخاب و استخراج را شروع کنید.
5. پردازش در foreground ادامه پیدا می‌کند و از اعلان قابل لغو است.

## ساختار

```text
app/src/main/java/ir/vadana/extractor/
├── data/       دانلود، ZIP و parserهای Adobe Connect
├── domain/     مدل‌های مستقل برنامه
├── render/     Canvas، PDF و تولید frame
├── media/      FFmpeg، صوت و ترکیب ویدئو
├── storage/    MediaStore و رمزگذاری درخواست Worker
├── worker/     پردازش foreground
└── ui/         Compose و ViewModel
```

## موتور FFmpeg

پروژه از بستهٔ Maven زیر استفاده می‌کند:

```kotlin
implementation("com.moizhassan.ffmpeg:ffmpeg-kit-16kb:6.1.1")
```

این بسته API سازگار با FFmpegKit و کتابخانه‌های native سازگار با page size جدید اندروید را ارائه می‌دهد. کد ابتدا `libx264` را امتحان می‌کند و در صورت نبودن encoder، خودکار به encoder داخلی `mpeg4` برمی‌گردد.

برای انتشار در فروشگاه، مجوز دقیق binary و قابلیت‌های فعال‌شده در build FFmpeg را بررسی و Noticeهای لازم را همراه برنامه منتشر کنید.

## محدودیت‌های عملی

- ساخت ویدئوی 1080p/1440p روی گوشی ضعیف می‌تواند زمان‌بر و داغ‌کننده باشد.
- ضبط‌های بسیار بزرگ به چند گیگابایت فضای خالی موقت نیاز دارند.
- layoutهای غیرمعمول Adobe Connect ممکن است parser یا الگوریتم انتخاب PDF را نیازمند توسعه کنند.
- خروجی از نظر عملکرد معادل نسخهٔ Python است، ولی به‌دلیل فونت و encoder دستگاه بایت‌به‌بایت یکسان نیست.
- بعضی سرورهای قدیمی HTTP هستند؛ cleartext برای سازگاری فعال شده، اما TLS هرجا موجود باشد ترجیح داده می‌شود.

## تست و CI

```bash
./gradlew testDebugUnitTest assembleDebug
```

تست‌های پایه برای parsing لینک، فایل‌های اشتراکی و streamها در `app/src/test` قرار دارند. فایل `.github/workflows/android.yml` نیز build و تست را روی GitHub Actions اجرا و APK دیباگ را به‌عنوان artifact ذخیره می‌کند.

جزئیات اعتبارسنجی این بسته در `BUILD_STATUS.md` آمده است.

## مجوز و انتساب

کد این مخزن تحت MIT منتشر شده است. منطق آن از پروژهٔ MIT زیر اقتباس شده است:

- `phoseinq/vadana-extractor`
- Copyright (c) 2026 phoseinq

متن مجوزها و توضیحات وابستگی‌ها در `LICENSE` و `THIRD_PARTY_NOTICES.md` آمده است.
