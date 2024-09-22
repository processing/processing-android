let
  sources = import ./npins;
  pkgs = import sources.nixpkgs {
    config.android_sdk.accept_license = true;
  };
  jdk = pkgs.jdk17_headless;
  # note: can use pkgs.androidenv.androidPkgs.androidsdk
  # but it pulls in lots of toolchains see https://github.com/NixOS/nixpkgs/blob/master/pkgs/development/mobile/androidenv/default.nix#L18
  androidSdk' =
    (pkgs.androidenv.composeAndroidPackages {
      platformVersions = [ "33" ];
      includeEmulator = false;
      includeSystemImages = false;
      includeNDK = false;
    }).androidsdk;
in
(pkgs.buildFHSUserEnv {
  name = "android-sdk";
  targetPkgs =
    pkgs:
    (with pkgs; [
      androidSdk'
      glibc
      npins
      nixfmt-rfc-style
    ]);
  runScript = pkgs.writeScript "init.sh" ''
    export JAVA_HOME=${jdk}
    export PATH="${jdk}/bin:$PATH"
    export ANDROID_SDK=${androidSdk'}/libexec/android-sdk
    exec bash
  '';
}).env
