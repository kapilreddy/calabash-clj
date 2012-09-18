#calabash-clj

Functional testing on android using calabash-jvm. The project includes some automation for building android project, building instrumentation for project and launching emulators.

## Basic usage
The following code block will do these things.
- Build android project
- Build instrumentation server for specified android project
- Start android emulators
- Start instrumentation on each emulator through adb shell
- Start running calabash tests

```clj
(require [calabash-clj.build.android :as android-build]
        [calabash-clj.platforms.android :as android])

(android-build/-main "/path/to/android-project/"
                     ["froyo" "gingerbread"] ;; List of emulator avds.
                     (fn []
                         ;; Write calabash queries
                         (android/touch "css" "div")))
```


## Future
- Add support for real devices.
- Add automation for builds and simulator for iOS.
