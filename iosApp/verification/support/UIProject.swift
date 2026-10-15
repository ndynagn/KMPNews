import Foundation
import PathKit
import XcodeProj

// This generator modifies only the disposable verification checkout supplied by the runner.
let path = Path(CommandLine.arguments[1])
let project = try XcodeProj(path: path)
let objects = project.pbxproj
guard let owner = objects.rootObject,
    let app = objects.nativeTargets.first(where: { $0.productType == .application }),
    let configurations = app.buildConfigurationList?.buildConfigurations,
    let group = owner.mainGroup
else { fatalError("Expected one application target and configuration list") }

for configuration in configurations {
    configuration.buildSettings["PRODUCT_BUNDLE_IDENTIFIER"] = "com.ndynagn.kmp.news.verification.ui"
    configuration.buildSettings["SWIFT_ACTIVE_COMPILATION_CONDITIONS"] = "$(inherited) DEBUG FEATURE_VERIFICATION"
}

let tests = configurations.map { configuration in
    XCBuildConfiguration(
        name: configuration.name,
        buildSettings: [
            "SDKROOT": "iphoneos",
            "SUPPORTED_PLATFORMS": "iphoneos iphonesimulator",
            "IPHONEOS_DEPLOYMENT_TARGET": configuration.buildSettings["IPHONEOS_DEPLOYMENT_TARGET"] ?? "18.2",
            "SWIFT_VERSION": "5.0",
            "GENERATE_INFOPLIST_FILE": "YES",
            "PRODUCT_BUNDLE_IDENTIFIER": "com.ndynagn.kmp.news.verification.ui.tests",
            "PRODUCT_NAME": "$(TARGET_NAME)",
            "TEST_TARGET_NAME": app.name,
            "CODE_SIGNING_ALLOWED": "NO",
            "TARGETED_DEVICE_FAMILY": "1,2",
        ])
}
for configuration in tests { objects.add(object: configuration) }
let list = XCConfigurationList(buildConfigurations: tests, defaultConfigurationName: "Debug")
objects.add(object: list)
let folder = PBXFileSystemSynchronizedRootGroup(sourceTree: .group, path: "verificationUITests")
objects.add(object: folder)
group.children.append(folder)
let product = PBXFileReference(
    sourceTree: .buildProductsDir, explicitFileType: "wrapper.cfbundle",
    path: "FeatureUITests.xctest")
objects.add(object: product)
owner.productsGroup?.children.append(product)
let sources = PBXSourcesBuildPhase()
let frameworks = PBXFrameworksBuildPhase()
objects.add(object: sources)
objects.add(object: frameworks)
let target = PBXNativeTarget(
    name: "FeatureUITests", buildConfigurationList: list,
    buildPhases: [sources, frameworks], productName: "FeatureUITests",
    product: product, productType: .uiTestBundle)
objects.add(object: target)
target.fileSystemSynchronizedGroups = [folder]
owner.targets.append(target)
_ = try target.addDependency(target: app)

let appReference = XCScheme.BuildableReference(
    referencedContainer: "container:module.xcodeproj",
    blueprint: app, buildableName: "KMPNews.app", blueprintName: app.name)
let testReference = XCScheme.BuildableReference(
    referencedContainer: "container:module.xcodeproj",
    blueprint: target, buildableName: "FeatureUITests.xctest", blueprintName: target.name)
let scheme = XCScheme(
    name: "FeatureUITests", lastUpgradeVersion: nil, version: "1.3",
    buildAction: XCScheme.BuildAction(buildActionEntries: [
        .init(buildableReference: appReference, buildFor: [.testing]),
        .init(buildableReference: testReference, buildFor: [.testing]),
    ]),
    testAction: XCScheme.TestAction(
        buildConfiguration: "Debug", macroExpansion: appReference,
        testables: [.init(skipped: false, parallelization: .none, buildableReference: testReference)]))
try project.write(path: path)
try scheme.write(path: path + "xcshareddata/xcschemes/FeatureUITests.xcscheme", override: true)
