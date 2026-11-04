const { app, server } = require('../server');
const { runAllTests, EXPECTED_CHECK_COUNT } = require('./test-suite');
const { analyzeResults } = require('./analyze-results');

async function main() {
  console.log('Starting Arena.ai server for automated test run...');
  
  try {
    const results = await runAllTests();
    console.log('Analyzing test outputs and generating report...');
    analyzeResults();

    // The suite itself cannot report a mislabelled count without changing it, so the total is
    // gated here: the constant that RELEASE.md and test.sh quote must be the real number.
    if (results.totalTests !== EXPECTED_CHECK_COUNT) {
      console.error(
        `\n\u274c The suite ran ${results.totalTests} checks but the documents quote ` +
        `${EXPECTED_CHECK_COUNT}. Update EXPECTED_CHECK_COUNT in tests/test-suite.js, ` +
        `README.md, RELEASE.md and test.sh together.`);
    }

    const exitCode = (results.failed > 0 || results.totalTests !== EXPECTED_CHECK_COUNT) ? 1 : 0;
    if (results.failed > 0) {
      console.error(`❌ Completed with ${results.failed} failures.`);
    } else {
      console.log('✨ All tests passed flawlessly! System fully verified.');
    }

    process.exitCode = exitCode;
    if (server && server.close) {
      server.close();
    }
  } catch (err) {
    console.error('Fatal test error:', err);
    process.exit(1);
  }
}

main();
