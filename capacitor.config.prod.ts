import { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: "ch.mycompassion.app",
  appName: "MyCompassionCH",
  webDir: "www",
  bundledWebRuntime: false,
  "server": {
    "url": "https://stage14.compassion.ch/web/login",
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
        "showSpinner": false
      }
    }
};

export default config;