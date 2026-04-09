import { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: "ch.mycompassion.app.",
  appName: "MyCompassionCH",
  webDir: "www",
  bundledWebRuntime: false,
  "server": {
    "url": "https://stage14.compassion.ch/web/login",
    "cleartext": true,
    "allowNavigation": [
        "mycompassion.ch",
        "*.mycompassion.ch",
        "compassion.ch",
        "*.compassion.ch",
        "*.nip.io"
    ]
  }
};

export default config;