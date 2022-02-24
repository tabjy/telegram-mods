# Telegram Mods for Xposed

## Features

- [x] Unlimited viewing of self-destruct photos and videos
- [x] Saving self-destruct photos to gallery

## Usage

1. Compile the Xposed module
    ```sh
    $ git clone https://github.com/tabjy/telegram-mods.git
    $ ./gradlew assembleDebug
    ```
2. Install the generated `.apk`, for example:
    ```sh
    $ adb install app/build/outputs/apk/debug/app-debug.apk
    ```
3. Open Lsposed or EdXposed and enable "Telegram Mods"
4. Select the Telegram app in the enabled scope
5. Force stop the Telegram app and restart

## TODOs

- [ ] Spoofing self-destruct message read acknowledgements
- [ ] Preventing message deletions
- [ ] Saving self-destruct videos to gallery
- [ ] Automating CI/CD via GitHub Actions
