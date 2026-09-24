// Dependency-free source guards and tests of the actual injected JavaScript.
// This does NOT replace Android compilation or device tests.
import fs from 'node:fs';
import assert from 'node:assert/strict';
import vm from 'node:vm';

const root = new URL('../', import.meta.url);
const read = path => fs.readFileSync(new URL(path, root), 'utf8');
const exists = path => fs.existsSync(new URL(path, root));

const base = 'app/src/main/java/cn/xzbim/workspace/';
const bridge = read(base + 'webview/NocoBaseSessionBridge.kt');
const js = bridge.match(/val jsCode = """([\s\S]*?)"""/)?.[1];
assert.ok(js);

let tests = 0;
for (const url of ['https://example.com/a', 'https://example.com:443/a', 'http://example.com', 'https://evil.test', 'https://example.com:8443']) {
    const location = new URL(url);
    const values = new Map();
    const localStorage = { getItem: k => values.get(k) ?? null, setItem: (k, v) => values.set(k, v) };
    const code = js.replaceAll('$jsonToken', JSON.stringify('test-token')).replaceAll('$expectedOrigin', JSON.stringify('https://example.com:443'));
    const result = JSON.parse(vm.runInNewContext(code, {location, localStorage}));
    const trusted = location.origin === 'https://example.com';
    assert.equal(result.writeSuccess, trusted, url);
    assert.equal(values.has('NOCOBASE_TOKEN'), trusted, url);
    tests++;
}

const screen = read(base + 'ui/webview/NocoBaseWebViewScreen.kt');
const chooser = read(base + 'webview/WebViewFileChooser.kt');
const manager = read(base + 'webview/NocoBaseWebViewManager.kt');
const repository = read(base + 'repository/WorkspaceRepository.kt');
const workspaceDao = read(base + 'data/local/WorkspaceDao.kt');
const authService = read(base + 'network/NocoBaseAuthService.kt');
const floatingBall = read(base + 'ui/webview/components/FloatingControlBall.kt');
const workspaceHome = read(base + 'ui/workspace/WorkspaceHomeScreen.kt');
const sslHandler = screen.slice(
    screen.indexOf('override fun onReceivedSslError'),
    screen.indexOf('webChromeClient = object')
);

const guards = [
    ['live URL handler', screen.includes('currentUrlHandler.value.handleUrlLoading')],
    ['destroy on disposal', screen.includes('webView.destroy()')],
    ['refresh recovered token', screen.includes('refreshedToken != token')],
    ['no copy on main thread', !chooser.includes('copyTo(')],
    ['no invalid persistable grant', !chooser.includes('takePersistableUriPermission')],
    ['picker only content URIs', chooser.includes('it.scheme == "content"')],
    ['no local file access', manager.includes('settings.allowFileAccess = false')],
    ['no insecure mixed resources', manager.includes('MIXED_CONTENT_NEVER_ALLOW')],
    ['password destination guard', repository.includes('if (!identityChanged) getPassword(id)')],
    ['no destructive database fallback', !read(base + 'data/local/AppDatabase.kt').includes('fallbackToDestructiveMigration')],
    ['no embedded signing password', !/storePassword\s*=\s*"/.test(read('app/build.gradle.kts'))],
    ['SSL errors only show page error for main frame', sslHandler.includes('handler?.cancel()') && sslHandler.includes('if (isMainFrameSslError)')],
    ['WebView content avoids navigation bar and fills behind it', screen.includes('.navigationBarsPadding()') && screen.includes('.background(statusBarBgColor)')],
    ['notification sync has an automatic single-flight guard', repository.includes('notificationSyncMutex.tryLock()') && repository.includes('lastAutomaticNotificationSyncAt')],
    ['new workspaces append after custom ordering', workspaceDao.includes('getNextOrderIndex()') && workspaceDao.includes('MAX(order_index)')],
    ['floating ball preserves valid saved ratios', !floatingBall.includes('savedVerticalRatio == 0.68f') && !floatingBall.includes('savedVerticalRatio == 0.85f')],
    ['backup rules exclude workspace database', read('app/src/main/res/xml/data_extraction_rules.xml').includes('domain="database" path="."') && read('app/src/main/res/xml/backup_rules.xml').includes('domain="database" path="."')],
    ['home status bar contrast follows app theme colors', workspaceHome.includes('colorScheme.background.luminance()') && !workspaceHome.includes('isSystemInDarkTheme()')],
    ['file option does not cancel first', !/onDismiss\(\)\s+on(?:TakePhoto|SelectGallery|SelectFile)\(\)/.test(read(base + 'ui/webview/components/FileUploadOptionsSheet.kt'))],
    ['auth service rethrows CancellationException', authService.includes('catch (e: CancellationException)') && authService.includes('throw e')],
    ['no account in auth logs', !authService.includes('Account:')],
    ['camera cache cleanup exists', chooser.includes('cleanOldCameraPhotos') && chooser.includes('24 * 60 * 60 * 1000L')],
    ['no non-null assertions on camera fields', !chooser.includes('cameraUri!!') && !chooser.includes('cameraFile!!')],
    ['gradle-wrapper.jar exists', exists('gradle/wrapper/gradle-wrapper.jar')],
];

for (const [name, passed] of guards) {
    assert.ok(passed, name);
    tests++;
}

console.log(`PASS: ${tests} JavaScript/source regression checks (not an Android build)`);
