#calabash-clj

Functional testing on android using [calabash-jvm] (https://github.com/calabash/calabash-ios/tree/master/calabash-jvm). The project includes some automation for building android project, building instrumentation for project and launching emulators.

calabash-clj in action
<img src="https://github.com/downloads/kapilreddy/calabash-clj/calabash_clj_screenshot.png"></img>

## Basic usage
The following code block will do these things.
- Build android project
- Build instrumentation server for specified android project
- Start android emulators
- Start instrumentation on each emulator through adb shell
- Start running calabash tests

```clj
(require '[calabash-clj.build.android :as android-build]
         '[calabash-clj.platforms.android :as android])

(android-build/run-on-emulators ["froyo" "gingerbread"] ;; List of emulator avds.
                                {:project-path "/path/to/android-project/"}
                                (fn []
                                  ;; Write calabash queries
                                  (android/command "click_on_view_by_id" "button1")))
```
Add internet permission in your project's AndroidManifest.xml for test server running on android.
```xml
<uses-permission android:name="android.permission.INTERNET" />
```

To run it on connected physical devices and emulators.
```clj
(require '[calabash-clj.build.android :as android-build]
         '[calabash-clj.platforms.android :as android])

(android-build/run-on-connected-devices {:project-path "/path/to/android-project/"}
                                        (fn []
                                          ;; Write calabash queries
                                          (android/command "click_on_view_by_id" "button1")))
```

## Future
- Add automation for builds and simulator for iOS.
