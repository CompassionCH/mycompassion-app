import { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: "ch.mycompassion.app",
  appName: "My Compassion",
  webDir: "www",
  bundledWebRuntime: false,
  server: {
    url: "https://mycompassion.ch",
    cleartext: false,
    allowNavigation: [
      "mycompassion.ch",
      "*.mycompassion.ch",
      "compassion.ch",
      "*.compassion.ch"
    ]
  }
};

export default config;