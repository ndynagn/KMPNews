#!/bin/sh
# Synthetic Swift integration checks. Normal builds never include FeedInteropFixture.
set -eu
cd "$(dirname "$0")/.."
output_dir=$(mktemp -d "${TMPDIR:-/tmp}/kmpnews-interop.XXXXXX")
trap 'rm -rf "$output_dir"' EXIT

./gradlew :sharedLogic:linkDebugFrameworkMacosArm64 \
    :sharedLogic:linkDebugFrameworkIosSimulatorArm64 -PfeedInteropTests=true

xcrun --sdk macosx swiftc -swift-version 5 -parse-as-library \
    -target arm64-apple-macos14.0 \
    sharedLogic/interopTests/FeedInteropSmoke.swift \
    -F sharedLogic/build/bin/macosArm64/debugFramework -framework SharedLogic \
    -Xlinker -dead_strip -o "$output_dir/macos-smoke"
"$output_dir/macos-smoke"

xcrun --sdk iphonesimulator swiftc -swift-version 5 -parse-as-library \
    -sdk "$(xcrun --sdk iphonesimulator --show-sdk-path)" -target arm64-apple-ios18.2-simulator \
    sharedLogic/interopTests/FeedInteropSmoke.swift \
    -F sharedLogic/build/bin/iosSimulatorArm64/debugFramework -framework SharedLogic \
    -Xlinker -dead_strip -o "$output_dir/ios-smoke"
xcrun simctl spawn "${SIMULATOR_UDID:-booted}" "$output_dir/ios-smoke"

# Restore ordinary frameworks so later app builds cannot consume fixture-enabled outputs.
./gradlew :sharedLogic:linkDebugFrameworkMacosArm64 :sharedLogic:linkDebugFrameworkIosSimulatorArm64
