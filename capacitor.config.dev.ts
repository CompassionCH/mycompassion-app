import { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: "ch.mycompassion.app.dev",
  appName: "Compassion Dev",
  webDir: "www",
  bundledWebRuntime: false,
  "server": {
    "url": "http://mycompassion.192.168.199.123.nip.io:8069",
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