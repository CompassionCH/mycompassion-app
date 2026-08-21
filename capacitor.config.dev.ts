import { CapacitorConfig } from '@capacitor/cli';

// STAGE build config — loaded by `sync:dev` / `build:stage-android`.
// Points the app at the stage server. Pair with the Android `stage`
// flavor / iOS "App Stage" scheme so it installs as a separate app.
const config: CapacitorConfig = {
  appId: "ch.mycompassion.app",
  appName: "My Compassion Stage",
  webDir: "www",
  bundledWebRuntime: false,
  "server": {
    "url": "https://stage18.compassion.ch/web/login",
    "errorPath": "maintenance.html",
    "cleartext": false,
    "allowNavigation": [
        "mycompassion.ch",
        "*.mycompassion.ch",
        "compassion.ch",
        "*.compassion.ch",
        "postfinance.ch",
        "*.postfinance.ch",
    ]
  },
  "plugins": {
      "SplashScreen": {
        "launchAutoHide": false,
        "backgroundColor": "#2a5eec",
        "androidSplashResourceName": "splash",
        "androidScaleType": "CENTER_CROP",
        "showSpinner": false
      }
    }
};

export default config;
