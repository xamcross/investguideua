// Karma configuration for the InvestGuideUA SPA (QA2 critical-flow tests).
// Consumed by Node on any platform; the ChromeHeadlessCI launcher (--no-sandbox) lets the same
// config run unprivileged in CI containers. `npm run test:ci` selects that launcher.

// Resolve a Chromium binary so ChromeHeadless works without a system Chrome install (local dev on
// Windows/macOS/Linux + CI). Precedence: an explicit CHROME_BIN wins; otherwise fall back to the
// Chromium that Puppeteer downloads as a devDependency. If neither is present, Karma uses the
// system Chrome on PATH.
if (!process.env.CHROME_BIN) {
  try {
    process.env.CHROME_BIN = require('puppeteer').executablePath();
  } catch {
    // Puppeteer not installed; rely on a system Chrome/Edge or an explicit CHROME_BIN env var.
  }
}

module.exports = function (config) {
  config.set({
    basePath: '',
    frameworks: ['jasmine', '@angular-devkit/build-angular'],
    plugins: [
      require('karma-jasmine'),
      require('karma-chrome-launcher'),
      require('karma-jasmine-html-reporter'),
      require('karma-coverage'),
      require('@angular-devkit/build-angular/plugins/karma'),
    ],
    client: {
      jasmine: {},
      clearContext: false,
    },
    jasmineHtmlReporter: {
      suppressAll: true,
    },
    coverageReporter: {
      dir: require('path').join(__dirname, './coverage/investguide-frontend'),
      subdir: '.',
      reporters: [{ type: 'html' }, { type: 'text-summary' }],
    },
    reporters: ['progress', 'kjhtml'],
    browsers: ['Chrome'],
    customLaunchers: {
      ChromeHeadlessCI: {
        base: 'ChromeHeadless',
        flags: ['--no-sandbox', '--disable-gpu'],
      },
    },
    restartOnFileChange: true,
  });
};
