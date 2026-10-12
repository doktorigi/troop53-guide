"""Regenerate the checked-in, dependency-free Xcode project deterministically."""
from pathlib import Path
import hashlib, json
from xml.sax.saxutils import escape

ROOT = Path(__file__).resolve().parents[1]
PROJECT = ROOT / 'Troop53Guide.xcodeproj'
objects = {}
def uid(name): return hashlib.sha256(name.encode()).hexdigest()[:24].upper()
def ref(name): return uid(name)
def quote(value): return json.dumps(str(value))
def obj(object_key, isa, **fields):
    objects[uid(object_key)] = dict(isa=isa, **fields)
    return uid(object_key)
def literal(value):
    if isinstance(value, list): return '(' + ', '.join(literal(x) for x in value) + ')'
    if isinstance(value, dict): return '{ ' + ' '.join(f'{k} = {literal(v)};' for k,v in value.items()) + ' }'
    if isinstance(value, str) and value in objects: return value
    return quote(value)

targets = ['Troop53Guide', 'Troop53GuideTests', 'Troop53GuideUITests']
filegroups = []; products = []; targetrefs = []
for target in targets:
    source_refs = []; source_builds = []
    for file in sorted((ROOT / target).glob('*.swift')):
        fileid = obj('file:'+str(file.relative_to(ROOT)), 'PBXFileReference', lastKnownFileType='sourcecode.swift', path=file.name, sourceTree='<group>')
        source_refs.append(fileid)
        source_builds.append(obj('build:'+str(file.relative_to(ROOT)), 'PBXBuildFile', fileRef=fileid))
    resource_refs = []; resource_builds = []
    if target == targets[0]:
        for name, path, typ in [
            ('guide','../app/src/main/assets/guide','folder'), ('quiz','../app/src/main/assets/quiz.txt','text'),
            ('Illustrations','Troop53Guide/Resources/Illustrations','folder'),
            ('Assets','Troop53Guide/Assets.xcassets','folder.assetcatalog'),
            ('Privacy','Troop53Guide/PrivacyInfo.xcprivacy','text.xml')]:
            fileid = obj('resource:'+name,'PBXFileReference',lastKnownFileType=typ,path=path,sourceTree='SOURCE_ROOT')
            resource_refs.append(fileid); resource_builds.append(obj('resourcebuild:'+name,'PBXBuildFile',fileRef=fileid))
    filegroups.append(obj('group:'+target,'PBXGroup',children=source_refs+resource_refs,path=target,sourceTree='<group>'))
    suffix = '.app' if target == targets[0] else '.xctest'
    product = obj('product:'+target,'PBXFileReference',explicitFileType='wrapper.application' if suffix=='.app' else 'wrapper.cfbundle',includeInIndex='0',path=target+suffix,sourceTree='BUILT_PRODUCTS_DIR')
    products.append(product)
    sourcephase = obj('sources:'+target,'PBXSourcesBuildPhase',buildActionMask='2147483647',files=source_builds,runOnlyForDeploymentPostprocessing='0')
    frameworkphase = obj('frameworks:'+target,'PBXFrameworksBuildPhase',buildActionMask='2147483647',files=[],runOnlyForDeploymentPostprocessing='0')
    resourcephase = obj('resources:'+target,'PBXResourcesBuildPhase',buildActionMask='2147483647',files=resource_builds,runOnlyForDeploymentPostprocessing='0')
    configs=[]
    for configuration in ['Debug','Release']:
        settings = dict(CODE_SIGN_STYLE='Automatic',CURRENT_PROJECT_VERSION='1',GENERATE_INFOPLIST_FILE='YES',
                        IPHONEOS_DEPLOYMENT_TARGET='16.0',MARKETING_VERSION='1.0.0',SWIFT_VERSION='5.0',
                        PRODUCT_NAME='$(TARGET_NAME)',TARGETED_DEVICE_FAMILY='1,2',SWIFT_EMIT_LOC_STRINGS='YES')
        if target == targets[0]:
            settings.update(PRODUCT_BUNDLE_IDENTIFIER='com.doktorigi.troop53',ASSETCATALOG_COMPILER_APPICON_NAME='AppIcon',
                INFOPLIST_KEY_CFBundleDisplayName='Troop 53 Guide', INFOPLIST_KEY_LSApplicationCategoryType='public.app-category.education',
                INFOPLIST_KEY_UIApplicationSceneManifest_Generation='YES',INFOPLIST_KEY_UIApplicationSupportsIndirectInputEvents='YES',
                INFOPLIST_KEY_UILaunchScreen_Generation='YES',INFOPLIST_KEY_UISupportedInterfaceOrientations='UIInterfaceOrientationPortrait UIInterfaceOrientationLandscapeLeft UIInterfaceOrientationLandscapeRight',
                INFOPLIST_KEY_UISupportedInterfaceOrientations_iPad='UIInterfaceOrientationPortrait UIInterfaceOrientationPortraitUpsideDown UIInterfaceOrientationLandscapeLeft UIInterfaceOrientationLandscapeRight')
        else:
            settings.update(PRODUCT_BUNDLE_IDENTIFIER='com.doktorigi.troop53.'+target,GENERATE_INFOPLIST_FILE='YES')
            if target == targets[1]: settings.update(TEST_HOST='$(BUILT_PRODUCTS_DIR)/Troop53Guide.app/Troop53Guide', BUNDLE_LOADER='$(TEST_HOST)')
            else: settings['TEST_TARGET_NAME']=targets[0]
        configs.append(obj('config:'+target+configuration,'XCBuildConfiguration',buildSettings=settings,name=configuration))
    configlist = obj('configlist:'+target,'XCConfigurationList',buildConfigurations=configs,defaultConfigurationIsVisible='0',defaultConfigurationName='Release')
    dependencies=[]
    if target != targets[0]:
        proxy=obj('proxy:'+target,'PBXContainerItemProxy',containerPortal=ref('project'),proxyType='1',remoteGlobalIDString=ref('target:'+targets[0]),remoteInfo=targets[0])
        dependencies=[obj('dependency:'+target,'PBXTargetDependency',target=ref('target:'+targets[0]),targetProxy=proxy)]
    targetrefs.append(obj('target:'+target,'PBXNativeTarget',buildConfigurationList=configlist,buildPhases=[sourcephase,frameworkphase,resourcephase],buildRules=[],dependencies=dependencies,
        name=target,productName=target,productReference=product,productType='com.apple.product-type.application' if target == targets[0] else 'com.apple.product-type.bundle.unit-test' if target == targets[1] else 'com.apple.product-type.bundle.ui-testing'))

productgroup=obj('products','PBXGroup',children=products,name='Products',sourceTree='<group>')
main=obj('main','PBXGroup',children=filegroups+[productgroup],sourceTree='<group>')
projectconfigs=[]
for name in ['Debug','Release']:
    settings=dict(ALWAYS_SEARCH_USER_PATHS='NO',CLANG_ENABLE_MODULES='YES',CLANG_ENABLE_OBJC_ARC='YES',
        CLANG_WARN_DOCUMENTATION_COMMENTS='YES',CLANG_WARN_UNGUARDED_AVAILABILITY='YES_AGGRESSIVE',
        GCC_C_LANGUAGE_STANDARD='gnu17',GCC_WARN_64_TO_32_BIT_CONVERSION='YES',SDKROOT='iphoneos',
        SWIFT_OPTIMIZATION_LEVEL='-Onone' if name=='Debug' else '-O',ENABLE_TESTABILITY='YES' if name=='Debug' else 'NO',
        DEBUG_INFORMATION_FORMAT='dwarf' if name=='Debug' else 'dwarf-with-dsym',ONLY_ACTIVE_ARCH='YES' if name=='Debug' else 'NO',
        SWIFT_ACTIVE_COMPILATION_CONDITIONS='DEBUG' if name=='Debug' else '',GCC_PREPROCESSOR_DEFINITIONS=['DEBUG=1','$(inherited)'] if name=='Debug' else ['$(inherited)'])
    projectconfigs.append(obj('projectconfig:'+name,'XCBuildConfiguration',buildSettings=settings,name=name))
projectconfiglist=obj('projectconfiglist','XCConfigurationList',buildConfigurations=projectconfigs,defaultConfigurationIsVisible='0',defaultConfigurationName='Release')
obj('project','PBXProject',attributes=dict(BuildIndependentTargetsInParallel='YES',LastUpgradeCheck='1640',TargetAttributes={ref('target:'+t):dict(CreatedOnToolsVersion='16.4',**({'TestTargetID':ref('target:'+targets[0])} if t!=targets[0] else {})) for t in targets}),
    buildConfigurationList=projectconfiglist,compatibilityVersion='Xcode 14.0',developmentRegion='en',hasScannedForEncodings='0',knownRegions=['en','Base'],mainGroup=main,productRefGroup=productgroup,projectDirPath='',projectRoot='',targets=targetrefs)
PROJECT.mkdir(parents=True,exist_ok=True)
lines=['// !$*UTF8*$!','{','archiveVersion = 1;','classes = {};','objectVersion = 56;','objects = {']
for key,value in sorted(objects.items()): lines.append(key+' = '+literal(value)+';')
lines+=['};','rootObject = '+ref('project')+';','}']
(PROJECT/'project.pbxproj').write_text('\n'.join(lines)+'\n',encoding='utf8')

def buildable(target):
    suffix='.app' if target==targets[0] else '.xctest'
    return f'<BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{ref("target:"+target)}" BuildableName="{target+suffix}" BlueprintName="{target}" ReferencedContainer="container:Troop53Guide.xcodeproj"/>'
entries=''.join(f'<BuildActionEntry buildForTesting="YES" buildForRunning="{"YES" if t==targets[0] else "NO"}" buildForProfiling="{"YES" if t==targets[0] else "NO"}" buildForArchiving="{"YES" if t==targets[0] else "NO"}" buildForAnalyzing="YES">{buildable(t)}</BuildActionEntry>' for t in targets)
tests=''.join(f'<TestableReference skipped="NO" parallelizable="NO">{buildable(t)}</TestableReference>' for t in targets[1:])
scheme=f'''<?xml version="1.0" encoding="UTF-8"?>
<Scheme LastUpgradeVersion="1640" version="1.3">
<BuildAction parallelizeBuildables="YES" buildImplicitDependencies="YES"><BuildActionEntries>{entries}</BuildActionEntries></BuildAction>
<TestAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" shouldUseLaunchSchemeArgsEnv="YES"><Testables>{tests}</Testables><MacroExpansion>{buildable(targets[0])}</MacroExpansion></TestAction>
<LaunchAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" launchStyle="0" useCustomWorkingDirectory="NO" ignoresPersistentStateOnLaunch="NO" debugDocumentVersioning="YES" debugServiceExtension="internal" allowLocationSimulation="YES"><BuildableProductRunnable runnableDebuggingMode="0">{buildable(targets[0])}</BuildableProductRunnable></LaunchAction>
<ProfileAction buildConfiguration="Release" shouldUseLaunchSchemeArgsEnv="YES" savedToolIdentifier="" useCustomWorkingDirectory="NO" debugDocumentVersioning="YES"><BuildableProductRunnable runnableDebuggingMode="0">{buildable(targets[0])}</BuildableProductRunnable></ProfileAction>
<AnalyzeAction buildConfiguration="Debug"/><ArchiveAction buildConfiguration="Release" revealArchiveInOrganizer="YES"/>
</Scheme>
'''
schemes=PROJECT/'xcshareddata/xcschemes'; schemes.mkdir(parents=True,exist_ok=True)
(schemes/'Troop53Guide.xcscheme').write_text(scheme,encoding='utf8')
print('Generated standalone Troop53Guide.xcodeproj and shared scheme.')
