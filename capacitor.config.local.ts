import { CapacitorConfig } from '@capacitor/cli';

// LOCAL build config. The host has to match the domain of the MyCompassion
// website record, or Odoo serves the default website instead. Simulator:
// mycompassion.localhost. Physical phone: set the website domain to
// mycompassion.<lan-ip>.nip.io and use that here.
const config: CapacitorConfig = {
  appId: "ch.mycompassion.app",
  appName: "My Compassion",
  webDir: "www",
  bundledWebRuntime: false,
  "server": {
    "url": "http://mycompassion.localhost:8069/web/login",
    "errorPath": "maintenance.html",
    "cleartext": true,
    "allowNavigation": [
        "mycompassion.localhost",
        "*.localhost",
        "*.local",
        "*.nip.io",
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
