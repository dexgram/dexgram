#!/usr/bin/env sh
# Build Android native libraries only.
#
# This script builds the Nix Android library outputs used by the Android app and
# extracts the resulting .so files. It does not build, package, align, or sign
# any APK.

set -eu

script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"

find_repo_root() {
  # Prefer an explicit repository path when the script is copied elsewhere.
  if [ -n "${REPO_ROOT:-}" ]; then
    if [ -f "$REPO_ROOT/flake.nix" ]; then
      CDPATH= cd -- "$REPO_ROOT" && pwd
      return
    fi

    printf 'REPO_ROOT does not contain flake.nix: %s\n' "$REPO_ROOT" >&2
    exit 1
  fi

  # Support the normal location: scripts/android/build-android-libs.sh.
  candidate="$(CDPATH= cd -- "$script_dir/../.." && pwd)"
  if [ -f "$candidate/flake.nix" ]; then
    printf '%s\n' "$candidate"
    return
  fi

  # Support running a copied script from the repository root.
  candidate="$(pwd)"
  if [ -f "$candidate/flake.nix" ]; then
    printf '%s\n' "$candidate"
    return
  fi

  printf 'Could not find flake.nix.\n' >&2
  printf 'Run this script from the repository root, keep it in scripts/android, or set REPO_ROOT=/path/to/simplex-chat.\n' >&2
  exit 1
}

repo_root="$(find_repo_root)"
out_dir="${OUT_DIR:-$repo_root/android-libs}"
arches="${ARCHES:-aarch64 armv7a}"
tmp="$(mktemp -d -t simplex-android-libs.XXXXXX)"
nix_max_jobs="${NIX_MAX_JOBS:-1}"
nix_build_cores="${NIX_BUILD_CORES:-1}"

nix_ver="nix-2.22.0"
nix_url="https://releases.nixos.org/nix/$nix_ver/install"
nix_hash="4fed7db867186c01ce2a2077da4a6950ed16232efbf78d0cd19700cff80559f9"
nix_config="sandbox = true
max-jobs = $nix_max_jobs
cores = $nix_build_cores
build-users-group =
experimental-features = nix-command flakes"

cleanup() {
  rm -rf "$tmp"
}
trap cleanup EXIT INT TERM

as_root() {
  if [ "$(id -u)" -eq 0 ]; then
    "$@"
  elif command -v sudo >/dev/null 2>&1; then
    sudo "$@"
  else
    printf 'This command needs root privileges and sudo is not installed: %s\n' "$*" >&2
    exit 1
  fi
}

ensure_user_vars() {
  # Some minimal environments do not export USER, but the Nix installer expects it.
  if [ -z "${USER:-}" ]; then
    USER="$(id -un)"
    export USER
  fi

  if [ -z "${HOME:-}" ]; then
    HOME="$(getent passwd "$(id -u)" | cut -d: -f6)"
    export HOME
  fi
}

install_debian_packages() {
  # Debian 13 packages needed to download Nix and unpack the library archives.
  if ! command -v apt-get >/dev/null 2>&1; then
    printf 'apt-get was not found. This installer path is intended for Debian 13.\n' >&2
    exit 1
  fi

  missing_packages=""

  for package in \
    ca-certificates \
    curl \
    git \
    unzip \
    xz-utils \
    tar \
    gzip \
    coreutils \
    findutils
  do
    if ! dpkg-query -W -f='${Status}' "$package" 2>/dev/null | grep -q 'install ok installed'; then
      missing_packages="$missing_packages $package"
    fi
  done

  if [ -n "$missing_packages" ]; then
    as_root apt-get update
    # shellcheck disable=SC2086
    as_root apt-get install -y $missing_packages
  fi
}

load_nix_profile() {
  # Source the single-user Nix profile when it exists.
  if [ -f "$HOME/.nix-profile/etc/profile.d/nix.sh" ]; then
    # shellcheck disable=SC1091
    . "$HOME/.nix-profile/etc/profile.d/nix.sh"
  fi

  if ! command -v nix >/dev/null 2>&1 && [ -x "$HOME/.nix-profile/bin/nix" ]; then
    PATH="$HOME/.nix-profile/bin:$PATH"
    export PATH
  fi
}

install_nix() {
  # Install Nix in single-user mode. The empty build-users-group setting keeps
  # Nix usable in root/sudo environments where a nixbld group is not configured.
  if [ ! -d /nix ]; then
    as_root mkdir -p /nix
    as_root chown -R "$(id -u):$(id -g)" /nix
  fi

  curl -sSf "$nix_url" -o "$tmp/nix-install"
  printf '%s  %s\n' "$nix_hash" "$tmp/nix-install" | sha256sum -c
  chmod +x "$tmp/nix-install"
  NIX_CONFIG="${NIX_CONFIG:-build-users-group =}" "$tmp/nix-install" --no-daemon
  load_nix_profile
}

ensure_nix() {
  load_nix_profile

  if ! command -v nix >/dev/null 2>&1; then
    install_nix
  fi

  printf '%s\n' "$nix_config" > "$tmp/nix.conf"
  export NIX_CONF_DIR="$tmp"
}

android_abi() {
  case "$1" in
    aarch64) printf 'arm64-v8a' ;;
    armv7a) printf 'armeabi-v7a' ;;
    *)
      printf 'Unsupported ARCHES value: %s\n' "$1" >&2
      printf 'Supported values are: aarch64 armv7a\n' >&2
      exit 1
      ;;
  esac
}

extract_zip() {
  zip_file="$1"
  dest_dir="$2"

  if [ ! -f "$zip_file" ]; then
    printf 'Expected build output was not found: %s\n' "$zip_file" >&2
    exit 1
  fi

  unzip -oq "$zip_file" -d "$dest_dir"
}

print_armv7a_simplex_failure_help() {
  cat >&2 <<'EOF'
Failed to build libsimplex.so for armv7a.

On this host the full Nix log shows qemu-arm aborting while starting
iserv-proxy-interpreter:

  error getting old personality value: Function not implemented
  qemu: uncaught target signal 6 (Aborted) - core dumped
  ghc-iserv terminated (1)

This is not an APK or Gradle issue. libsimplex.so for armv7a needs the
cross-GHC Template Haskell interpreter, and the qemu-arm runtime available in
this environment cannot run it correctly.

Practical options:
  - build only aarch64: ARCHES=aarch64 ./scripts/android/build-android-libs.sh
  - build armv7a on a host where qemu-arm supports that interpreter
  - use a Nix remote builder/substituter that already has the armv7a output
EOF
}

build_arch() {
  arch="$1"
  abi="$(android_abi "$arch")"
  arch_out="$out_dir/$abi"
  simplex_link="$tmp/result-$arch-libsimplex"
  support_link="$tmp/result-$arch-libsupport"

  printf 'Building Android libraries for %s (%s)\n' "$arch" "$abi"

  rm -rf "$arch_out"
  mkdir -p "$arch_out"

  nix build \
    "$repo_root#hydraJobs.x86_64-linux.$arch-android:lib:support" \
    --max-jobs "$nix_max_jobs" \
    --cores "$nix_build_cores" \
    --out-link "$support_link"
  extract_zip "$support_link/pkg-$arch-android-libsupport.zip" "$arch_out"

  nix build \
    "$repo_root#hydraJobs.x86_64-linux.$arch-android:lib:simplex-chat" \
    --max-jobs "$nix_max_jobs" \
    --cores "$nix_build_cores" \
    --out-link "$simplex_link" || {
      if [ "$arch" = "armv7a" ]; then
        print_armv7a_simplex_failure_help
      fi
      exit 1
    }
  extract_zip "$simplex_link/pkg-$arch-android-libsimplex.zip" "$arch_out"

  find "$arch_out" -type f -name '*.so' -print | sort
}

main() {
  ensure_user_vars
  install_debian_packages
  ensure_nix

  mkdir -p "$out_dir"

  for arch in $arches; do
    build_arch "$arch"
  done

  printf 'Android native libraries are available in: %s\n' "$out_dir"
}

main "$@"
