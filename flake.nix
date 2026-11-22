{
  description = "TinyBattery Android widget build environment";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs = { self, nixpkgs }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs {
        inherit system;
        config = {
          # Explicitly accept Google's Android SDK license for reproducible setup.
          android_sdk.accept_license = true;
          allowUnfree = true;
        };
      };

      buildToolsVersion = "35.0.0";
      android = pkgs.androidenv.composeAndroidPackages {
        platformVersions = [ "35" ];
        buildToolsVersions = [ buildToolsVersion ];
        includeEmulator = false;
        includeSystemImages = false;
        includeSources = false;
        includeCmake = false;
        includeNDK = false;
      };
    in
    {
      devShells.${system}.default = pkgs.mkShell {
        packages = [
          pkgs.jdk17
          pkgs.gradle_8
          android.androidsdk
        ];

        ANDROID_HOME = "${android.androidsdk}/libexec/android-sdk";
        ANDROID_SDK_ROOT = "${android.androidsdk}/libexec/android-sdk";
        JAVA_HOME = "${pkgs.jdk17}";

        shellHook = ''
          export PATH="$ANDROID_HOME/platform-tools:$PATH"

          aapt2="$ANDROID_HOME/build-tools/${buildToolsVersion}/aapt2"
          if [ ! -x "$aapt2" ]; then
            echo "TinyBattery: Nix AAPT2 is missing or not executable: $aapt2" >&2
            return 1
          fi

          # Fail here with the real loader/library error rather than later with
          # AGP's unhelpful 'AAPT2 Daemon startup failed' message.
          if ! aapt2_version="$("$aapt2" version 2>&1)"; then
            echo "TinyBattery: Nix AAPT2 exists but cannot start:" >&2
            echo "$aapt2_version" >&2
            return 1
          fi
          echo "TinyBattery: using $aapt2"
          echo "$aapt2_version"

          # AGP's AAPT2 override must be passed as a Gradle project property.
          # Writing it to local.properties is not reliable across AGP versions.
          # Install a project-local Gradle launcher so the usual
          # `gradle assembleDebug` command always passes the override.
          if [ -f "$PWD/settings.gradle.kts" ]; then
            mkdir -p "$PWD/.nix-support/bin"
            cat > "$PWD/.nix-support/bin/gradle" <<EOF_GRADLE_WRAPPER
#!/usr/bin/env bash
exec "${pkgs.gradle_8}/bin/gradle" \
  -Dorg.gradle.project.android.aapt2FromMavenOverride="$aapt2" \
  "\$@"
EOF_GRADLE_WRAPPER
            chmod +x "$PWD/.nix-support/bin/gradle"
            export PATH="$PWD/.nix-support/bin:$PATH"
            echo "TinyBattery: Gradle launcher configured to pass the AAPT2 override (-Dorg.gradle.project...)."
          else
            echo "TinyBattery: enter nix develop from the project root to configure the Gradle launcher." >&2
          fi
        '';
      };
    };
}
